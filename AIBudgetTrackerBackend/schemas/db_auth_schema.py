from pydantic import BaseModel, EmailStr, Field, field_validator

class UserAuth(BaseModel):
    email: EmailStr
    password: str

class AuthResponse(BaseModel):
    user_id: str
    access_token: str
    refresh_token: str

class UserAuth(BaseModel):
    email: EmailStr
    password: str


class AuthResponse(BaseModel):
    user_id: str
    access_token: str
    refresh_token: str


from pydantic import BaseModel, Field, field_validator

class PinSetupRequest(BaseModel):
    # REMOVED user_id from here. We get it from the Token!
    pin: str = Field(..., min_length=6, max_length=6)

    @field_validator("pin")
    @classmethod
    def validate_pin(cls, v: str) -> str:
        if not v.isdigit():
            raise ValueError("PIN must contain only digits")
        return v