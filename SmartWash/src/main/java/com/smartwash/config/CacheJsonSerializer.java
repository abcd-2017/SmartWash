package com.smartwash.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.io.IOException;

/**
 * Redis 缓存 value 序列化器（Jackson + 多态白名单）。
 *
 * <p>等价原 FastJSON2 {@code WriteClassName} 的带类型序列化：缓存值类型信息随 JSON 一起落库，
 * 反序列化时还原为原始类型（如 {@code List<SchoolNameVo>}），避免服务侧取缓存后强转失败。
 *
 * <p>实现要点：Jackson 默认多态在「根节点是数组/集合」时不会包装根类型，导致按 {@code Object.class}
 * 回读失败。因此根节点统一包装为 {@link CacheEnvelope}（POJO），由信封的 {@code value} 字段承载
 * 带类型信息的原始缓存值，规避该不对称。
 *
 * <p>安全边界：反序列化多态类型仅放行
 * <ul>
 *   <li>{@code com.smartwash.} —— 项目业务对象（含本序列化器的信封类型）；</li>
 *   <li>{@code java.util. / java.lang. / java.math. / java.time.} —— JDK 基础容器与值类型。</li>
 * </ul>
 * 白名单外类型直接拒绝，防止缓存反序列化漏洞（原 FastJSON2 autoType 白名单仅放行 com.smartwash.）。
 */
public final class CacheJsonSerializer implements RedisSerializer<Object> {

    private static final String ALLOWED_PACKAGE = "com.smartwash.";

    private final ObjectMapper mapper;

    public CacheJsonSerializer() {
        this(createMapper());
    }

    public CacheJsonSerializer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType(ALLOWED_PACKAGE)
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.lang.")
                        .allowIfSubType("java.math.")
                        .allowIfSubType("java.time.")
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }

    /**
     * 暴露底层 {@link ObjectMapper}，供序列化格式回归测试使用。
     */
    public ObjectMapper getMapper() {
        return mapper;
    }

    @Override
    public byte[] serialize(Object object) throws SerializationException {
        if (object == null) {
            return new byte[0];
        }
        try {
            return mapper.writeValueAsBytes(new CacheEnvelope(object));
        } catch (JsonProcessingException e) {
            throw new SerializationException("Redis 缓存序列化失败", e);
        }
    }

    @Override
    public Object deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return mapper.readValue(bytes, CacheEnvelope.class).getValue();
        } catch (IOException e) {
            throw new SerializationException("Redis 缓存反序列化失败", e);
        }
    }

    /**
     * 缓存值信封：根节点固定为 POJO，使 Jackson 默认多态能正确回读；
     * {@code value} 声明为 Object，其运行时类型会写入类型信息。
     */
    public static final class CacheEnvelope {

        private Object value;

        public CacheEnvelope() {
        }

        public CacheEnvelope(Object value) {
            this.value = value;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }
    }
}
