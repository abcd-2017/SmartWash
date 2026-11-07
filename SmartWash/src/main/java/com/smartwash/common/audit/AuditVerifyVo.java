package com.smartwash.common.audit;

import lombok.Data;

/**
 * 审计链校验结果（GET /admin/audit-logs/verify 响应体）。
 */
@Data
public class AuditVerifyVo {

    /** 全部抽查条目是否完整（内容 hash 与链接关系均一致） */
    private Boolean valid;

    /** 实际校验条数 */
    private Integer checkedCount;

    /** 首个断裂条目 id（被篡改或前驱缺失）；无断裂为 null */
    private Long brokenId;

    /** 人类可读校验说明 */
    private String message;
}
