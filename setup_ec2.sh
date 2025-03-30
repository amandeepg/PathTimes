#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

echo " SCRIPT START: Setting up Android build environment..."

# --- Configuration Variables (You might want to adjust these) ---

# Choose JDK version (11 is widely compatible, 17 is needed for newer AGP)
JDK_VERSION="17"
# Target Android Platform SDK (Check your project's compileSdk)
ANDROID_PLATFORM="android-35"
# Target Android Build Tools (Check your project's buildToolsVersion or AGP requirement)
ANDROID_BUILD_TOOLS="36.0.0"
# Android Command Line Tools download URL
# **IMPORTANT**: Check for the latest version URL on the Android Studio download page!
# https://developer.android.com/studio#command-tools
# This URL might become outdated.
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
# Installation directory for Android SDK
ANDROID_SDK_ROOT="/opt/android-sdk"
# User who will run the gradle build (often ec2-user on Amazon Linux, ubuntu on Ubuntu)
# If you run this script AS the user who will build, $(whoami) works well.
BUILD_USER=$(whoami)

echo " --- Configuration ---"
echo "JDK Version       : $JDK_VERSION"
echo "Platform          : $ANDROID_PLATFORM"
echo "Build Tools       : $ANDROID_BUILD_TOOLS"
echo "SDK Root          : $ANDROID_SDK_ROOT"
echo "Build User        : $BUILD_USER"
echo "Cmdline Tools URL : $CMDLINE_TOOLS_URL"
echo " ---"


# --- Detect Package Manager ---
PKG_MANAGER=""
UPDATE_CMD=""
INSTALL_CMD=""
JDK_PACKAGE=""

if command -v yum &> /dev/null; then
    echo "Detected YUM package manager (Amazon Linux, CentOS, RHEL)."
    PKG_MANAGER="yum"
    UPDATE_CMD="sudo yum update -y"
    INSTALL_CMD="sudo yum install -y"
    JDK_PACKAGE="java-${JDK_VERSION}-amazon-corretto-devel"
elif command -v apt-get &> /dev/null; then
    echo "Detected APT package manager (Ubuntu, Debian)."
    PKG_MANAGER="apt"
    UPDATE_CMD="sudo apt-get update && sudo apt-get upgrade -y"
    INSTALL_CMD="sudo apt-get install -y --no-install-recommends" # Avoid extra recommends
    JDK_PACKAGE="openjdk-${JDK_VERSION}-jdk"
else
    echo "ERROR: Unsupported package manager. Please install dependencies manually."
    exit 1
fi

# --- 1. Update System Packages ---
echo " STEP 1: Updating system packages..."
$UPDATE_CMD
echo " STEP 1: System packages updated."

# --- 2. Install Dependencies (JDK, unzip, wget) ---
echo " STEP 2: Installing dependencies (JDK, unzip, wget)..."
$INSTALL_CMD wget unzip $JDK_PACKAGE
echo " STEP 2: Dependencies installed."

# --- Verify Java Installation ---
echo "Verifying Java installation..."
java -version
javac -version
# Set JAVA_HOME (useful for some tools, though Gradle often finds it)
# Find the JDK installation path (heuristic, might need adjustment)
if [ "$PKG_MANAGER" == "yum" ]; then
    # Typical path on Amazon Linux/CentOS for OpenJDK
    JAVA_HOME_PATH=$(dirname $(dirname $(readlink -f $(which javac))))
elif [ "$PKG_MANAGER" == "apt" ]; then
    # Typical path on Ubuntu/Debian for OpenJDK
    JAVA_HOME_PATH="/usr/lib/jvm/java-${JDK_VERSION}-openjdk-amd64" # Common pattern
    # Verify path exists
    if [ ! -d "$JAVA_HOME_PATH" ]; then
         # Fallback discovery
         JAVA_HOME_PATH=$(dirname $(dirname $(readlink -f $(which javac))))
         echo "Warning: Standard JDK path not found, using discovered path: $JAVA_HOME_PATH"
    fi
else
    JAVA_HOME_PATH="" # Unable to determine
    echo "Warning: Could not automatically determine JAVA_HOME path."
fi

if [ -n "$JAVA_HOME_PATH" ] && [ -d "$JAVA_HOME_PATH" ]; then
    echo "Detected JAVA_HOME should be: $JAVA_HOME_PATH"
else
    echo "Warning: JAVA_HOME path detection failed or path doesn't exist."
    JAVA_HOME_PATH="<Set Correct JAVA_HOME Path>" # Placeholder
fi


# --- 3. Download and Install Android SDK Command Line Tools ---
echo " STEP 3: Downloading and installing Android SDK Command Line Tools..."

# Create SDK directory and set permissions
sudo rm -rf "$ANDROID_SDK_ROOT" || true
sudo mkdir -p "$ANDROID_SDK_ROOT"
sudo chown -R $BUILD_USER:$BUILD_USER "$ANDROID_SDK_ROOT" # Grant ownership to build user

