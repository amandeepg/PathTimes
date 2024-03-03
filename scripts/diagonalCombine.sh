#!/usr/bin/env bash
 
#This script will merge two jpg images into one using imageMagick.
#The final result will be a picture that is split diagonally.
#The diagonal line will start from the top left of the image.
#Both pictures must be of the same size.
#If you do not give the filenames as part of the command line, the default names will be used (Left.jpg and Right.jpg).
 
#If command line argument 1 is not provided, the value will default to the variable $LEFT_DEFAULT
LEFT_DEFAULT="Left.jpg";
LEFT=${1:-$LEFT_DEFAULT};
 
#If command line argument 2 is not provided, the value will default to the variable $Right_DEFAULT
RIGHT_DEFAULT="Right.jpg";
RIGHT=${2:-$RIGHT_DEFAULT};
 
#The intermediate images we will use must be png to support transparency.
#We remove the extension '.jpg' from the filenames and add the extension '.png'.
LEFT_OUT="${LEFT%.jpg}.Diagonal Down.png";
RIGHT_OUT="${RIGHT%.jpg}.Diagonal Down.png";
OUT="DiagonalDown.jpg";
OUT_PNG="DiagonalDown.png";
 
#Read the width of one of the images;
WIDTH=`identify -format %w "$LEFT"`;
#Read the height of one of the images;
HEIGHT=`identify -format %h "$LEFT"`;
 
OFFSET=1;
WIDTH_M=$((WIDTH-OFFSET));
HEIGHT_M=$((HEIGHT-OFFSET));
 
#We create a transparent triangle on the side of the image we do not want visible.
#A problem that arises here: we create a triangle with no fill, which turns black. Then we fill that area to make it transparent, since the image is a jpg some of the newly created black pixels do not get removed. When merging the two images, this creates a black diagonal line in the middle.
magick "$LEFT" -gravity north -crop "$WIDTH"x"$HEIGHT"+0+0 +repage \
-draw "polygon 0,0 "$WIDTH","$HEIGHT" "$WIDTH",0 fill none alpha "$WIDTH_M","$OFFSET" floodfill" \
\( +clone -channel RGBA \) \
-compose DstOver -composite "$LEFT_OUT";
 
#We create a transparent triangle on the side of the image we do not want visible.
magick "$RIGHT" -gravity north -crop "$WIDTH"x"$HEIGHT"+0+0 +repage \
-draw "polygon "$WIDTH","$HEIGHT" 0,0 0,"$HEIGHT" fill none alpha "$OFFSET","$HEIGHT_M" floodfill" \
\( +clone -channel RGBA \) \
-compose DstOver -composite "$RIGHT_OUT";
 
#We merge the two images together.
composite -blend 50% "$LEFT_OUT" "$RIGHT_OUT" "$OUT";
 
#Cleaning up
rm "$LEFT_OUT" "$RIGHT_OUT";
convert -define webp:lossless=true -quality 50 "$OUT" "$OUT_PNG"
rm $OUT