package com.smartwash.common.audit;

import com.smartwash.common.Result;
import com.smartwash.common.ResultCodeEnum;
import com.smartwash.toolbox.toolkit.ClientIpUtil;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 管理端审计切面：拦截标注 {@link Audited} 的方法，在<b>成功返回后</b>异步落审计日志。
 *
 * <p><b>纯观察不拦截</b>：不改入参、不改返回值、不改变既有业务逻辑；审计链路（SpEL 解析、
 * 摘要构建、入队）全程 try-catch 包裹，任何异常只 warn，绝不影响业务返回。
 * 记录时机用 @AfterReturning：方法抛异常即视为操作未生效，不记审计。
 *
 * <p><b>成败判定</b>：返回 {@link Result} 时以 {@code code == 200} 为成功（业务语义看信封 code），
 * failMsg 等失败分支不产生审计；非 Result 返回值（含 void）一律记录。
 *
 * <p><b>摘要语义</b>：before_json = 入参 JSON 摘要（脱敏+截断），after_json = 返回值 JSON 摘要。
 * Controller 层拿不到改动前库内快照，故 before 记录「操作提交的内容」，配合目标表自身变更可追溯。
 *
 * <p><b>操作人/IP</b>：操作人取 {@link SecurityUtil}（LoginUser.username，匿名兜底 unknown）；
 * IP 复用 {@link ClientIpUtil}（与工具箱点击统计同一可信代理口径：仅直连为回环/内网时才采信
 * X-Forwarded-For/X-Real-IP，防公网伪造头部污染审计归属）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private static final SpelExpressionParser SPEL_PARSER = new SpelExpressionParser();

    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private static final String ANONYMOUS_OPERATOR = "unknown";

    private final IAdminAuditLogService auditLogService;

    private final SecurityUtil securityUtil;

    private final AuditPayloadBuilder payloadBuilder;

    /**
     * 成功返回后异步记录。整个方法体 try-catch 全包：审计侧任何异常（SpEL、JSON、入队拒绝）
     * 均只 warn，不向业务传播。
     */
    @AfterReturning(pointcut = "@annotation(audited)", returning = "result")
    public void recordSuccess(JoinPoint joinPoint, Audited audited, Object result) {
        try {
            // 业务失败（信封 code != 200）不视为有效操作，不记审计
            if (result instanceof Result<?> envelope
                    && !ResultCodeEnum.SUCCESS.getCode().equals(envelope.getCode())) {
                return;
            }
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Object[] args = joinPoint.getArgs();

            AdminAuditLog entry = new AdminAuditLog();
            entry.setAction(audited.action());
            entry.setTargetType(audited.targetType());
            entry.setAdminUser(currentAdminUsername());
            entry.setTargetId(resolveTargetId(method, args, result, audited.targetIdSpEL()));
            entry.setCreatedAt(LocalDateTime.now().withNano(0)); // 秒级，参与规范化 payload，入库后可复算
            if (audited.recordPayload()) {
                entry.setBeforeJson(payloadBuilder.buildArgsJson(args));
                entry.setAfterJson(payloadBuilder.buildResultJson(result));
            }
            entry.setIp(currentClientIp());

            auditLogService.recordAsync(entry);
        } catch (Throwable t) {
            log.warn("审计记录构建失败(不影响业务), action={}, targetType={}", audited.action(), audited.targetType(), t);
        }
    }

    /**
     * 解析目标 id SpEL；空表达式返回 null，解析失败吞异常降级为 null。
     * 包级可见供单测直接验证。
     */
    String resolveTargetId(Method method, Object[] args, Object result, String spel) {
        if (spel == null || spel.isBlank()) {
            return null;
        }
        try {
            MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                    this, method, args, PARAMETER_NAME_DISCOVERER);
            // 兜底注册 #pN 别名：参数名经 -parameters 编译可用，但显式别名更稳
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    context.setVariable("p" + i, args[i]);
                }
            }
            context.setVariable("result", result);
            Expression expression = SPEL_PARSER.parseExpression(spel);
            Object value = expression.getValue(context);
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            log.warn("审计 targetId SpEL 解析失败, spel={}, method={}", spel, method.getName(), e);
            return null;
        }
    }

    /** 当前登录管理员用户名；匿名/非 LoginUser 主体安全退化 */
    private String currentAdminUsername() {
        try {
            var principal = securityUtil.getCurrentUser();
            if (principal instanceof LoginUser loginUser) {
                return loginUser.getUsername();
            }
            if (principal != null && principal.getUsername() != null) {
                return principal.getUsername();
            }
        } catch (Exception e) {
            log.warn("审计提取操作人失败，记为 unknown", e);
        }
        return ANONYMOUS_OPERATOR;
    }

    /** 当前请求客户端 IP（与工具箱点击统计同口径）；无请求上下文（如内部调用）返回 null */
    private String currentClientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return ClientIpUtil.get(attributes.getRequest());
        }
        return null;
    }
}
