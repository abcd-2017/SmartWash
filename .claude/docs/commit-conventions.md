# Commit 规范

基于 [Conventional Commits 1.0.0](https://www.conventionalcommits.org/)，中文书写。

## 格式

**只写一行标题，默认不写 body。** 根因分析、修复方式、改动清单等细节写在代码注释或 PR 描述里，不进 commit message。

```
<type>(<scope>): <描述>
```

## type 取值

| type | 含义 |
|------|------|
| `feat` | 新功能 |
| `fix` | 修 bug |
| `refactor` | 重构（无新功能/无修 bug） |
| `perf` | 性能优化 |
| `docs` | 文档 |
| `test` | 测试 |
| `build` | 构建/依赖 |
| `ci` | CI 配置 |
| `style` | 格式（不影响逻辑） |
| `chore` | 杂项 |
| `revert` | 回滚 |

## scope

受影响端：`Backend` / `Android` / `Harmony` / `Web` / `SQL`；跨仓库级改动（如本规范文档）可省略。跨端改动拆分提交或在描述中列明。

## 描述规则

- **必须写解决了什么问题，不要写怎么解决问题**
  - ✅ `fix(Android): 修复点击设置页闪退`
  - ❌ `fix(Android): MainActivity 改用 hiltViewModel`
- **单行成文**：一句话讲清主要重点，禁止多行 body、禁止 bullet 清单
- 现在时祈使语气，<72 字符

## 破坏性变更

在 type/scope 后加 `!`，或在 footer 写 `BREAKING CHANGE: 描述`：

```
feat(api)!: 移除旧接口
```

## 示例

```
feat(order): 新增订单取消功能

fix(payment): 修复充值金额显示异常

docs(Android): 补充 Hilt ViewModel 误用约束

refactor(backend): 拆分 OrderService 为独立 UseCase
```
