package com.smartwash.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONFactory;
import com.alibaba.fastjson2.JSONWriter;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@EnableCaching
public class RedisConfig {

    static {
        // 限制 FastJSON2 反序列化自动类型识别仅允许项目内部类，防止反序列化漏洞
        JSONFactory.getDefaultObjectReaderProvider().addAutoTypeAccept("com.smartwash.");
    }

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
        // 使用 FastJSON2 作为 value 序列化器
        RedisSerializer<Object> fastJson2Serializer = new RedisSerializer<Object>() {
            @Override
            public byte[] serialize(Object object) {
                if (object == null) return new byte[0];
                return JSON.toJSONString(object, JSONWriter.Feature.WriteClassName).getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public Object deserialize(byte[] bytes) {
                if (bytes == null || bytes.length == 0) return null;
                return JSON.parseObject(new String(bytes, StandardCharsets.UTF_8), Object.class);
            }
        };

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(fastJson2Serializer))
                .computePrefixWith(cacheName -> "smartwash:" + cacheName + "::");

        // 不同缓存空间的 TTL 配置
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("schools", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("laundryItems", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("coupon", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put("dashboard", defaultConfig.entryTtl(Duration.ofMinutes(2)));

        log.info("RedisCacheManager 初始化成功");
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
