from fastapi import APIRouter, HTTPException, Body, Depends
import logging

from dependencies import get_current_user_id
from services.database.db_auth_service import DBAuthService
from schemas.db_auth_schema import PinSetupRequest

logger = logging.getLogger(__name__)

# 1. Define the router with a prefix
# This makes your URL: /auth/login/google
router = APIRouter(prefix="/auth", tags=["User Authentication"])

# 2. Initialize the service you just moved
auth_service = DBAuthService()


@router.post("/login/google")
async def google_login(payload: dict = Body(...)):
    """
    Endpoint to exchange a Google ID Token for a Supabase Session.
    """
    id_token = payload.get("id_token")

    if not id_token:
        raise HTTPException(status_code=400, detail="No id_token provided in request body")

    try:
        # Call your service logic
        user, session = await auth_service.sign_in_with_google(id_token)

        if not user or not session:
            raise HTTPException(
                status_code=401,
                detail="Authentication failed. Google token may be invalid or expired."
            )

        return {
            "status": "success",
            "user": {
                "id": user.id,
                "email": user.email,
            },
            "session": {
                "access_token": session.access_token,
                "refresh_token": session.refresh_token,
                "expires_at": session.expires_at
            }
        }
    except HTTPException:
        raise
    except Exception as e:
        logger.exception("Google login failed")
        raise HTTPException(status_code=500, detail=f"Google login failed: {str(e)}")


@router.post("/setup-pin")
async def set_user_pin(
    payload: dict = Body(...),
    authenticated_user_id: str = Depends(get_current_user_id)
):
    try:
        email = payload.get("email")
        pin = payload.get("pin")

        if not email:
            raise HTTPException(status_code=400, detail="No email provided in request body")

        if not pin:
            raise HTTPException(status_code=400, detail="No pin provided in request body")

        await auth_service.setup_new_user(authenticated_user_id, email, pin)
        return {"status": "success", "message": "PIN configured successfully"}
    except HTTPException:
        raise
    except Exception as e:
        logger.exception("PIN setup failed for user_id=%s", authenticated_user_id)
        raise HTTPException(status_code=500, detail=f"PIN setup failed: {str(e)}")


@router.post("/login/pin")
async def pin_login(
    payload: dict = Body(...),
    authenticated_user_id: str = Depends(get_current_user_id)
):
    try:
        pin = payload.get("pin")

        if not pin:
            raise HTTPException(status_code=400, detail="No pin provided in request body")

        is_valid = await auth_service.check_pin(authenticated_user_id, pin)

        if not is_valid:
            raise HTTPException(status_code=401, detail="Invalid PIN")

        return {
            "status": "success",
            "message": "PIN authentication successful",
            "user_id": authenticated_user_id
        }
    except HTTPException:
        raise
    except Exception as e:
        logger.exception("PIN login failed for user_id=%s", authenticated_user_id)
        raise HTTPException(status_code=500, detail=f"PIN login failed: {str(e)}")