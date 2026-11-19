package com.smartwash.dining.service;

import com.smartwash.dining.from.DiningRecipeImportConfirmFrom;
import com.smartwash.dining.vo.DiningRecipeImportPreviewVo;
import com.smartwash.dining.vo.DiningRecipeImportResultVo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 菜谱批量导入服务（03 号票 + 契约 §3.2 v2 扩展）：两阶段导入的解析校验与整批事务写入。
 *
 * <p>阶段一 {@link #preview}：解析上传的 xlsx/CSV 模板文件并逐行校验（缺必选列整体拒绝、
 * 菜名/分类/食材/明细/步骤/封面给行号级错误），<b>不落库</b>，返回归一后的行数据供预览确认；
 * 不阻断的行级问题（分类自动创建/封面对象键存储缺失/明细解析失败）记入行级 warnings。</p>
 *
 * <p>阶段二 {@link #confirm}：整批事务——再次逐行校验（防绕过预览直接提交），
 * 任一行失败则全批不落库并返回逐行失败报告；全部通过才写入：分类自动创建（sort 递增）、
 * 配料按名 upsert + 写菜谱配料关联（聚合主数据源）、营养 6 列写入菜谱，
 * 并返回逐行成功报告（事务语义对齐游戏模块关卡批量导入先例，杜绝半批状态）。</p>
 *
 * <p>列契约：既有 5 列（分类名/菜名/食材名列表/步骤/封面链接）必选，v2 新列
 * （食材明细列表/封面对象键/热量每100g/整份热量/蛋白质g/脂肪g/碳水g/钠mg）全部可选，
 * 旧模板/旧 CSV 零改动仍可导入。步骤列可空（酱料配方/瓶装饮品无做法，仅校验长度上限）。</p>
 *
 * <p>幂等判重（source_key 唯一标识）：判重顺序 = 行 source_key 非空按 key 查 →（菜名,分类）兜底查；
 * 命中即更新既有菜谱（保留 id/created_at/status/source/创建人，不静默改上下架），未命中新增。
 * 存量未标识菜谱靠重导按兜底键命中回填 source_key。同批内重复 source_key 为行级错误（全批回滚）。</p>
 *
 * <p>{@link #buildTemplate} 生成带填写说明与示例行的 xlsx 模板（接口动态生成而非静态文件：
 * 模板列约束与代码校验规则同源演进，不依赖 jar 内资源路径维护）。</p>
 */
public interface DiningRecipeImportService {

    /** 判重结果类型：新增（未命中库内已有菜谱） */
    String EXISTS_KIND_CREATE = "CREATE";

    /** 判重结果类型：更新（source_key/（菜名,分类）命中库内已有菜谱，幂等覆盖刷新） */
    String EXISTS_KIND_UPDATE = "UPDATE";

    /** 生成导入模板 xlsx 字节（首个 sheet 为表头+示例行，第二个 sheet 为填写说明） */
    byte[] buildTemplate();

    /**
     * 阶段一：解析上传文件（.xlsx / .csv，≤5MB、≤1000 数据行）并逐行校验，不落库。
     * 表头缺必选列、空文件、格式不支持、超行数上限等整体性问题抛业务异常；
     * 逐行问题（菜名空/超长、食材/明细超限、步骤超长、封面格式非法、营养数值非法等）记入行级 errors。
     */
    DiningRecipeImportPreviewVo preview(MultipartFile file);

    /**
     * 阶段二：确认导入（整批事务）。再次逐行校验，任一行失败全批不落库
     * （rolledBack=true + 逐行失败报告）；全部通过才写入（分类自动创建、配料 upsert +
     * 关联表、营养列、封面对象键存储校验；source=ADMIN、默认上架、记录创建管理员）
     * 并返回逐行成功报告。命中库内已有菜谱（source_key 优先 →（菜名,分类）兜底）按
     * 幂等更新处理：刷新内容列、重建配料关联，保留 id/created_at/status/来源与创建人。
     */
    DiningRecipeImportResultVo confirm(DiningRecipeImportConfirmFrom from);
}
