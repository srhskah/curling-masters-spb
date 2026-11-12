ARG BASE_IMAGE=mcr.registry.cdn.w7.cc/openjdk/jdk:21-ubuntu
FROM ${BASE_IMAGE}

# 设置工作目录
WORKDIR /app

# 工具 + 字体合并为单层：一次 apt-get update，变更少时整层命中缓存，避免重复拉包
# （deploy 使用 DOCKER_BUILDKIT=0，不用 BuildKit cache mount，依赖经典层缓存）
RUN set -eux; \
    # 1. 优先处理 deb822 格式的新配置文件
    if [ -f /etc/apt/sources.list.d/ubuntu.sources ]; then \
        sed -i 's|http://archive.ubuntu.com/ubuntu/|https://mirrors.tuna.tsinghua.edu.cn/ubuntu/|g' /etc/apt/sources.list.d/ubuntu.sources && \
        sed -i 's|http://security.ubuntu.com/ubuntu/|http://mirrors.tuna.tsinghua.edu.cn/ubuntu/|g' /etc/apt/sources.list.d/ubuntu.sources && \
        sed -i 's|http://azure.archive.ubuntu.com/ubuntu/|https://mirrors.tuna.tsinghua.edu.cn/ubuntu/|g' /etc/apt/sources.list.d/ubuntu.sources; \
    fi; \
    # 2. 同时处理传统的 sources.list 文件，作为兼容
    if [ -f /etc/apt/sources.list ]; then \
        sed -i 's|http://archive.ubuntu.com/ubuntu/|http://mirrors.tuna.tsinghua.edu.cn/ubuntu/|g' /etc/apt/sources.list && \
        sed -i 's|http://security.ubuntu.com/ubuntu/|http://mirrors.tuna.tsinghua.edu.cn/ubuntu/|g' /etc/apt/sources.list && \
        sed -i 's|http://azure.archive.ubuntu.com/ubuntu/|https://mirrors.tuna.tsinghua.edu.cn/ubuntu/|g' /etc/apt/sources.list; \
    fi; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
        curl \
        wget \
        fontconfig \
        fonts-wqy-zenhei \
        fonts-wqy-microhei \
        fonts-dejavu-core \
        fonts-noto-core \
        fonts-noto-unhinted \
        fonts-noto-color-emoji \
    ; \
    rm -rf /var/lib/apt/lists/*

# 仅复制 WAR：放在 apt 层之后，代码/产物变更不会迫使重新下载 apt 包
COPY build/libs/demo-0.0.1-SNAPSHOT.war app.war

# 单色 Noto Emoji（可选）：本地已有 NotoEmoji-Regular.ttf 时再解开，勿 COPY 整个构建上下文
# COPY NotoEmoji-Regular.ttf /tmp/NotoEmoji-Regular.ttf
# COPY install-font.sh /tmp/install-font.sh
# RUN chmod +x /tmp/install-font.sh && \
#     mkdir -p /build-context && mv /tmp/NotoEmoji-Regular.ttf /build-context/ && \
#     /tmp/install-font.sh && \
#     rm -rf /build-context /tmp/install-font.sh

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
