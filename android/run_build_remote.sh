#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# --- Configuration ---
LOCAL_BASE_PATH="."

# Read EC2 IP from file
EC2_IP=$(aws ec2 describe-instances --filters "Name=instance-state-name,Values=running" --query 'Reservations[*].Instances[*].[PublicIpAddress]' --output text)
EC2_USER="ec2-user"
REMOTE_BASE_PATH="/home/${EC2_USER}/PathTimes/android"
REMOTE_OUTPUT_DIR="${REMOTE_BASE_PATH}/build"
LOCAL_OUTPUT_DIR="${LOCAL_BASE_PATH}/build"

# Remote build command
REMOTE_BUILD_COMMAND="cd ${REMOTE_BASE_PATH} && dos2unix * && ./gradlew assembleDebug"

# --- Script Steps ---

echo "EC2 IP: ${EC2_IP}"
echo ""
echo "--- Starting build process ---"

# 1. Push source code
echo "--> Pushing source code to ${EC2_USER}@${EC2_IP}:${REMOTE_BASE_PATH}/"
# Use trailing slash on source path to copy contents
rsync -avz --mkpath \
      --exclude={'build','.gradle','.idea','.kotlin','.git','local.properties'} \
      "${LOCAL_BASE_PATH}/" "${EC2_USER}@${EC2_IP}:${REMOTE_BASE_PATH}/"
echo "Source push complete."
echo ""

# 2. Run remote build command
echo "--> Running remote command on ${EC2_USER}@${EC2_IP}:"
echo "    '${REMOTE_BUILD_COMMAND}'"
ssh "${EC2_USER}@${EC2_IP}" "${REMOTE_BUILD_COMMAND}" || {
    echo "ERROR: Remote build command failed!"
    exit 1
}
echo "Remote build command finished."
echo ""

# 3. Pull build output files
echo "--> Pulling build output files from ${EC2_USER}@${EC2_IP}:${REMOTE_OUTPUT_DIR}/"
# Use trailing slash on source path to copy contents
rsync -av --mkpath --quiet \
      "${EC2_USER}@${EC2_IP}:${REMOTE_OUTPUT_DIR}/" "${LOCAL_OUTPUT_DIR}/"
echo "Build output pull complete."
echo ""

echo "ADB install"
adb install build/app/outputs/apk/debug/app-debug.apk
adb shell am start -n "ca.amandeep.path/ca.amandeep.path.MainActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
echo "APK installed"

echo "--- Process finished successfully ---"