package com.smartwash.dining.stream;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 点单 SSE 多实例广播配置：注册 Redis 消息监听容器，订阅 {@link DiningOrderStreamer#CHANGE_CHANNEL}。
 * <p>仅非 test profile 装配——测试环境 Redis 为 mock（{@code application-test.yaml} 惰性建连），
 * 不引入真实中间件依赖；SSE 的本机推送路径在单测/集成测试中仍可直接覆盖。
 */
@Configuration
@Profile("!test")
public class DiningStreamConfig {

    @Bean
    public RedisMessageListenerContainer diningOrderChangeContainer(
            RedisConnectionFactory connectionFactory,
            DiningOrderChangeRedisListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new ChannelTopic(DiningOrderStreamer.CHANGE_CHANNEL));
        return container;
    }
}
