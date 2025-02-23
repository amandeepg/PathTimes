import sys
import os
import hashlib


def compute_directory_hash(directory: str):
    sha1 = hashlib.sha1()
    files = []

    # Collect all files and convert paths to relative
    for root, _, filenames in os.walk(directory):
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

        # Hash file contents in chunks
        full_path = os.path.join(directory, rel_path)
        try:
            with open(full_path, "rb") as f:
                while True:
                    chunk = f.read(4096)  # Read in 4KB chunks
                    if not chunk:
                        break
                    sha1.update(chunk)
        except IOError as e:
            raise RuntimeError(f"Error reading {full_path}: {e}")

    return sha1.hexdigest()


def main():
    directory = "baml_src"
    if not os.path.isdir(directory):
        print(f"Error: {directory} is not a valid directory")
        sys.exit(1)

    try:
        directory_hash = compute_directory_hash(directory)
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)

    # Write hash to a Python file
    output_file = "src/lib/hash_constants.py"
    with open(output_file, "w") as f:
        f.write(f"# Auto-generated hash from directory: {directory}\n")
        f.write(f"LLM_HASH = '{directory_hash}'\n")

    print(f"Successfully created hash file: {output_file}")


if __name__ == "__main__":
    main()
