package com.smartwash.toolbox.service;

import com.smartwash.toolbox.vo.LockerTagBatchItemVo;
import com.smartwash.toolbox.vo.LockerTagVo;
import com.smartwash.toolbox.vo.ShortCodeVo;

import java.util.List;

/**
 * 储物柜资产码（一物一码 M3）服务。
 *
 * <p>柜码 = 短码内核活码（content_type=2）的首次落地消费：每个柜子一张二维码标签，
 * 扫码经 {@code /web/t/{code}} 302 至 Web 公开落地页 {@code /l/{lockerId}}；
 * 跳转目标可重定向（活码语义——落地页演进无需重贴柜上标签）。</p>
 *
 * <p>幂等约定：柜码别名固定为 {@code L{lockerId}}，由 uk_code 唯一索引裁决，
 * 重复生成返回既有活码（不重复建行）；活码 owner 记 0（系统生成，管理端治理）。</p>
 */
public interface ToolboxLockerTagService {

    /** 管理端：为单个柜子生成资产码（活码；已存在则幂等返回） */
    ShortCodeVo generateForLocker(Long lockerId);

    /** 管理端：按校批量生成（逐柜幂等），返回逐柜结果供打印排版 */
    List<LockerTagBatchItemVo> generateForSchool(Long schoolId);

    /** 公开：扫码解析 → 柜子信息卡（校验活码存在、类型与绑定一致；不存在/不匹配一律 404） */
    LockerTagVo resolve(String code);
}
