package com.smartwash.common.audit;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 管理端审计日志服务。
 */
public interface IAdminAuditLogService extends IService<AdminAuditLog> {

    /**
     * 异步落一条审计日志（HMAC 链串行计算，绝不阻塞/抛出到业务线程）。
     * 入参 entry 已由切面填充业务字段（adminUser/action/target/payload/ip/createdAt），
     * prev_hash 与 hash 由本方法在单线程链写任务内计算。
     */
    void recordAsync(AdminAuditLog entry);

    /**
     * 分页筛选审计日志（操作人/动作/目标类型/目标 id/时间区间）。
     */
    Page<AuditLogVo> pageAuditLogs(SearchAuditLogFrom from);

    /**
     * 校验最近 limit 条（按 id 倒序取窗、正序校验）审计链是否被篡改。
     */
    AuditVerifyVo verifyChain(Integer limit);
}
