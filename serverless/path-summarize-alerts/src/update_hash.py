import hashlib
import os
import sys
from typing import List


def compute_directory_hash(directory: str):
    sha1 = hashlib.sha1()
    files: List[str] = []

    # Collect all files and convert paths to relative
    for root, _, filenames in os.walk(directory):
        # Skip __pycache__ directories
        if "__pycache__" in root.split(os.path.sep):
            continue

        for filename in filenames:
            file_path = os.path.join(root, filename)
            rel_path = os.path.relpath(file_path, directory)
            # Normalize path separators for cross-platform consistency
            rel_path = rel_path.replace(os.path.sep, "/")
            files.append(rel_path)

    # Sort files to ensure consistent order
    files.sort()

    for rel_path in files:
        # Output the file with a helpful message
        print("Hashing file:", rel_path)

        # Hash the relative path
        sha1.update(rel_path.encode("utf-8"))

        # Hash file contents in chunks, normalizing line endings
        full_path = os.path.join(directory, rel_path)
        try:
            with open(full_path, "rb") as f:
                content = f.read()
                # Normalize line endings to LF (\n) by replacing CRLF (\r\n) with LF
                normalized_content = content.replace(b"\r\n", b"\n")
                sha1.update(normalized_content)
        except IOError as e:
            raise RuntimeError(f"Error reading {full_path}: {e}")

    return sha1.hexdigest()


def compute_combined_hash(directories: List[str]):
    sha1 = hashlib.sha1()

    for directory in directories:
        if not os.path.isdir(directory):
            print(f"Warning: {directory} is not a valid directory, skipping...")
            continue

        print(f"\nProcessing directory: {directory}")
        dir_hash = compute_directory_hash(directory)
        print(f"Hash for {directory}: {dir_hash}")
        sha1.update(dir_hash.encode("utf-8"))

    return sha1.hexdigest()


def main():
    directories = ["baml_src", "src/lib/dspy"]

    # Verify that at least one directory exists
    valid_directories = [d for d in directories if os.path.isdir(d)]
    if not valid_directories:
        print(f"Error: None of the directories {directories} exist")
        sys.exit(1)

    try:
        combined_hash = compute_combined_hash(directories)
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)

    # Write hash to a Python file
    output_file = "src/lib/hash_constants.py"
    with open(output_file, "w") as f:
        f.write(
            "# Auto-generated hash from directories: " + ", ".join(directories) + "\n"
        )
        f.write(f"LLM_HASH = '{combined_hash}'\n")

    print(f"\nSuccessfully created hash file: {output_file}")
    print(f"Combined hash: {combined_hash}")


if __name__ == "__main__":
    main()
