from services.ai_queries.csv.csv_cleanup import normalize_transactions
from services.ai_queries.csv.csv_ai_cleanup import get_mapping_from_ai
import pandas as pd


def process_csv_upload(file_path: str):
    """
    Processes a raw CSV file by:
    1. Snippet analysis for AI column mapping.
    2. Normalizing split-column amounts (Credits/Debits).
    3. Returning a list of cleaned transaction dictionaries.
    """

    # 1. PEAK AT THE DATA
    # We read 5 rows to provide context for the AI 'Fingerprint'
    snippet_df = pd.read_csv(file_path, nrows=5)
    snippet_text = snippet_df.to_string()

    # 2. GET AI CONFIG (Column Map, Headers, etc.)
    config_obj = get_mapping_from_ai(snippet_text)

    # 3. SAFETY CHECK & CONVERSION
    # If AI fails (Quota/Error), we stop here to prevent further crashes
    if config_obj is None:
        print("❌ AI Mapping failed. Returning empty list.")
        return []

    # Convert Pydantic object to dict for the normalize_transactions function
    if hasattr(config_obj, "model_dump"):
        config = config_obj.model_dump()
    else:
        config = config_obj

    # 4. RUN FORENSIC NORMALIZATION
    # This function now handles Lloyds-style Credit vs Debit columns
    # IMPORTANT: Ensure normalize_transactions returns a pd.DataFrame
    clean_df = normalize_transactions(file_path, config)

    # 5. FINAL CONVERSION
    # We convert the DataFrame back to a list of dicts for the categorizer
    if isinstance(clean_df, pd.DataFrame):
        return clean_df.to_dict(orient="records")

    # Fallback if normalize_transactions already returned a list
    return clean_df