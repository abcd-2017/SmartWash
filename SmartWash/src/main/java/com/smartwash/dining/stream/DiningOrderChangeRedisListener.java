package com.smartwash.dining.stream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis 广播监听：收到「订单变更」消息后回调 {@link DiningOrderStreamer#notifyLocal(Long)} 推本机订阅者。
 * 多实例部署下，任一实例上的写操作经 {@link DiningOrderStreamer#publish(Long)} 广播，其余实例据此同步。
 * <p>仅非 test profile 装配（测试环境 Redis 为 mock，避免连真实中间件）。
 */
@Slf4j
@Component
@Profile("!test")
public class DiningOrderChangeRedisListener implements MessageListener {

    private final DiningOrderStreamer streamer;

    public DiningOrderChangeRedisListener(DiningOrderStreamer streamer) {
        this.streamer = streamer;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8).trim();
        try {
            streamer.notifyLocal(Long.parseLong(body));
        } catch (NumberFormatException e) {
            log.warn("点单变更广播消息非法，已忽略: {}", body);
        }
    }
}
