import json
import time
from typing import List
from core.config import client, MODEL_ID
from schemas.banking_data_schema import TransactionRequest, CategorisationRequest


def process_and_categorize(
        cleaned_rows: List[dict],
        biz_categories: List[str],
        pers_categories: List[str],
    business_name: str | None = None,
        business_industry: str | None = None,
        business_description: str | None = None,
        batch_size: int = 60  # 60 is the "Golden Ratio" for speed vs reliability
) -> List[TransactionRequest]:
    print(
        f"📩 CSV business context received: "
        f"name={'yes' if business_name else 'no'}, "
        f"industry={'yes' if business_industry else 'no'}, "
        f"description={'yes' if business_description else 'no'}"
    )

    # 1. Deduplicate unique descriptions to save tokens and money
    unique_desc = list({row['description'] for row in cleaned_rows})
    batches = [unique_desc[i:i + batch_size] for i in range(0, len(unique_desc), batch_size)]
    ai_master_data = {}

    print(f"📦 Unique Merchants: {len(unique_desc)} | Batches: {len(batches)}")

    for idx, batch in enumerate(batches):
        print(f"   ⚡ Processing Batch {idx + 1}/{len(batches)} (Size: {len(batch)})...")

        # We are ultra-strict about the 'original_description' to ensure the mapping works
        prompt = f"""
        Return a JSON list of objects for these bank descriptions.

        BIZ CATEGORIES: {biz_categories}
        PERS CATEGORIES: {pers_categories}
        
        BUSINESS CONTEXT:
        - Business Name: {business_name}
        - Business Industry: {business_industry}
        - Business Description: {business_description}
        
        If a transaction is a determined as a business transaction, use the BUSINESS CONTEXT to help categorize it.
        
        For each string in DATA, return:
        - "original_description": The EXACT string provided in DATA.
        - "merchant": A clean name (e.g., 'Starbucks').
        - "category_name": The best fit from the provided category lists. Important note: only select 1 category per transaction.
        - "is_business": true IF the category is from the BIZ list, else false.

        DATA: {batch}
        """

        try:
            # We removed http_options to fix the 400 INVALID_ARGUMENT error
            response = client.models.generate_content(
                model=MODEL_ID,
                contents=prompt,
                config={
                    'response_mime_type': 'application/json'
                }
            )

            batch_results = json.loads(response.text)

            # Map the results using the description as the key
            for item in batch_results:
                desc_key = item.get('original_description')
                if desc_key:
                    ai_master_data[desc_key] = item

            # Brief pause for the 2026 Rate Limits
            if len(batches) > 1:
                time.sleep(0.5)

        except Exception as e:
            print(f"⚠️ Batch {idx + 1} failed: {e}")
            continue

    # 2. Re-stitch the cleaned rows with the AI data
    final_results = []
    for row in cleaned_rows:
        # Look up the AI's answer for this specific description
        meta = ai_master_data.get(row['description'], {})
        is_biz = meta.get('is_business', False)

        # 2. Re-stitch the cleaned rows with the AI data
        final_results = []
        for row in cleaned_rows:
            meta = ai_master_data.get(row['description'], {})
            is_biz = meta.get('is_business', False)

            final_results.append(TransactionRequest(
                merchant=meta.get('merchant', "Unknown"),
                amount=row['amount'],
                date=row['date'],
                description=row['description'],
                category_name=meta.get('category_name', "Uncategorized"),
                # Back to snake_case for the Python constructor
                is_business=bool(is_biz)
                # categories=... is gone (The Bloat Fix)
            ))

        return final_results


def category_assignment(request_data: CategorisationRequest):
    transaction = request_data.transaction
    # The prompt is now much simpler because the choice is narrower

    user_prompt = f"""
    Categorize this transaction into EXACTLY one category from the list below.

    ALLOWED CATEGORIES: {request_data.categories}

    TRANSACTION:
    - Merchant: {transaction.merchant}
    - Description: {transaction.description}
    - Amount: {transaction.amount}
    - Is Business: {transaction.is_business}
    - Business Name: {request_data.business_name}
    - Business Industry: {request_data.business_industry}
    - Business Description: {request_data.business_description}

    RULES:
    1. You MUST pick a name from the ALLOWED CATEGORIES list.
    2. Return ONLY the category name as a plain string.
    3. Only use the business Name, Industry, and Description if the transaction is a business transaction.
    """

    try:
        response = client.models.generate_content(
            model=MODEL_ID,
            contents=user_prompt
        )

        ai_category = response.text.strip()

        # Forensic Safety: If Gemini hallucinations, fallback to the first item or 'Uncategorized'
        if ai_category not in request_data.categories:
            ai_category = request_data.categories[0] if request_data.categories else "Uncategorized"

        return TransactionRequest(
            merchant=transaction.merchant,
            amount=transaction.amount,
            date=transaction.date,
            description=transaction.description,
            category_name=ai_category,
            is_business=transaction.is_business,
            original_category=transaction.original_category
        )

    except Exception as e:
        print(f"Error calling Gemini: {e}")
        return transaction.dict(by_alias=True)