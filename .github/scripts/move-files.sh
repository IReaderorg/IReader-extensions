#!/bin/bash
set -e
shopt -s globstar nullglob extglob

# Get APKs from previous jobs' artifacts
cp -R ~/artifacts/ $PWD
ALL_APKS=(**/apk/*.apk)
JARS=(**/jar/*.jar)
ICONS=(**/icon/*.png)
JS_FILES=(**/js/*.js)

# Load working sources filter if working-sources.json exists
# Script runs from master/ directory, file is in repo root
WORKING_SOURCES_JSON="working-sources.json"
ENABLED_SOURCES=""
if [ -f "$WORKING_SOURCES_JSON" ]; then
    echo "Found working-sources.json"
    ENABLED_SOURCES=$(python3 -c "
import json
data = json.load(open('$WORKING_SOURCES_JSON'))
sources = data.get('sources', {})
enabled = [k for k, v in sources.items() if v.get('enabled', False)]
print(' '.join(enabled))
")
    echo "Enabled sources count: $(echo $ENABLED_SOURCES | wc -w)"
else
    echo "working-sources.json not found, building all sources"
fi

# Filter APKs based on working sources
if [ -n "$ENABLED_SOURCES" ]; then
    APKS=()
    for APK in "${ALL_APKS[@]}"; do
        BASENAME=$(basename "$APK")
        # APK format: ireader-{lang}-{name}-v{version}.apk
        # Extract source name (third part after splitting by -)
        SOURCE_NAME=$(echo "$BASENAME" | sed -E 's/^ireader-[^-]+-([^-]+)-v.*/\1/')
        if echo "$ENABLED_SOURCES" | grep -qw "$SOURCE_NAME"; then
            APKS+=("$APK")
        fi
    done
    echo "Working sources filter: ${#APKS[@]} of ${#ALL_APKS[@]} APKs enabled"
else
    APKS=("${ALL_APKS[@]}")
fi

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
DEST_JS=$PWD/repo/js

rm -rf "$DEST_JSON" "$DEST_APK" "$DEST_JAR" "$DEST_ICON" "$DEST_JS" && mkdir -p "$DEST_JSON" "$DEST_APK" "$DEST_JAR" "$DEST_ICON" "$DEST_JS"

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

# Move Icon files to icon folder
for ICON in "${ICONS[@]}"; do
    BASENAME=$(basename "$ICON")
    ICONSDEST="$DEST_ICON/$BASENAME"

    cp "$ICON" "$ICONSDEST"
done

# Move JS files to js folder
echo "Moving ${#JS_FILES[@]} JS files"
for JS in "${JS_FILES[@]}"; do
    BASENAME=$(basename "$JS")
    JSDEST="$DEST_JS/$BASENAME"

    cp "$JS" "$JSDEST"
done

# Merge JSON arrays into a single JSON array
MERGED_JSON="index.json"
MINIFIED_MERGED_JSON="index.min.json"
JSON_FILES=(**/index.min.json)

# Filter and merge index entries
if [ -n "$ENABLED_SOURCES" ] && [ ${#JSON_FILES[@]} -gt 0 ]; then
    # Use Python to filter and merge JSON with working sources
    python3 -c "
import json, glob, sys

enabled = set('''$ENABLED_SOURCES'''.split())
all_entries = []

for f in glob.glob('**/index.min.json', recursive=True):
    try:
        with open(f) as fh:
            data = json.load(fh)
            if isinstance(data, list):
                all_entries.extend(data)
    except:
        pass

# Filter entries by source name
filtered = [e for e in all_entries if e.get('name', '') in enabled]

with open('$DEST_JSON/$MINIFIED_MERGED_JSON', 'w') as out:
    json.dump(filtered, out, separators=(',', ':'))

with open('$DEST_JSON/$MERGED_JSON', 'w') as out:
    json.dump(filtered, out, indent=2)

print(f'Filtered index: {len(filtered)} of {len(all_entries)} entries')
"
else
    # Fallback: merge without filtering
    JSON_OBJECTS=()
    for JSON_FILE in "${JSON_FILES[@]}"; do
        JSON_CONTENT=$(cat "$JSON_FILE")
        if [[ $JSON_CONTENT == \[*\] ]]; then
            JSON_CONTENT=${JSON_CONTENT#"["}
            JSON_CONTENT=${JSON_CONTENT%"]"}
            JSON_OBJECTS+=("$JSON_CONTENT")
        fi
    done
    MERGED_CONTENT=$(IFS=,; echo "[${JSON_OBJECTS[*]}]")
    echo "$MERGED_CONTENT" > "$DEST_JSON/$MINIFIED_MERGED_JSON"
    jq '.' "$DEST_JSON/$MINIFIED_MERGED_JSON" > "$DEST_JSON/$MERGED_JSON"
fi