# Download Command Line Tools
TMP_DIR=$(mktemp -d)
echo "Downloading command line tools to $TMP_DIR..."
wget -q -O "$TMP_DIR/cmdline-tools.zip" "$CMDLINE_TOOLS_URL"
echo "Download complete."

# Unzip Command Line Tools into the correct SDK structure
# The zip file contains a `cmdline-tools` directory. We need it at $ANDROID_SDK_ROOT/cmdline-tools/latest
echo "Extracting command line tools..."
# Create the target structure first
mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools/latest"
# Unzip directly into the 'latest' directory, skipping the top-level 'cmdline-tools' folder in the zip
unzip -q "$TMP_DIR/cmdline-tools.zip" 'cmdline-tools/*' -d "$ANDROID_SDK_ROOT/cmdline-tools/latest_temp"
# Move contents from the temporary extracted 'cmdline-tools' subdir to the final 'latest' dir
# The unzip command above extracts INTO latest_temp, creating a cmdline-tools dir *inside* it
mv "$ANDROID_SDK_ROOT/cmdline-tools/latest_temp/cmdline-tools/"* "$ANDROID_SDK_ROOT/cmdline-tools/latest/"
# Clean up temporary directories and file
rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/latest_temp"
rm -rf "$TMP_DIR"
echo " STEP 3: Command Line Tools installed to $ANDROID_SDK_ROOT/cmdline-tools/latest"


# --- 4. Set Environment Variables ---
echo " STEP 4: Setting up environment variables..."

# Set for the current session AND persistently
# Using /etc/profile.d ensures it's set system-wide for login shells

# Escape $ for persistent file writing, use variable directly for current export
PROFILE_SCRIPT="/etc/profile.d/android-sdk.sh"
echo "Creating profile script: $PROFILE_SCRIPT"

# Use cat with EOF to write multiline content with sudo
sudo bash -c "cat > $PROFILE_SCRIPT" <<EOF
#!/bin/bash
export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT"
export ANDROID_HOME="\$ANDROID_SDK_ROOT" # Some older tools might still use ANDROID_HOME
export JAVA_HOME="$JAVA_HOME_PATH"      # Set JAVA_HOME persistently
export PATH="\$PATH:\$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:\$ANDROID_SDK_ROOT/platform-tools"
EOF

sudo chmod +x $PROFILE_SCRIPT

# Export for the *current* script execution (needed for sdkmanager below)
export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT"
export ANDROID_HOME="$ANDROID_SDK_ROOT" # Set for current session too
# Export JAVA_HOME if detected
if [ -n "$JAVA_HOME_PATH" ] && [ -d "$JAVA_HOME_PATH" ]; then
    export JAVA_HOME="$JAVA_HOME_PATH"
fi
export PATH="$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools"

echo "Environment variables configured. You may need to log out and log back in, or run 'source $PROFILE_SCRIPT' for them to apply to *new* shells."
echo "Current PATH: $PATH"
echo "ANDROID_SDK_ROOT: $ANDROID_SDK_ROOT"
echo "JAVA_HOME: $JAVA_HOME"
echo " STEP 4: Environment variables set."


# --- 5. Use SDK Manager to Install Required SDK Packages ---
echo " STEP 5: Installing SDK platform-tools, platforms, and build-tools..."
echo "          This may take a while depending on download speed."

# Ensure sdkmanager exists and is executable
if [ ! -x "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
    echo "ERROR: sdkmanager not found or not executable at $ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
    exit 1
fi

# Accept licenses automatically
echo "Accepting SDK licenses..."
yes | "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --licenses

# Install platform-tools, specified platform, and build-tools
echo "Installing packages: platform-tools, platforms;$ANDROID_PLATFORM, build-tools;$ANDROID_BUILD_TOOLS"
"$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;$ANDROID_PLATFORM" "build-tools;$ANDROID_BUILD_TOOLS"
# Consider adding "emulator" and "system-images;${ANDROID_PLATFORM};google_apis;x86_64" if you need to run emulators

echo " STEP 5: Required SDK packages installed."


# --- 6. Final Permissions Check (Redundant if chown above worked) ---
echo " STEP 6: Ensuring correct ownership for $ANDROID_SDK_ROOT..."
sudo chown -R $BUILD_USER:$BUILD_USER "$ANDROID_SDK_ROOT"
echo " STEP 6: Permissions checked."

# --- 7. Gradle installation ---
curl -s "https://get.sdkman.io" | bash
source "/home/ec2-user/.sdkman/bin/sdkman-init.sh"
sdk install gradle 8.13

# --- Done ---
echo ""
echo " SCRIPT COMPLETE: Android build environment setup finished!"
echo " RUN THE FOLLOWING: "
echo "source \"/home/ec2-user/.sdkman/bin/sdkman-init.sh\""
echo "source \"/etc/profile.d/android-sdk.sh\""
echo ""
