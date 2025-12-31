#!/bin/bash
# 便捷脚本：提取所有常用的 Minecraft API

echo "========================================"
echo "Minecraft API Extractor - ALL COMMON APIs"
echo "========================================"
echo ""
echo "This will extract 40+ common Minecraft classes"
echo "including items, blocks, entities, enchantments,"
echo "data components, and more..."
echo ""
echo "Location: api_ref/"
echo ""

./gradlew :common:extractAllCommonApis

echo ""
echo "========================================"
echo "API extraction completed!"
echo "Check the api_ref directory for results."
echo "========================================"
