import pandas as pd
from dateutil import parser
import re


def normalize_transactions(file_path, config):
    df = pd.read_csv(file_path)
    cols = config['column_map']

    clean_rows = []
    for _, row in df.iterrows():
        try:
            # Safely parse Credit and Debit
            def parse_amt(val):
                if pd.isna(val) or str(val).strip() == "": return 0.0
                return float(str(val).replace('£', '').replace(',', '').strip())

            credit = parse_amt(row.iloc[cols['amount_in']])
            debit = parse_amt(row.iloc[cols['amount_out']])

            # Final calculation: Income (Credit) - Expenses (Debit)
            final_amount = credit - debit

            clean_rows.append({
                "date": str(row.iloc[cols['date']]),
                "description": str(row.iloc[cols['description']]),
                "amount": round(final_amount, 2)
            })
        except Exception as e:
            continue

    # RETURN THE DATAFRAME
    return pd.DataFrame(clean_rows)