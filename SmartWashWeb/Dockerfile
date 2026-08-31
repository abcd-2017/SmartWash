# ============================================================
# SmartWash Web Admin Docker Image
# 构建：docker build -t smartwash-web .
# ============================================================
FROM node:20-alpine AS builder

WORKDIR /app

# 安装依赖
COPY package.json package-lock.json* ./
RUN npm ci

# 复制源码并构建
COPY . .
RUN npm run build

# ------------------------------------------------------------
# 运行阶段：Nginx 静态托管 + 反代
# ------------------------------------------------------------
FROM nginx:1.27-alpine

# 自定义 Nginx 配置（SPA 路由 + API 反代）
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 从构建阶段复制构建产物
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
