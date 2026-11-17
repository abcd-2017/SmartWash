# 吃啥菜谱库导入材料（本目录不入库，含无 LICENSE 内容）

生成时间：2026-11-17 10:59
来源：CookLikeHOC（无 LICENSE，仅本地使用）× HowToCook（Public Domain）

## 产物

| 文件 | 说明 |
|---|---|
| cooklikehoc.csv | 340 行（15 分类 + 酱料），营养表口径 |
| howtocook.csv | 357 行（10 分类），整份热量口径 |
| ingredients.csv | 1260 个配料（核对用，导入自动注册为主） |
| migration.sql | 幂等迁移脚本（配料两表 + 菜谱营养 6 列） |
| covers/ | 393 张封面图（CookLikeHOC 原名 / HowToCook htc_ 前缀） |

## 导入步骤

1. **先跑迁移**：`mysql -u<user> -p <库名> < static/dining/migration.sql`
2. **Web 后台 → 吃啥管理 → 菜谱导入**：上传 cooklikehoc.csv → 预览核对 → 确认导入（≤500 行/文件，两个文件分别导入）
3. 同法导入 howtocook.csv
4. **封面**：本目录 covers/ 的图已由脚本直传 MinIO（若上传脚本已执行）；否则在 Web 后台菜谱编辑中逐张上传（对象键规则 `dining/covers/<文件名>`）
5. **校验**：菜谱库抽查营养徽标与封面；发起一单试算买菜清单聚合与人均卡路里

## 口径说明

- 一份热量 = 整份热量（HowToCook）?: 热量每100g × 250g（份量假设）
- 买菜清单按（配料名, 归一单位）聚合求和；无用量配料仅按名去重
