package com.smartwash.dining.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 点单订单变更 SSE 推送注册中心。
 *
 * 职责：维护「orderId → 订阅者 emitter 集合」，向本机订阅者推 `changed` 信号；
 * 变更经 Redis pub/sub 广播到多实例（{@link DiningOrderChangeRedisListener} 收到后回调本类的
 * 「本地推送」）。信号只带 orderId——端上收到后仍走既有 {@code GET /{id}?knownVersion=} 回拉详情，
 * 从而复用轮询的差分/脱敏契约（join_token 不下发参与者，也不广播大 payload）。
 *
 * 生命周期：emitter 30min 超时 + 30s 心跳 comment 防代理断连；completion/timeout/error 均移除，防泄漏。
 * 订阅资格（成员校验）由调用方（Controller/Service）保证，本类不做业务判断。
 */
@Slf4j
@Component
public class DiningOrderStreamer {

    /** Redis 广播频道：消息体为 orderId 字符串 */
    public static final String CHANGE_CHANNEL = "dining:order:changed";

    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    private static final long HEARTBEAT_SECONDS = 30L;

    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "dining-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    private final StringRedisTemplate stringRedisTemplate;

    @Autowired
    public DiningOrderStreamer(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        heartbeat.scheduleAtFixedRate(this::heartbeat, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 订阅某订单变更：创建并注册 emitter（成员校验由调用方完成）。
     * 返回的 emitter 交给 Controller 作为 SSE 响应体。
     */
    public SseEmitter subscribe(Long orderId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.computeIfAbsent(orderId, key -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(orderId, emitter));
        emitter.onTimeout(() -> remove(orderId, emitter));
        emitter.onError(throwable -> remove(orderId, emitter));
        log.debug("SSE 订阅登记, orderId: {}, 当前订阅数: {}", orderId, emitters.get(orderId).size());
        return emitter;
    }

    /**
     * 变更发布：本机订阅者即时推送 + Redis 广播（多实例）。
     * 调用方应在事务提交后触发（见 DiningOrderServiceImpl#publishChanged）。
     */
    public void publish(Long orderId) {
        notifyLocal(orderId);
        try {
            stringRedisTemplate.convertAndSend(CHANGE_CHANNEL, String.valueOf(orderId));
        } catch (Exception e) {
            // Redis 不可用只影响跨实例广播，本机订阅与业务写入不受影响
            log.warn("点单变更 Redis 广播失败（仅影响多实例同步）, orderId: {}, 原因: {}", orderId, e.getMessage());
        }
    }

    /** 本地推送（供 Redis 监听器回调；只推信号，不组装详情） */
    public void notifyLocal(Long orderId) {
        Set<SseEmitter> set = emitters.get(orderId);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : Set.copyOf(set)) {
            try {
                emitter.send(SseEmitter.event()
                        .name("changed")
                        .data("{\"orderId\":" + orderId + "}", MediaType.TEXT_EVENT_STREAM));
            } catch (IOException | IllegalStateException e) {
                // 客户端已断开/连接已终态：移除即可（onCompletion/onError 亦会兜底）
                remove(orderId, emitter);
            }
        }
    }

    private void remove(Long orderId, SseEmitter emitter) {
        Set<SseEmitter> set = emitters.get(orderId);
        if (set == null) {
            return;
        }
        set.remove(emitter);
        if (set.isEmpty()) {
            emitters.remove(orderId, set);
        }
    }

    /** 心跳：仅对仍存活的连接发注释帧，规避反向代理的空闲断连（发送失败由 remove 清理） */
    private void heartbeat() {
        emitters.forEach((orderId, set) -> {
            for (SseEmitter emitter : Set.copyOf(set)) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException e) {
                    remove(orderId, emitter);
                }
            }
        });
    }
}
