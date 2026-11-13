package com.smartwash.dining.service;

import com.smartwash.dining.from.DiningRecipeImportConfirmFrom;
import com.smartwash.dining.vo.DiningRecipeImportPreviewVo;
import com.smartwash.dining.vo.DiningRecipeImportResultVo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 菜谱批量导入服务（03 号票）：两阶段导入的解析校验与整批事务写入。
 *
 * <p>阶段一 {@link #preview}：解析上传的 xlsx/CSV 模板文件并逐行校验（缺列整体拒绝、
 * 菜名/分类/食材/步骤/封面链接给行号级错误），<b>不落库</b>，返回归一后的行数据供预览确认。</p>
 *
 * <p>阶段二 {@link #confirm}：整批事务——再次逐行校验（防绕过预览直接提交），
 * 任一行失败则全批不落库并返回逐行失败报告；全部通过才逐行插入并返回逐行成功报告
 * （事务语义对齐游戏模块关卡批量导入先例，杜绝半批状态）。</p>
 *
 * <p>{@link #buildTemplate} 生成带填写说明与示例行的 xlsx 模板（接口动态生成而非静态文件：
 * 模板列约束与代码校验规则同源演进，不依赖 jar 内资源路径维护）。</p>
 */
public interface DiningRecipeImportService {

    /** 生成导入模板 xlsx 字节（首个 sheet 为表头+示例行，第二个 sheet 为填写说明） */
    byte[] buildTemplate();

    /**
     * 阶段一：解析上传文件（.xlsx / .csv，≤5MB、≤500 数据行）并逐行校验，不落库。
     * 表头缺列、空文件、格式不支持、超行数上限等整体性问题抛业务异常；
     * 逐行问题（菜名空/超长、分类不存在、食材超限、步骤超长、封面链接非法等）记入行级 errors。
     */
    DiningRecipeImportPreviewVo preview(MultipartFile file);

    /**
     * 阶段二：确认导入（整批事务）。再次逐行校验，任一行失败全批不落库
     * （rolledBack=true + 逐行失败报告）；全部通过才逐行写入（source=ADMIN、默认上架、
     * 记录创建管理员）并返回逐行成功报告。
     */
    DiningRecipeImportResultVo confirm(DiningRecipeImportConfirmFrom from);
}
