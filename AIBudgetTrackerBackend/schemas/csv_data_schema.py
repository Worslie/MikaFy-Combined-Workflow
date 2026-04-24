from pydantic import BaseModel

class ColumnMap(BaseModel):
    date: int
    description: int
    amount_in: int  # The 'Credit' column index
    amount_out: int # The 'Debit' column index

class CSVConfig(BaseModel):
    column_map: ColumnMap  # This creates the nested structure you need
    has_headers: bool
    is_split_amount: bool