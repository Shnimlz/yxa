#!/bin/bash
IMG="/home/shni/.gemini/antigravity/brain/4df4f203-de31-49ca-8df4-d426918298a3/media__1777096154481.jpg"

# Generate legacy icons
magick "$IMG" -resize 48x48 app/src/main/res/mipmap-mdpi/ic_launcher.webp
magick "$IMG" -resize 72x72 app/src/main/res/mipmap-hdpi/ic_launcher.webp
magick "$IMG" -resize 96x96 app/src/main/res/mipmap-xhdpi/ic_launcher.webp
magick "$IMG" -resize 144x144 app/src/main/res/mipmap-xxhdpi/ic_launcher.webp
magick "$IMG" -resize 192x192 app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp

# Generate round icons (since image is already black background with circle, we just copy)
cp app/src/main/res/mipmap-mdpi/ic_launcher.webp app/src/main/res/mipmap-mdpi/ic_launcher_round.webp
cp app/src/main/res/mipmap-hdpi/ic_launcher.webp app/src/main/res/mipmap-hdpi/ic_launcher_round.webp
cp app/src/main/res/mipmap-xhdpi/ic_launcher.webp app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp
cp app/src/main/res/mipmap-xxhdpi/ic_launcher.webp app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp
cp app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp

# Generate adaptive foreground (108x108 base, pad original image so it fits safe zone 72x72)
# We calculate: original is 100%. We want it to be 72/108 = 66.6% of the final size.
# Wait, let's just make the foreground 108x108 directly by centering the 72x72 version onto a transparent 108x108 canvas.
magick "$IMG" -resize 72x72 -background none -gravity center -extent 108x108 app/src/main/res/mipmap-mdpi/ic_launcher_foreground.webp
magick "$IMG" -resize 108x108 -background none -gravity center -extent 162x162 app/src/main/res/mipmap-hdpi/ic_launcher_foreground.webp
magick "$IMG" -resize 144x144 -background none -gravity center -extent 216x216 app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.webp
magick "$IMG" -resize 216x216 -background none -gravity center -extent 324x324 app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.webp
magick "$IMG" -resize 288x288 -background none -gravity center -extent 432x432 app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp

# Update adaptive icon xmls
cat << 'XML' > app/src/main/res/mipmap-anydpi/ic_launcher.xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
XML

cp app/src/main/res/mipmap-anydpi/ic_launcher.xml app/src/main/res/mipmap-anydpi/ic_launcher_round.xml

# Update background color
cat << 'XML' > app/src/main/res/values/ic_launcher_background.xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#000000</color>
</resources>
XML

# Delete old drawable background/foreground
rm -f app/src/main/res/drawable/ic_launcher_background.xml
rm -f app/src/main/res/drawable/ic_launcher_foreground.xml
