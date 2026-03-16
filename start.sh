#!/bin/bash
# Curling Masters 应用启动脚本
# 每次运行都会创建带时间戳的独立日志文件

# 设置时区环境变量
export TZ=Asia/Shanghai
export JAVA_OPTS="-Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8"

# 生成启动时间戳
START_TIME=$(date +%Y-%m-%d_%H-%M-%S)
echo "启动时间: $START_TIME"
echo "时区设置: $TZ"
echo "Java时区参数: $JAVA_OPTS"
echo "日志文件将保存到: logs/curling-masters_${START_TIME}.log"

# 设置环境变量并启动应用
cd /home/srhskah/SpB/curling-masters
export START_TIME=$START_TIME
./gradlew build
./gradlew bootRun -Dorg.gradle.jvmargs="-Duser.timezone=Asia/Shanghai"