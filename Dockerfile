FROM mcr.microsoft.com/openjdk/jdk:21-ubuntu

# 设置工作目录
WORKDIR /app

# 安装必要的工具和字体
RUN sed -i 's|http://archive.ubuntu.com/ubuntu/|http://mirrors.aliyun.com/ubuntu/|g' /etc/apt/sources.list && \
    sed -i 's|http://security.ubuntu.com/ubuntu/|http://mirrors.aliyun.com/ubuntu/|g' /etc/apt/sources.list && \
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
        && rm -rf /var/lib/apt/lists/*

# 复制WAR文件到容器
COPY build/libs/demo-0.0.1-SNAPSHOT.war app.war

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
