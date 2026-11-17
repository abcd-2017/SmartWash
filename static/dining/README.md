# 吃啥菜谱库导入材料（非商用项目使用；CookLikeHOC 部分无 LICENSE，禁止任何商业用途）

生成时间：2026-11-18 01:43
来源：CookLikeHOC（无 LICENSE，仅本地使用）× HowToCook（Public Domain）

## 产物

| 文件 | 说明 |
|---|---|
| cooklikehoc.csv | 340 行（15 分类 + 酱料），营养表口径，含「唯一标识」列（hoc_ 前缀） |
| howtocook.csv | 357 行（10 分类），整份热量口径，含「唯一标识」列（htc_ 前缀） |
| ingredients.csv | 1260 个配料（核对用，导入自动注册为主） |
| covers/ | 409 张封面图（CookLikeHOC 原名 / HowToCook htc_ 前缀） |

## 导入步骤

1. **迁移脚本人工维护**（转换器不再生成）：`static/dining/migration.sql`，结构变更后手工执行
2. **Web 后台 → 吃啥管理**：文件导入上传 CSV，或目录导入选本目录（csv + image/）；唯一标识命中 = 幂等更新，重复导入不堆重复菜
3. **封面**：本目录 covers/ 的图已由脚本直传 MinIO（若上传脚本已执行）；否则在 Web 后台菜谱编辑中逐张上传（对象键规则 `dining/covers/<文件名>`）
4. **校验**：菜谱库抽查营养徽标与封面；发起一单试算买菜清单聚合与人均卡路里

## 口径说明

- 一份热量 = 整份热量（HowToCook）?: 热量每100g × 250g（份量假设）
- 买菜清单按（配料名, 归一单位）聚合求和；无用量配料仅按名去重
- 唯一标识（source_key）：hoc_/htc_ 前缀 + 菜名，重复导入按它判重（优先）；无该列的旧 CSV 按（菜名, 分类）兜底
