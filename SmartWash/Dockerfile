# ============================================================
# SmartWash Backend Docker Image
# 构建：docker build -t smartwash-backend .
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 先复制 pom，利用 Docker 缓存层（依赖不变时不重新下载）
COPY pom.xml .
RUN mvn -B dependency:go-offline

# 再复制源码并打包
COPY src ./src
RUN mvn -B package -DskipTests

# ------------------------------------------------------------
# 运行阶段：JRE 镜像，更小更安全
# ------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 非 root 运行
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 从构建阶段复制可执行 JAR
COPY --from=builder /build/target/*.jar app.jar

# Spring Boot 默认端口
EXPOSE 9000

USER appuser

# 生产配置通过 SPRING_PROFILES_ACTIVE 或外部挂载 application.yml 注入
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
