package com.smartwash.dining.stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 点单 SSE 推送链路单测：发布广播、空订阅兜底、Redis 消息解析与非法体防御。
 * （emitter 帧级行为依赖 Servlet 异步环境，交由模拟器真机链路验证，见方案 §九）
 */
@DisplayName("点单 SSE 推送（streamer/Redis 监听）")
class DiningOrderStreamTest {

    @Test
    @DisplayName("publish：向 Redis 频道广播 orderId 字符串（本机订阅由 notifyLocal 并行处理）")
    void publish_broadcasts_to_redis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DiningOrderStreamer streamer = new DiningOrderStreamer(redis);

        streamer.publish(900L);

        verify(redis).convertAndSend(DiningOrderStreamer.CHANGE_CHANNEL, "900");
    }

    @Test
    @DisplayName("notifyLocal：无订阅者时静默返回（不抛异常、不触碰 Redis）")
    void notifyLocal_without_subscribers_is_noop() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DiningOrderStreamer streamer = new DiningOrderStreamer(redis);

        streamer.notifyLocal(900L);

        verify(redis, org.mockito.Mockito.never())
                .convertAndSend(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("Redis 监听器：解析 orderId 回调本地推送；非法消息体忽略不抛")
    void listener_parses_and_ignores_bad_body() {
        DiningOrderStreamer streamer = mock(DiningOrderStreamer.class);
        DiningOrderChangeRedisListener listener = new DiningOrderChangeRedisListener(streamer);

        Message message = mock(Message.class);
        when(message.getBody()).thenReturn("900".getBytes(StandardCharsets.UTF_8));
        listener.onMessage(message, null);
        verify(streamer).notifyLocal(900L);

        Message bad = mock(Message.class);
        when(bad.getBody()).thenReturn("abc".getBytes(StandardCharsets.UTF_8));
        listener.onMessage(bad, null);
    }
}
