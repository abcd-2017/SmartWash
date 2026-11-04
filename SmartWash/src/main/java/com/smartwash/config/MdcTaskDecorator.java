package com.smartwash.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MDC 上下文装饰器：把提交任务线程的 MDC（主要是 TraceIdFilter 写入的 traceId）复制到
 * 异步执行线程，任务结束后清理，避免线程复用导致的 traceId 串扰，实现链路日志跨线程传播。
 *
 * <p>生效方式：
 * <ul>
 *   <li><b>@Async 线程池</b>：应用已开启 @EnableAsync，Spring Boot 的
 *       ThreadPoolTaskExecutorBuilder 会自动应用容器中唯一的 TaskDecorator Bean 到
 *       applicationTaskExecutor，无需额外配置；</li>
 *   <li><b>自建线程池</b>（如 DivInterpretationServiceImpl 的固定线程池）：
 *       提交任务时调用 {@link #decorate(Runnable)} 手动包装。</li>
 * </ul>
 */
@Component
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // decorate 在提交任务的线程（持有时任 MDC）中执行，此处快照上下文
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            if (context != null) {
                MDC.setContextMap(context);
            }
            try {
                runnable.run();
            } finally {
                // 异步线程同样可能复用，结束后清理
                MDC.clear();
            }
        };
    }
}
