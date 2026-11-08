package com.smartwash.common.audit;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端审计日志控制器。
 * 路径前缀 /admin/audit-logs，需 ROLE_ADMIN（SecurityConfig 统一拦截）。
 * 四端联动说明：本组为新增接口，消费方为 Web 管理后台（审计日志页），Android/鸿蒙不消费。
 */
@Tag(name = "管理端审计日志", description = "敏感操作审计查询与 HMAC 链防篡改校验（ROLE_ADMIN）")
@Slf4j
@RestController
@RequestMapping("/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final IAdminAuditLogService auditLogService;

    @Operation(summary = "分页查询审计日志", description = "筛选：操作人/动作/目标类型/目标id/时间区间，按 id 倒序")
    @GetMapping
    public Result<Page<AuditLogVo>> page(SearchAuditLogFrom from) {
        return Result.ok(auditLogService.pageAuditLogs(from));
    }

    @Operation(summary = "校验审计链完整性", description = "抽查最近 N 条（默认 500，上限 5000）：逐条复算 HMAC 并检查链衔接，返回首个断裂点；删掉窗口末尾条目无法检出")
    @GetMapping("/verify")
    public Result<AuditVerifyVo> verify(@RequestParam(name = "limit", required = false, defaultValue = "500")
                                        @Parameter(description = "抽查最近 N 条，上限 5000") Integer limit) {
        return Result.ok(auditLogService.verifyChain(limit));
    }
}
