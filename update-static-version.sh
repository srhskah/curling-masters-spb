#!/bin/bash

# 更新静态资源版本号的脚本
# 用法: ./update-static-version.sh [新版本号]

NEW_VERSION=${1:-"1.2"}

echo "正在更新静态资源版本号到: $NEW_VERSION"

# 更新所有模板文件中的版本号
find src/main/resources/templates -name "*.html" -exec sed -i "s|main\.css?v=[0-9.]*|main.css?v=$NEW_VERSION|g" {} \;
find src/main/resources/templates -name "*.html" -exec sed -i "s|main\.js?v=[0-9.]*|main.js?v=$NEW_VERSION|g" {} \;

echo "版本号更新完成! 新版本: $NEW_VERSION"
echo "请重启应用以使更改生效"
