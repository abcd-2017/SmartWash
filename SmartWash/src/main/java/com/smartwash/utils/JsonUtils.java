package com.smartwash.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Map;

/**
 * 项目统一 JSON 工具（Jackson）。
 *
 * <p>替代历史 FastJSON 2 用法：序列化/反序列化统一走 Jackson，避免两套 JSON 库并存导致
 * 响应结构、日期格式、null 处理不一致。配置与 Spring MVC 默认 Jackson 行为对齐：
 * <ul>
 *   <li>注册 {@link JavaTimeModule} 并关闭时间戳输出，日期序列化为 ISO-8601 字符串；</li>
 *   <li>忽略未知字段，避免端上加字段导致反序列化失败；</li>
 *   <li>保留 null 字段（与全局异常处理器/响应信封输出一致）。</li>
 * </ul>
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = createMapper();

    private JsonUtils() {
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * 暴露底层 {@link ObjectMapper}，供缓存序列化器等需要复用配置的场景使用；
     * 调用方不得修改其配置。
     */
    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    /**
     * 对象序列化为 JSON 字符串（保留 null 字段）。
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    /**
     * JSON 字符串反序列化为指定类型。
     */
    public static <T> T parseObject(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 反序列化失败: " + clazz.getName(), e);
        }
    }

    /**
     * JSON 字符串反序列化为带泛型的类型（如 {@code List<Float>}）。
     */
    public static <T> T parseObject(String json, TypeReference<T> typeReference) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 反序列化失败", e);
        }
    }

    /**
     * JSON 字符串解析为 {@code Map<String, Object>}（未知结构 JSON 的通用读法）。
     */
    public static Map<String, Object> parseMap(String json) {
        return parseObject(json, new TypeReference<Map<String, Object>>() {
        });
    }

    /**
     * JSON 字符串解析为 {@link JsonNode} 树（用于字段级读取与结构判定）。
     */
    public static JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 解析失败", e);
        }
    }

    /**
     * 创建空对象节点（用于手工构造 JSON 文档）。
     */
    public static ObjectNode createObjectNode() {
        return MAPPER.createObjectNode();
    }

    /**
     * 创建空数组节点（用于手工构造 JSON 文档）。
     */
    public static ArrayNode createArrayNode() {
        return MAPPER.createArrayNode();
    }
}
