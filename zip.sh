#!/bin/bash

# Get the name of the current directory
current_dir=$(basename "$PWD")

# Name of the output zip file
output_zip="${current_dir}.zip"

# Check if the zip file already exists
if [ -f "$output_zip" ]; then
    echo "Error: The file '$output_zip' already exists."
    echo "Please remove or rename it and try again."
    exit 1
fi

# Create the zip file while excluding the specified directories
zip -r "$output_zip" . -x "*/.git/*" "*/.venv/*" "*/node_modules/*" "*/.serverless/*" "*/build/*" "build/*" ".gradle/*" "*/.ruff_cache/*"

# Check if zip was successful
if [ $? -eq 0 ]; then
    echo "Successfully created '$output_zip' excluding specified directories."
else
    echo "Error occurred while creating the zip file."
    exit 1
fi
