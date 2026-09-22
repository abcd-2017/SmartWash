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
# 运行阶段：distroless JRE，最小化镜像体积
# gcr.io/distroless/java17-debian12 约 120MB，比 eclipse-temurin:17-jre-alpine 小 ~60MB
# ------------------------------------------------------------
FROM gcr.io/distroless/java17-debian12

WORKDIR /app

# 从构建阶段复制可执行 JAR（distroless 已内置非 root 用户 nonroot:65532）
COPY --from=builder /build/target/*.jar app.jar

# Spring Boot 默认端口
EXPOSE 9000

# distroless 镜像没有 shell，USER 指令使用数字 UID
USER nonroot

# 生产配置通过 SPRING_PROFILES_ACTIVE 或外部挂载 application.yml 注入
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
