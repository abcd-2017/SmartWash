# SmartWash 校园智能洗衣系统

校园智能洗衣一站式服务平台，覆盖用户端（Android / 鸿蒙）、管理后台（Web）和后端服务。

## 项目架构

```
SmartWash/
├── SmartWash/              # Spring Boot 后端服务
├── SmartWash-Android/      # Android 用户端（Jetpack Compose）
├── SmartWash_Harmony/      # 鸿蒙 NEXT 用户端（ArkTS）
├── SmartWashWeb/           # Vue 3 Web 管理后台
└── smart_wash.sql          # MySQL 数据库初始化脚本
```

## 技术栈

| 模块 | 技术 |
|------|------|
| 后端 | Spring Boot 3.4, Spring Security, MyBatis-Plus 3.5, MySQL 8, Redis, JWT |
| Android | Kotlin, Jetpack Compose, Retrofit, Hilt, Room, DataStore, Paging 3, Material 3 |
| 鸿蒙 | ArkTS, ArkUI, Stage 模型, API 5.0.5(17) |
| Web 后台 | Vue 3, Vite, Element Plus, Pinia, Vue Router, Axios |

## 功能模块

### 用户端（Android / 鸿蒙）
- 用户注册/登录
- 洗衣套餐选购与下单
- 订单状态追踪（待寄件 → 已收取 → 清洗中 → 已烘干 → 配送中 → 待取件 → 已完成）
- 优惠券领取与使用
- 钱包余额充值与支付
- 储物柜选择

### 管理后台（Web）
- 用户管理
- 学校与储物柜管理
- 洗衣项目/套餐管理
- 订单管理
- 支付记录
- 优惠券模板管理
- 用户优惠券管理
- 充值记录
- 角色权限管理（超级管理员 / 学校管理员 / 厂房管理员）
- 后台用户管理

## 后端分层架构

```
controller/
├── web/          # 移动端 API 接口
└── background/   # 后台管理 API 接口
service/          # 业务逻辑层
mapper/           # MyBatis-Plus 数据访问层
entity/           # 数据库实体
from/             # 请求表单对象（DTO）
vo/               # 视图响应对象（VO）
config/           # Spring 配置（Security, CORS, Redis, MyBatis, JWT）
filter/           # 过滤器（JWT 认证等）
common/           # 公共类
utils/            # 工具类
exception/        # 异常定义
```

## 数据库表结构

| 表名 | 说明 |
|------|------|
| `users` | 用户表 |
| `admin_users` | 后台管理员 |
| `roles` | 角色表（root / schools_admin / plant） |
| `schools` | 学校信息 |
| `lockers` | 储物柜 |
| `laundry_items` | 洗衣套餐 |
| `orders` | 订单 |
| `payments` | 支付记录 |
| `recharge_records` | 充值记录 |
| `coupon` | 优惠券模板 |
| `user_coupon` | 用户领取优惠券记录 |

## 快速开始

### 1. 数据库初始化

```bash
mysql -u root -p < smart_wash.sql
```

### 2. 启动后端

```bash
cd SmartWash
# 修改 src/main/resources/application.yml 中的数据库和 Redis 连接配置
mvn spring-boot:run
```

### 3. 启动 Web 管理后台

```bash
cd SmartWashWeb
npm install
npm run dev
```

### 4. 启动 Android 客户端

用 Android Studio 打开 `SmartWash-Android` 目录，同步 Gradle 后运行。

### 5. 启动鸿蒙客户端

用 DevEco Studio 打开 `SmartWash_Harmony` 目录，同步后运行到鸿蒙设备或模拟器。

## 默认管理员账号

| 用户名 | 角色 |
|--------|------|
| root | 超级管理员 |
| admin | 学校管理员 |