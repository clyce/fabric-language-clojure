#!/bin/bash

# ========================================
# Extract Single Minecraft Class API
# ========================================

if [ -z "$1" ]; then
    echo "Usage: ./extract-class.sh <full.class.name>"
    echo ""
    echo "Example:"
    echo "  ./extract-class.sh net.minecraft.world.item.ItemStack"
    echo ""
    echo "This will generate:"
    echo "  - api_ref/ItemStack.md     (human-readable)"
    echo "  - api_ref/ItemStack.json   (machine-readable)"
    echo ""
    exit 1
fi

CLASS_NAME="$1"

# Extract simple class name from full path
SIMPLE_NAME="${CLASS_NAME##*.}"

echo "========================================"
echo "Extracting API: $CLASS_NAME"
echo "========================================"
echo ""

# Check if already exists
if [ -f "api_ref/$SIMPLE_NAME.md" ]; then
    echo "Warning: api_ref/$SIMPLE_NAME.md already exists"
    echo ""
    read -p "Overwrite? (y/N): " OVERWRITE
    if [[ ! "$OVERWRITE" =~ ^[Yy]$ ]]; then
        echo "Cancelled."
        exit 0
    fi
    echo ""
fi

echo "Extracting..."
./gradlew :common:extractApi -PapiClasses="$CLASS_NAME" --console=plain

echo ""
if [ -f "api_ref/$SIMPLE_NAME.md" ]; then
    echo "========================================"
    echo "SUCCESS!"
    echo "========================================"
    echo ""
    echo "Generated files:"
    echo "  - api_ref/$SIMPLE_NAME.md"
    echo "  - api_ref/$SIMPLE_NAME.json"
    echo ""
    echo "View documentation:"
    echo "  cat api_ref/$SIMPLE_NAME.md"
    echo ""
else
    echo "========================================"
    echo "FAILED"
    echo "========================================"
    echo ""
    echo "The class could not be extracted. Possible reasons:"
    echo "  1. Class name is incorrect"
    echo "  2. Class requires Minecraft bootstrap"
    echo "  3. Class is not accessible via reflection"
    echo ""
    echo "Try checking the full class name with:"
    echo "  https://linkie.shedaniel.dev/mappings"
    echo ""
fi
