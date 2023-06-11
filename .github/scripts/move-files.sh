#!/bin/bash
set -e
shopt -s globstar nullglob extglob

# Get APKs from previous jobs' artifacts
cp -R ~/artifacts/ $PWD
APKS=(**/apk/*.apk)
JARS=(**/jar/*.jar)
ICONS=(**/icon/*.png)

# Fail if too few APKs have been found
if [ "${#APKS[@]}" -le "10" ]; then
    echo "Insufficient amount of APKs found. Please check the project configuration."
    exit 1
else
    echo "Moving ${#APKS[@]} APKs"
fi

DEST_JSON=$PWD/repo
DEST_APK=$PWD/repo/apk
DEST_JAR=$PWD/repo/jar
DEST_ICON=$PWD/repo/icon

rm -rf "$DEST_JSON" "$DEST_APK" "$DEST_JAR" "$DEST_ICON" && mkdir -p "$DEST_JSON" "$DEST_APK" "$DEST_JAR" "$DEST_ICON"

# Move APK files to apk folder
for APK in "${APKS[@]}"; do
    BASENAME=$(basename "$APK")
    APKDEST="$DEST_APK/$BASENAME"

    cp "$APK" "$APKDEST"
done

# Move JAR files to jar folder
for JAR in "${JARS[@]}"; do
    BASENAME=$(basename "$JAR")
    JARDEST="$DEST_JAR/$BASENAME"

    cp "$JAR" "$JARDEST"
done

# Move JAR files to jar folder
for ICON in "${ICONS[@]}"; do
    BASENAME=$(basename "$ICON")
    ICONSDEST="$DEST_ICON/$BASENAME"

    cp "$ICON" "$ICONSDEST"
done

# Merge JSON arrays into a single JSON array
MERGED_JSON="index.json"
MINIFIED_MERGED_JSON="index.min.json"
JSON_FILES=(**/index.min.json)

# Create an empty array to store JSON objects
JSON_OBJECTS=()

# Read each JSON file and extract the JSON objects
for JSON_FILE in "${JSON_FILES[@]}"; do
    # Read the content of the JSON file
    JSON_CONTENT=$(cat "$JSON_FILE")

    # Check if the JSON content is an array
    if [[ $JSON_CONTENT == \[*\] ]]; then
        # Remove brackets from the JSON content
        JSON_CONTENT=${JSON_CONTENT#"["}
        JSON_CONTENT=${JSON_CONTENT%"]"}
        JSON_OBJECTS+=("$JSON_CONTENT")
    fi
done

# Create a single JSON array from the extracted JSON objects
MERGED_CONTENT=$(IFS=,; echo "[${JSON_OBJECTS[*]}]")

# Save the merged JSON content to a file
echo "$MERGED_CONTENT" > "$DEST_JSON/$MINIFIED_MERGED_JSON"

# Beautify the merged JSON file
jq '.' "$DEST_JSON/$MINIFIED_MERGED_JSON" > "$DEST_JSON/$MERGED_JSON"