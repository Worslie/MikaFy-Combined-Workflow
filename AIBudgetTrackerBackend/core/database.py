import os
from supabase import create_client, Client

# Update your environment variables to use the new naming convention
url: str = os.getenv("SB_URL")
secret_key: str = os.getenv("SB_SECRET_KEY") # Use sb_secret_... here

# Initialize with the modern secret key
supabase: Client = create_client(url, secret_key)