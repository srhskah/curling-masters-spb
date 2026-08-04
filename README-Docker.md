# Docker 部署指南

本项目提供了完整的 Docker 部署方案，包括开发环境和生产环境的配置。

## 文件说明

- `Dockerfile` - 开发环境镜像（基于预构建的WAR文件）
- `Dockerfile.prod` - 生产环境镜像（多阶段构建，从源码构建）
- `docker-compose.yml` - 开发环境编排文件
- `docker-compose.prod.yml` - 生产环境编排文件
- `.dockerignore` - Docker构建忽略文件

## 快速开始

### 开发环境

1. **构建并启动服务**
   ```bash
   docker-compose up -d
   ```

2. **查看日志**
   ```bash
   docker-compose logs -f app
   ```

3. **停止服务**
   ```bash
   docker-compose down
   ```

### 生产环境

1. **构建并启动生产服务**
   ```bash
   docker-compose -f docker-compose.prod.yml up -d --build
   ```

2. **查看服务状态**
   ```bash
   docker-compose -f docker-compose.prod.yml ps
   ```

3. **查看日志**
   ```bash
   docker-compose -f docker-compose.prod.yml logs -f app
   ```

## 配置说明

### 环境变量

主要环境变量配置：

- `SPRING_DATASOURCE_URL` - 数据库连接地址
- `SPRING_DATASOURCE_USERNAME` - 数据库用户名
- `SPRING_DATASOURCE_PASSWORD` - 数据库密码
- `JAVA_OPTS` - JVM参数配置

### 端口映射

- `8080` - Spring Boot应用端口
- `3306` - MySQL数据库端口
- `6379` - Redis缓存端口
- `80/443` - Nginx代理端口（仅生产环境）

### 数据持久化

- `mysql_data` - MySQL数据卷
- `redis_data` - Redis数据卷
- `heapdump` - JVM堆转储文件卷（生产环境）

## 构建镜像

### 开发环境镜像

```bash
# 确保已构建WAR文件
./gradlew build

# 构建Docker镜像
docker build -t curling-masters:latest .
```

### 生产环境镜像

```bash
# 使用多阶段构建（从源码构建）
docker build -f Dockerfile.prod -t curling-masters:prod .
```

## 健康检查

所有服务都配置了健康检查：

- **应用服务**: 检查 `/actuator/health` 端点
- **MySQL**: 检查数据库连接
- **Redis**: 检查Redis服务响应

## 日志管理

### 开发环境

日志输出到容器标准输出，使用以下命令查看：

```bash
docker-compose logs -f app
```

### 生产环境

日志文件挂载到宿主机 `./logs` 目录：

```bash
# 应用日志
tail -f ./logs/app.log

# Nginx日志
tail -f ./logs/nginx/access.log
```

## 性能优化

### JVM参数

- **开发环境**: `-Xmx1g -Xms512m`
- **生产环境**: `-Xmx2g -Xms1g -XX:+HeapDumpOnOutOfMemoryError`

### 数据库优化

- 使用连接池配置
- 启用UTF-8字符集支持
- 配置健康检查

## 安全配置

- 使用非root用户运行应用
- 敏感信息通过环境变量传递
- 配置适当的网络隔离

## 故障排除

### 常见问题

1. **应用启动失败**
   ```bash
   # 检查日志
   docker-compose logs app
   
   # 检查数据库连接
   docker-compose exec mysql mysql -u root -p
   ```

2. **内存不足**
   ```bash
   # 调整JVM参数
   export JAVA_OPTS="-Xmx512m -Xms256m"
   docker-compose up -d
   ```

3. **数据库连接问题**
   ```bash
   # 检查数据库状态
   docker-compose exec mysql mysqladmin ping
   
   # 查看数据库日志
   docker-compose logs mysql
   ```

### 监控命令

```bash
# 查看容器资源使用
docker stats

# 查看容器详细信息
docker inspect <container_id>

# 进入容器调试
docker-compose exec app sh
```

## 备份与恢复

### 数据库备份

```bash
# 备份数据库
docker-compose exec mysql mysqldump -u root -p curling_masters > backup.sql

# 恢复数据库
docker-compose exec -T mysql mysql -u root -p curling_masters < backup.sql
```

### 数据卷备份

```bash
# 备份数据卷
docker run --rm -v curling-masters_mysql_data:/data -v $(pwd):/backup alpine tar czf /backup/mysql-backup.tar.gz -C /data .

# 恢复数据卷
docker run --rm -v curling-masters_mysql_data:/data -v $(pwd):/backup alpine tar xzf /backup/mysql-backup.tar.gz -C /data
```

## 更新部署

### 应用更新

```bash
# 构建新镜像
docker-compose build app

# 滚动更新
docker-compose up -d app
```

### 完整更新

```bash
# 停止服务
docker-compose down

# 更新代码
git pull

# 重新构建并启动
docker-compose up -d --build
```
