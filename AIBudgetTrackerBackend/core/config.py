import os
from dotenv import load_dotenv
from google import genai

# 1. Load the environment variables once
if os.path.exists(".env"):
    load_dotenv()

# 2. Centralized Settings
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
MODEL_ID = "gemini-2.5-flash-lite"

if not GEMINI_API_KEY:
    raise ValueError("FATAL ERROR: GEMINI_API_KEY not found in environment.")

# 3. Global Client Instance
# Creating this once saves memory and connection time
client = genai.Client(api_key=GEMINI_API_KEY)