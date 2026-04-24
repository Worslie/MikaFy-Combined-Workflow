from fastapi import Depends, HTTPException, Security
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from supabase import create_client, Client
import os

# 1. Setup the Bearer token scheme (This looks for 'Authorization: Bearer <token>')
security = HTTPBearer()

# 2. Initialize a client for verification
url = os.getenv("SB_URL")
key = os.getenv("SB_SECRET_KEY")
supabase: Client = create_client(url, key)


async def get_current_user(credentials: HTTPAuthorizationCredentials = Security(security)):
    """
    Returns the authenticated Supabase user object.
    """
    token = credentials.credentials
    try:
        res = supabase.auth.get_user(token)

        if not res.user:
            raise HTTPException(status_code=401, detail="Invalid session")

        return res.user
    except Exception:
        raise HTTPException(status_code=401, detail="Session expired or invalid")


async def get_current_user_id(credentials: HTTPAuthorizationCredentials = Security(security)) -> str:
    """
    Returns the authenticated user's Supabase ID.
    """
    user = await get_current_user(credentials)

    if not user or not getattr(user, "id", None):
        raise HTTPException(status_code=401, detail="Invalid session")

    return str(user.id)