#!/bin/bash
# Batch convert lnreader-plugins to IReader extensions

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check if lnreader-plugins directory exists
if [ ! -d "lnreader-plugins-master/plugins" ]; then
    echo -e "${RED}Error: lnreader-plugins-master/plugins directory not found${NC}"
    echo "Please ensure lnreader-plugins-master is in the current directory"
    exit 1
fi

# Check if Python is available
if ! command -v python3 &> /dev/null; then
    echo -e "${RED}Error: python3 is required but not installed${NC}"
    exit 1
fi

# Function to convert a single plugin
convert_plugin() {
    local plugin_file=$1
    local lang=$2
    
    echo -e "${YELLOW}Converting: $(basename $plugin_file)${NC}"
    
    if python3 scripts/js-to-kotlin-converter.py "$plugin_file" "$lang" ./sources; then
        echo -e "${GREEN}✓ Success${NC}"
        return 0
    else
        echo -e "${RED}✗ Failed${NC}"
        return 1
    fi
}

# Language mapping
declare -A lang_map=(
    ["english"]="en"
    ["arabic"]="ar"
    ["chinese"]="cn"
    ["french"]="fr"
    ["indonesian"]="in"
    ["japanese"]="ja"
    ["korean"]="ko"
    ["polish"]="pl"
    ["portuguese"]="pt"
    ["russian"]="ru"
    ["spanish"]="es"
    ["thai"]="th"
    ["turkish"]="tu"
    ["ukrainian"]="uk"
    ["vietnamese"]="vi"
)

# Counters
total=0
success=0
failed=0

# Get language filter from command line
lang_filter=${1:-"all"}

echo -e "${GREEN}=== IReader Extension Batch Converter ===${NC}"
echo "Converting lnreader-plugins to IReader extensions"
echo ""

# Iterate through language directories
for lang_dir in lnreader-plugins-master/plugins/*/; do
    lang_name=$(basename "$lang_dir")
    
    # Skip if not in language map
    if [ -z "${lang_map[$lang_name]}" ]; then
        continue
    fi
    
    lang_code="${lang_map[$lang_name]}"
    
    # Skip if language filter is set and doesn't match
    if [ "$lang_filter" != "all" ] && [ "$lang_filter" != "$lang_code" ]; then
        continue
    fi
    
    echo -e "${GREEN}Processing $lang_name ($lang_code)...${NC}"
    
    # Convert each plugin in the language directory
    for plugin_file in "$lang_dir"*.ts; do
        if [ -f "$plugin_file" ]; then
            ((total++))
            
            if convert_plugin "$plugin_file" "$lang_code"; then
                ((success++))
            else
                ((failed++))
            fi
            
            echo ""
        fi
    done
done

# Summary
echo -e "${GREEN}=== Conversion Summary ===${NC}"
echo "Total plugins: $total"
echo -e "${GREEN}Successful: $success${NC}"
if [ $failed -gt 0 ]; then
    echo -e "${RED}Failed: $failed${NC}"
fi

echo ""
echo "Next steps:"
echo "1. Review generated extensions in ./sources"
echo "2. Update TODO comments in each extension"
echo "3. Test extensions in Android Studio"
echo "4. Add icons for each extension"

exit 0
