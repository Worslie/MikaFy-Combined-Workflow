from pydantic import BaseModel, Field, ConfigDict
from typing import Optional, List


class TransactionRequest(BaseModel):
    # Use the modern Pydantic V2 config
    model_config = ConfigDict(populate_by_name=True)

    merchant: str
    amount: float
    date: str
    description: str = ""

    # Remove serialization_alias so it sends "category_name" to match Kotlin
    category_name: str
    is_business: bool = False
    original_category: Optional[str] = None

class CategorisationRequest(BaseModel):
    transaction: TransactionRequest
    categories: List[str]
    # Added business details
    business_name: Optional[str] = None
    business_industry: Optional[str] = None
    business_description: Optional[str] = None