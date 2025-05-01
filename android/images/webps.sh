#!/usr/bin/env sh
set -e

for file in *.png; do
    convert "$file" -quality 100 -define webp:lossless=true "${file%.png}.webp"
done