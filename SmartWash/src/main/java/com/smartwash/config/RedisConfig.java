package com.smartwash.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * 缓存 value 序列化器：Jackson + 多态白名单（见 {@link CacheJsonSerializer}），
     * 替代原 FastJSON2 WriteClassName 序列化。
     */
    private static final CacheJsonSerializer CACHE_SERIALIZER = new CacheJsonSerializer();

    @Bean
    public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        log.info("Redis 连接工厂初始化成功");
        RedisTemplate<Object, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(CACHE_SERIALIZER))
                .computePrefixWith(cacheName -> "smartwash:" + cacheName + "::");

        // 不同缓存空间的 TTL 配置
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("schools", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("laundryItems", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("coupon", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put("dashboard", defaultConfig.entryTtl(Duration.ofMinutes(2)));
        // 工作台配置类字典：变更频率低，管理端写操作统一 @CacheEvict 失效
        cacheConfigs.put("workbenchEntries", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("deeplinkRoutes", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        // 游戏中心下发列表：上传/编辑/开关/删除均 @CacheEvict 全量失效
        cacheConfigs.put("gameList", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        log.info("RedisCacheManager 初始化成功");
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
