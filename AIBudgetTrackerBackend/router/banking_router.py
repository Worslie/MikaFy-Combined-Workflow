from datetime import datetime, timedelta, timezone
from typing import List

from fastapi import APIRouter, HTTPException
from fastapi.responses import RedirectResponse, HTMLResponse

from supabase import create_client, Client

from core.database import supabase
from schemas.banking_data_schema import TransactionRequest
from services.banking.truelayer.tl_link_service import TrueLayerService

router = APIRouter(prefix="/bank", tags=["Banking"])
tl_service = TrueLayerService()


@router.get("/exchange")
async def initiate_bank_link(userId: str = None):  # Capture the ?userId= from Android
    if not userId:
        raise HTTPException(status_code=400, detail="User ID is required")

    # Pass the userId to the service so it can be used as 'state'
    auth_url = tl_service.get_initiation_url(userId)
    return RedirectResponse(url=auth_url)

@router.post("/exchange")
async def exchange_token(code: str):
    result = tl_service.exchange_code(code)
    if "error" in result:
        raise HTTPException(status_code=400, detail=result.get("error_description"))
    return result


@router.get("/callback")
async def callback(code: str = None, state: str = None):
    if not code or not state:
        raise HTTPException(status_code=400, detail="Missing code or userId")

    # 1. Exchange code for tokens
    token_data = tl_service.exchange_code(code)

    if "access_token" in token_data:
        try:
            access_token = token_data["access_token"]

            # 1. Get the provider_id
            provider_info = tl_service.get_metadata(access_token)
            provider_id = provider_info.get("provider_id", "unknown-bank")

            expires_in = token_data.get("expires_in", 3600)

            # 2. Calculate the specific deadline using that dynamic value
            # We use utcnow() to stay consistent with Supabase's default timezones
            expiry_timestamp = (datetime.now(timezone.utc) + timedelta(seconds=expires_in)).isoformat()

            # 3. Map it to your database dictionary
            db_data = {
                "user_id": state,
                "provider_id": provider_id,
                "access_token": access_token,
                "refresh_token": token_data.get("refresh_token"),
                "expires_at": expiry_timestamp,  # Dynamically calculated based on TrueLayer's response
            }

            # 4. Save to Supabase
            supabase.from_("bank_connections").upsert(db_data).execute()

            print(f"✅ Saved {provider_id} for user {state}")
            app_url = f"mikafy://auth-callback?success=true&userId={state}"

        except Exception as e:
            print(f"🔥 Error saving connection: {str(e)}")
            app_url = "mikafy://auth-callback?success=false"
    else:
        app_url = "mikafy://auth-callback?success=false"

    return HTMLResponse(content=f"<script>window.location.href='{app_url}'</script>")