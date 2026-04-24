import os
import requests
from dotenv import load_dotenv

load_dotenv()


class TrueLayerService:
    def __init__(self):
        self.client_id = os.getenv("TRUELAYER_CLIENT_ID")
        self.client_secret = os.getenv("TRUELAYER_CLIENT_SECRET")
        self.redirect_uri = os.getenv("TRUELAYER_REDIRECT_URI")

        # 1. AUTH URL: For login & getting tokens
        self.base_auth_url = "https://auth.truelayer-sandbox.com"

        # 2. DATA URL: For accounts, transactions, and metadata (/me)
        # You were likely missing this definition!
        self.base_data_url = "https://api.truelayer-sandbox.com"

    def get_initiation_url(self, userId: str):
        scopes = "info accounts balance cards transactions direct_debits standing_orders offline_access"
        # Initiation ALWAYS uses the Auth URL
        return (
            f"{self.base_auth_url}/"
            f"?response_type=code"
            f"&client_id={self.client_id}"
            f"&redirect_uri={self.redirect_uri}"
            f"&scope={scopes.replace(' ', '%20')}"
            f"&providers=uk-cs-mock"
            f"&state={userId}"
        )

    def exchange_code(self, code: str):
        # Token exchange ALWAYS uses the Auth URL
        url = f"{self.base_auth_url}/connect/token"
        payload = {
            "grant_type": "authorization_code",
            "client_id": self.client_id,
            "client_secret": self.client_secret,
            "redirect_uri": self.redirect_uri,
            "code": code
        }
        response = requests.post(url, data=payload)
        return response.json()

    def get_metadata(self, access_token: str):
        # Metadata/Data calls ALWAYS use the Data URL
        url = f"{self.base_data_url}/data/v1/me"
        headers = {
            "Authorization": f"Bearer {access_token}",
            "Accept": "application/json"
        }

        try:
            response = requests.get(url, headers=headers)
            if response.status_code == 200:
                data = response.json()
                results = data.get("results", [])
                if results:
                    return results[0].get("provider", {})
            else:
                # Log the specific error from TrueLayer
                print(f"❌ Data API Error: {response.status_code} - {response.text}")
        except Exception as e:
            print(f"❌ Metadata Fetch Error: {e}")

        return {}