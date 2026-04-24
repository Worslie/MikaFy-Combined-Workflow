import uvicorn
import os
from fastapi import FastAPI
from router import banking_router, ai_router, db_auth_router

app = FastAPI(title="MikaFy API (2026)")

# Register Routers
app.include_router(ai_router.router)

# Banking is on hold due to FCA requirements
# app.include_router(banking_router.router)

app.include_router(db_auth_router.router)

@app.get("/")
async def home():
    return {"status": "Online", "message": "MikaFy Server Active"}

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8080))
    uvicorn.run("main:app", host="0.0.0.0", port=port)