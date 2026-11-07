package com.smartwash.common.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理端敏感操作审计注解。
 *
 * <p>标注在管理端 Controller 写操作方法上，由 {@link AuditAspect} 在方法<b>成功返回后</b>
 * 异步落一条审计日志（{@code admin_audit_log} 表，HMAC 链防篡改）。纯观察不拦截：
 * 注解只追加留痕，不改任何既有业务逻辑与返回结构；审计链路任何异常均被吞掉并 warn，
 * 绝不影响业务返回。
 *
 * <p>SpEL 上下文支持：方法参数（按形参名，编译开启 -parameters，另有 #p0/#p1 别名）
 * 与返回值 {@code #result}（如 {@code #result.data}）。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** 动作标识，如 LAUNDRY_ITEM_UPDATE / COUPON_DELETE / PAYMENT_MANUAL_ADD */
    String action();

    /** 目标对象类型，如 laundry_item / coupon / user；空串表示无固定目标类型 */
    String targetType() default "";

    /**
     * 目标 id 的 SpEL 表达式（相对方法入参与返回值），如 {@code "#usersFrom.userId"}、
     * {@code "#ids"}、{@code "#result.data"}；解析失败降级为空，不抛异常。
     */
    String targetIdSpEL() default "";

    /** 是否记录 before/after JSON 摘要（入参摘要/返回值摘要，敏感字段掩码、超 2KB 截断） */
    boolean recordPayload() default true;
}
