#!/bin/bash

# --- Configuration ---
SAM_CLI_URL="https://github.com/aws/aws-sam-cli/releases/latest/download/aws-sam-cli-linux-x86_64.zip"
INSTALL_DIR="/tmp/sam-cli-install"
ZIP_FILE="aws-sam-cli-linux-x86_64.zip"
INSTALL_SCRIPT="install" # Name of the installation script inside the zip

# --- Functions for Error Handling and Cleanup ---

# Function to display an error message and exit
handle_error() {
    local msg="$1"
    echo "Error: $msg" >&2
    cleanup # Attempt cleanup on error
    exit 1
}

# Function to clean up temporary directory
cleanup() {
    if [ -d "$INSTALL_DIR" ]; then
        echo "Cleaning up temporary directory: $INSTALL_DIR"
        rm -rf "$INSTALL_DIR"
    fi
}

# Trap command to ensure cleanup runs even if script exits early due to error or signal
trap cleanup EXIT

# --- Main Script ---

echo "Starting AWS SAM CLI installation script..."

# 1. Create temporary directory
echo "Creating temporary directory: $INSTALL_DIR"
mkdir -p "$INSTALL_DIR" || handle_error "Failed to create directory $INSTALL_DIR."

# Change to the temporary directory
cd "$INSTALL_DIR" || handle_error "Failed to change to directory $INSTALL_DIR."

# 2. Download the ZIP file
echo "Downloading AWS SAM CLI from: $SAM_CLI_URL"
wget "$SAM_CLI_URL" -O "$ZIP_FILE" || handle_error "Failed to download SAM CLI zip file."

# Check if download was successful (exit code is already checked by ||)
if [ ! -f "$ZIP_FILE" ]; then
    handle_error "Downloaded file '$ZIP_FILE' not found."
fi

# 3. Unzip the file
echo "Unzipping $ZIP_FILE to $INSTALL_DIR"
unzip "$ZIP_FILE" || handle_error "Failed to unzip SAM CLI file."

# 4. Check if the install script exists and make it executable
if [ ! -f "$INSTALL_SCRIPT" ]; then
    handle_error "Installation script '$INSTALL_SCRIPT' not found after unzipping."
fi

echo "Making '$INSTALL_SCRIPT' executable."
chmod +x "$INSTALL_SCRIPT" || handle_error "Failed to make install script executable."

# 5. Run the install script with sudo
echo "Running the installation script with sudo..."
echo "You may be prompted to enter your password."
sudo "./$INSTALL_SCRIPT" || handle_error "SAM CLI installation failed."

echo "AWS SAM CLI installation script finished."
echo "Please note: You may need to open a new terminal window or source your shell's profile (e.g., source ~/.bashrc or source ~/.zshrc) for the 'sam' command to be available in your PATH."

# Cleanup happens automatically via the trap command on script exit.
