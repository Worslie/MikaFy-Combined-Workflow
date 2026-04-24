import os
from supabase import create_client, Client
from core.security import hash_pin, verify_pin
from fastapi import HTTPException


class DBAuthService:
    def __init__(self):
        url = os.getenv("SB_URL")
        secret_key = os.getenv("SB_SECRET_KEY")
        if not url or not secret_key:
            raise ValueError("SB_URL and SB_SECRET_KEY must be set")

        # Admin/backend client: use this for protected DB writes
        self.admin_supabase: Client = create_client(url, secret_key)

        # Separate auth client in case auth operations attach session state
        self.auth_supabase: Client = create_client(url, secret_key)

    async def sign_in_with_google(self, id_token: str):
        """
        Swaps a Google ID Token for a Supabase session.
        """
        try:
            res = self.auth_supabase.auth.sign_in_with_id_token({
                "provider": "google",
                "token": id_token
            })
            return res.user, res.session
        except Exception as e:
            print(f"Google Auth Error: {e}")
            return None, None

    async def setup_new_user(self, user_id: str, email: str, pin: str):
        """
        Create or update the user's profile row using the backend/admin client.
        """
        hashed = hash_pin(pin)

        try:
            self.admin_supabase.table("profiles").upsert({
                "id": user_id,
                "email": email,
                "pin_hash": hashed,
                "biometrics_enabled": False
            }).execute()
        except Exception as e:
            print(f"PIN Setup DB Error: {e}")
            raise HTTPException(status_code=500, detail=f"Failed to save PIN: {e}")

    async def check_pin(self, user_id: str, pin: str) -> bool:
        """
        Fetches the hash and verifies it against the input pin.
        """
        try:
            res = self.admin_supabase.table("profiles").select("pin_hash").eq("id", user_id).single().execute()

            if not res.data or "pin_hash" not in res.data:
                return False

            return verify_pin(res.data["pin_hash"], pin)
        except Exception as e:
            print(f"PIN Verification Error: {e}")
            return False