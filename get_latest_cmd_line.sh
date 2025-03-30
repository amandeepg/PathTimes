#!/bin/bash

set -e

# Fetch the download page HTML
page_content=$(curl -s https://developer.android.com/studio#command-tools)

# Extract the Linux command-line tools URL using standard grep
download_url=$(echo "$page_content" | grep -o 'href="https://[^"]*commandlinetools-linux[^"]*_latest.zip"' | sed 's/href="//;s/"//' | head -n1)

# Verify URL was found
if [ -z "$download_url" ]; then
    echo "Error: Failed to extract download URL." >&2
    exit 1
fi

# Download the file
echo $download_url