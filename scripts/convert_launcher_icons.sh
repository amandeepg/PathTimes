#!/bin/bash

# Source file
SOURCE_FILE="mipmap-xxxhdpi/ic_launcher_foreground.png"

# Check if source file exists
if [ ! -f "$SOURCE_FILE" ]; then
    echo "Error: Source file $SOURCE_FILE not found!"
    exit 1
fi

# Check if oxipng is installed
if ! command -v oxipng &> /dev/null; then
    echo "Error: oxipng is not installed. Please install it first."
    echo "Install with: cargo install oxipng or download from https://github.com/shssoichiro/oxipng"
    exit 1
fi

# Create output directories if they don't exist
mkdir -p mipmap-xxhdpi mipmap-xhdpi mipmap-hdpi mipmap-mdpi

# Function to optimize PNG with oxipng
optimize_png() {
    local file=$1
    echo "Optimizing $file with oxipng..."
    oxipng -o 6 --strip safe --alpha "$file"
}

echo "Converting to xxhdpi (75%)..."
convert "$SOURCE_FILE" -resize 75% "mipmap-xxhdpi/ic_launcher_foreground.png"
optimize_png "mipmap-xxhdpi/ic_launcher_foreground.png"

echo "Converting to xhdpi (50%)..."
convert "$SOURCE_FILE" -resize 50% "mipmap-xhdpi/ic_launcher_foreground.png"
optimize_png "mipmap-xhdpi/ic_launcher_foreground.png"

echo "Converting to hdpi (37.5%)..."
convert "$SOURCE_FILE" -resize 37.5% "mipmap-hdpi/ic_launcher_foreground.png"
optimize_png "mipmap-hdpi/ic_launcher_foreground.png"

echo "Converting to mdpi (25%)..."
convert "$SOURCE_FILE" -resize 25% "mipmap-mdpi/ic_launcher_foreground.png"
optimize_png "mipmap-mdpi/ic_launcher_foreground.png"

# Optimize the original input image
echo "Optimizing original input image..."
optimize_png "$SOURCE_FILE"

echo "Conversion and optimization complete!"
