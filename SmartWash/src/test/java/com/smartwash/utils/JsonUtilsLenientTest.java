package com.smartwash.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * JsonUtils 宽容解析特性测试：对齐历史 FastJSON 容忍度，防止迁移后外部 JSON 文件解析回归
 * （真实案例：/app/config/releases/latest/version.json 含前导零数字，迁移 Jackson 后解析失败）。
 */
@DisplayName("JsonUtils 宽容解析特性")
class JsonUtilsLenientTest {

    @Test
    @DisplayName("前导零数字：{\"version\":\"1.01\"} 形态不报错（ALLOW_LEADING_ZEROS_FOR_NUMBERS）")
    void parse_leadingZeroes_success() {
        // 模拟 version.json line2 col19 的场景：数值字段含前导零
        String json = "{\"buildNumber\":01,\"name\":\"release\"}";
        Map<String, Object> map = assertDoesNotThrow(() -> JsonUtils.parseMap(json));
        assertEquals(1, map.get("buildNumber"));
        assertEquals("release", map.get("name"));
    }

    @Test
    @DisplayName("单引号字符串可解析")
    void parse_singleQuotes_success() {
        String json = "{'name':'smartwash'}";
        Map<String, Object> map = assertDoesNotThrow(() -> JsonUtils.parseMap(json));
        assertEquals("smartwash", map.get("name"));
    }

    @Test
    @DisplayName("JSON 注释可解析")
    void parse_comments_success() {
        String json = "{\"name\":\"x\" /* 注释 */}";
        assertDoesNotThrow(() -> JsonUtils.parseMap(json));
    }

    @Test
    @DisplayName("无引号字段名可解析")
    void parse_unquotedFieldNames_success() {
        String json = "{name:\"x\"}";
        Map<String, Object> map = assertDoesNotThrow(() -> JsonUtils.parseMap(json));
        assertEquals("x", map.get("name"));
    }

    @Test
    @DisplayName("尾逗号可解析")
    void parse_trailingComma_success() {
        String json = "{\"items\":[1,2,],}";
        List<Integer> items = assertDoesNotThrow(
                () -> JsonUtils.parseObject(json, new TypeReference<Map<String, List<Integer>>>() {
                }).get("items"));
        assertEquals(List.of(1, 2), items);
    }
}
