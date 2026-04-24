from schemas.csv_data_schema import CSVConfig
from core.config import client, MODEL_ID
def get_mapping_from_ai(snippet):
    prompt = f"Analyze this CSV snippet and return column indices: {snippet}"

    response = client.models.generate_content(
        model=MODEL_ID,
        contents=prompt,
        config={'response_mime_type': 'application/json', 'response_schema': CSVConfig}
    )
    return response.parsed
