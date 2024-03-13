#!/usr/bin/env bash

# Constants
SNAPSHOT_DIR="app/src/test/snapshots/images"
RESULT_PNG="result.png"

SCREENSHOT_FOLDER="app/src/test/snapshots/images/Pixel 8 Pro"
SCREENSHOT_1="$SCREENSHOT_FOLDER/ca.amandeep.path.ui.main_MainScreenshotTest_screenshotMain[darkMode=true, device=Pixel 8 Pro, alertsExp=null, dirWarn=false, helpGuide=false, showOppoDir=false, showNotifs=true, updatedWhen=0 shortNames=false].png"
SCREENSHOT_2="$SCREENSHOT_FOLDER/ca.amandeep.path.ui.main_MainScreenshotTest_screenshotMain[darkMode=false, device=Pixel 8 Pro, alertsExp=true, dirWarn=false, helpGuide=true, showOppoDir=false, showNotifs=true, updatedWhen=0 shortNames=false].png"
SCREENSHOT_3_1="$SCREENSHOT_FOLDER/ca.amandeep.path.ui.main_MainScreenshotTest_screenshotMain[darkMode=true, device=Pixel 8 Pro, alertsExp=null, dirWarn=false, helpGuide=true, showOppoDir=true, showNotifs=true, updatedWhen=0 shortNames=false].png"
SCREENSHOT_3_2="$SCREENSHOT_FOLDER/ca.amandeep.path.ui.main_MainScreenshotTest_screenshotMain[darkMode=false, device=Pixel 8 Pro, alertsExp=null, dirWarn=false, helpGuide=true, showOppoDir=true, showNotifs=true, updatedWhen=0 shortNames=false].png"
SCREENSHOT_3="DiagonalDown.png"
SCREENSHOT_4="$SCREENSHOT_FOLDER/ca.amandeep.path.ui.main_MainScreenshotTest_screenshotMain[darkMode=true, device=Pixel 8 Pro, alertsExp=false, dirWarn=false, helpGuide=true, showOppoDir=false, showNotifs=true, updatedWhen=0 shortNames=false].png"

FRAME_1="images/frame1.webp"
FRAME_2="images/frame2.webp"
FRAME_3="images/frame3.webp"
FRAME_4="images/frame4.webp"
FRAMED_1="images/framescr1.webp"
FRAMED_2="images/framescr2.webp"
FRAMED_3="images/framescr3.webp"
FRAMED_4="images/framescr4.webp"

# Function to check if a command exists
command_exists() {
    command -v "$1" &>/dev/null
}

# Verify all required commands are available
for cmd in mogrify convert python3; do
    if ! command_exists "$cmd"; then
        echo "Error: '$cmd' is not available. Please install it and try again."
        exit 1
    fi
done

echo "Preparing environment..."
rm -rf "$SNAPSHOT_DIR"

echo "Generating and organizing Paparazzi images..."
if ! ./gradlew recordPaparazziDebug; then
    echo "Error during 'recordPaparazziDebug'. Please check your setup."
    exit 1
fi

if ! python3 scripts/organize_paparazzi_images.py; then
    echo "Error during 'organize_paparazzi_images.py'. Please check the script."
    exit 1
fi

echo "Trimming images..."
for f in "$SCREENSHOT_1" "$SCREENSHOT_2" "$SCREENSHOT_3_1" "$SCREENSHOT_3_2" "$SCREENSHOT_4"; do
    if ! mogrify -trim +repage "$f"; then
        echo "Error trimming $f. Please check the image file and mogrify command."
        exit 1
    fi
done

echo "Diagonalizing image..."
rm -f "$SCREENSHOT_3"
scripts/diagonalCombine.sh "$SCREENSHOT_3_1" "$SCREENSHOT_3_2"

echo "Framing images..."
for i in {1..4}; do
    FRAME_VAR="FRAME_$i"
    SCREENSHOT_VAR="SCREENSHOT_$i"
    FRAMED_VAR="FRAMED_$i"
    
    rm -f "$RESULT_PNG"
    if ! framer --oxipng-level 6 --pngquant-speed 1 "${!FRAME_VAR}" "${!SCREENSHOT_VAR}"; then
        echo "Error during image optimization. Please check 'framer' command."
        exit 1
    fi
    
    rm -f "${!FRAMED_VAR}"
    if ! convert -define webp:lossless=true -quality 100 "$RESULT_PNG" "${!FRAMED_VAR}"; then
        echo "Error converting $RESULT_PNG to WebP format. Please check the 'convert' command."
        exit 1
    fi
    rm -f "$RESULT_PNG"
    echo "Process completed successfully. Output file: $FRAMED_VAR"
    rm -f "$SCREENSHOT_VAR"
done

rm -f "$SCREENSHOT_3"

for file in images/framescr*.webp; do 
    rm -f "${file%.webp}.png"
    convert "$file" "${file%.webp}.png" 
done
echo "Process completed successfully."