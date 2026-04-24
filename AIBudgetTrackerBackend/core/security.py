from argon2 import PasswordHasher
from argon2.exceptions import VerifyMismatchError

# Initialize with 2026 recommended parameters for mobile PINs
# m=65536 (64MB RAM), t=3 (3 iterations), p=4 (4 parallel threads)
ph = PasswordHasher(
    time_cost=3,
    memory_cost=65536,
    parallelism=4
)

def hash_pin(pin: str) -> str:
    """
    Takes a 6-digit string and returns a secure Argon2id hash.
    Argon2id automatically includes a unique salt in the output string.
    """
    return ph.hash(pin)

def verify_pin(hashed_pin: str, input_pin: str) -> bool:
    """
    Verifies the input PIN against the stored hash.
    Returns True if match, False otherwise.
    """
    try:
        return ph.verify(hashed_pin, input_pin)
    except (VerifyMismatchError, Exception):
        # We catch everything to prevent timing attacks or crashes
        # from malformed hash strings.
        return False