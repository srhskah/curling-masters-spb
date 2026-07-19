FROM mcr.microsoft.com/openjdk/jdk:21-ubuntu

# 设置工作目录
WORKDIR /app

# 安装必要的工具和字体
RUN sed -i 's|http://archive.ubuntu.com/ubuntu/|http://mirrors.tuna.tsinghua.edu.cn/ubuntu/|g' /etc/apt/sources.list && \
    sed -i 's|http://security.ubuntu.com/ubuntu/|http://mirrors.tuna.tsinghua.edu.cn/ubuntu/|g' /etc/apt/sources.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        curl \
        wget \
        fontconfig \
        && rm -rf /var/lib/apt/lists/*

# 安装中文字体支持
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        fonts-wqy-zenhei \
        fonts-wqy-microhei \
        fonts-dejavu-core \
        fonts-noto-core \
        fonts-noto-unhinted \
        fonts-noto-color-emoji \
        && rm -rf /var/lib/apt/lists/*

# 复制字体安装脚本和构建产物
COPY install-font.sh /tmp/install-font.sh
COPY build/libs/demo-0.0.1-SNAPSHOT.war app.war

# 复制整个构建上下文到临时目录（用于检测本地字体文件）
COPY . /build-context/

# 安装单色 Noto Emoji（优先使用本地文件，否则从网络下载）
RUN chmod +x /tmp/install-font.sh && \
    /tmp/install-font.sh && \
    rm -rf /build-context /tmp/install-font.sh

# 创建非root用户
# RUN groupadd -r appuser && useradd -r -g appuser appuser && \
#     chown -R appuser:appuser ./app

# # 切换到非root用户
# USER appuser

# 暴露端口
EXPOSE 8080

# 设置JVM参数（优化容器环境）
ENV JAVA_OPTS="-Xmx1g -Xms512m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.war"]
