#!/bin/bash

# Check if oxipng is installed
if ! command -v oxipng &> /dev/null; then
    echo "Error: oxipng is not installed. Please install it first."
    echo "Installation: cargo install oxipng"
    exit 1
fi

# Check if pngquant is installed
if ! command -v pngquant &> /dev/null; then
    echo "Error: pngquant is not installed. Please install it first."
    echo "Installation: cargo install pngquant"
    exit 1
fi

# Optimize all PNGs with max settings
for png in *.png; do
    if [ -f "$png" ]; then
        echo "Optimizing: $png"
        pngquant --force --speed 1 --quality 100 --strip --skip-if-larger -o "$png" -- "$png"
        oxipng -o max --strip all -Z "$png"
    else
        echo "No PNG files found in current directory."
        exit 1
    fi
done

echo "PNG optimization complete!"