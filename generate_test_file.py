import os
import hashlib
import time

FILE_NAME = "sharedash_1gb_benchmark.bin"
FILE_SIZE = 1024 * 1024 * 1024  # 1 GiB = 1,073,741,824 bytes
CHUNK_SIZE = 8 * 1024 * 1024    # 8 MiB write buffer

print(f"Creating 1GB benchmark file: {FILE_NAME} ({FILE_SIZE:,} bytes)...")
start = time.time()

sha256 = hashlib.sha256()
total_written = 0

# Use a deterministic pseudo-random sequence for data integrity testing
seed_pattern = bytes([i % 256 for i in range(CHUNK_SIZE)])

with open(FILE_NAME, "wb") as f:
    while total_written < FILE_SIZE:
        to_write = min(CHUNK_SIZE, FILE_SIZE - total_written)
        buf = seed_pattern[:to_write]
        f.write(buf)
        sha256.update(buf)
        total_written += to_write

elapsed = time.time() - start
digest = sha256.hexdigest()

print(f"Created {FILE_NAME} in {elapsed:.2f}s")
print(f"Size: {os.path.getsize(FILE_NAME):,} bytes")
print(f"SHA-256: {digest}")

with open("sharedash_1gb_benchmark.bin.sha256", "w") as f:
    f.write(f"{digest} *{FILE_NAME}\n")
