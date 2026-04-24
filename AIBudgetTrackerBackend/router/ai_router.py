from services.ai_queries.csv.csv_proccessor import process_csv_upload
from schemas.banking_data_schema import TransactionRequest, CategorisationRequest
from fastapi import APIRouter, HTTPException, UploadFile, File, Form
import shutil
import os
import json
from services.ai_queries.csv.csv_proccessor import process_csv_upload
from services.ai_queries.transaction_enricher import process_and_categorize, category_assignment

router = APIRouter(prefix="/analyze", tags=["AI"])


@router.post("/transaction")
async def analyze(request_data: CategorisationRequest):
    try:
        # 1. Get the single categorized transaction
        print (request_data.model_dump())
        enriched_transaction = category_assignment(
            request_data
        )

        # 2. Wrap it in a list to match the CSV upload format
        return {
            "transactions": [enriched_transaction]
        }

    except Exception as e:
        print(f"❌ Error: {e}")
        raise HTTPException(status_code=500, detail="AI Processing Failed")


@router.post("/upload-csv")
async def upload_csv(
        file: UploadFile = File(...),
        biz_categories: str = Form(...),  # Received as a JSON string from the app
        pers_categories: str = Form(...),  # Received as a JSON string from the app
        business_name: str | None = Form(None),
        business_industry: str | None = Form(None),
        business_description: str | None = Form(None)
):
    if not file.filename.endswith('.csv'):
        raise HTTPException(status_code=400, detail="Invalid file type.")

    temp_path = f"temp_{file.filename}"

    try:
        # 1. Parse the category lists from the Form strings
        biz_list = json.loads(biz_categories)
        pers_list = json.loads(pers_categories)

        # 2. Save temp file securely
        with open(temp_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)

        # 3. Step One: Forensic Cleanup (Normalizing Date/Amount)
        # Returns a list of dicts: [{'date':..., 'description':..., 'amount':...}]
        cleaned_rows = process_csv_upload(temp_path)

        # 4. Step Two: AI Enrichment & Categorization
        # This calls Gemini once with your two specific lists
        final_enriched_data = process_and_categorize(
            cleaned_rows=cleaned_rows,
            biz_categories=biz_list,
            pers_categories=pers_list,
            business_name=business_name,
            business_industry=business_industry,
            business_description=business_description
        )

        # 5. Cleanup
        if os.path.exists(temp_path): os.remove(temp_path)

        # 6. Return the full TransactionRequest objects

        print (final_enriched_data)
        return {
            "transactions": final_enriched_data
        }

    except Exception as e:
        if os.path.exists(temp_path): os.remove(temp_path)
        raise HTTPException(status_code=500, detail=f"Processing Failed: {str(e)}")