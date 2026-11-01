package com.smartwash.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * MinIO 对象键 ↔ 对外直链转换测试。
 *
 * <p>这两个方法是「DB 存对象键、读时拼 URL」的核心：换服务器/换域名只改配置不必刷库，
 * 同时要能把存量的绝对 URL 还原成对象键（历史实现把端点拼进了 DB）。</p>
 */
@DisplayName("MinioConfig urlOf / toObjectKey 测试")
class MinioConfigTest {

    private MinioConfig config(String endpoint, String externalEndpoint, String bucket) {
        MinioConfig config = new MinioConfig();
        config.setEndpoint(endpoint);
        config.setExternalEndpoint(externalEndpoint);
        config.setBucketName(bucket);
        return config;
    }

    // ==================== urlOf ====================

    @Test
    @DisplayName("urlOf：endpoint + bucket + 对象键")
    void urlOf_basic() {
        MinioConfig config = config("http://192.168.1.61:9000", null, "smartwash");

        assertEquals("http://192.168.1.61:9000/smartwash/avatar/uuid.png",
                config.urlOf("avatar/uuid.png"));
    }

    @Test
    @DisplayName("urlOf：端点带尾斜杠时不产生双斜杠")
    void urlOf_stripsTrailingSlashOfEndpoint() {
        MinioConfig config = config("http://minio.local:9000/", null, "smartwash");

        assertEquals("http://minio.local:9000/smartwash/game/snake/index.html",
                config.urlOf("game/snake/index.html"));
    }

    @Test
    @DisplayName("urlOf：external-endpoint 优先于 endpoint（对外直链用对外端点）")
    void urlOf_prefersExternalEndpoint() {
        MinioConfig config = config("http://minio:9000", "https://cdn.example.com", "smartwash");

        assertEquals("https://cdn.example.com/smartwash/avatar/uuid.png",
                config.urlOf("avatar/uuid.png"));
    }

    @Test
    @DisplayName("urlOf：对象键路径段做 URL 编码（zip 条目名含空格/中文）")
    void urlOf_encodesPathSegments() {
        MinioConfig config = config("http://minio.local:9000", null, "smartwash");

        assertEquals("http://minio.local:9000/smartwash/game/my%20game/index.html",
                config.urlOf("game/my game/index.html"));
        assertEquals("http://minio.local:9000/smartwash/game/%E6%B8%B8%E6%88%8F/index.html",
                config.urlOf("game/游戏/index.html"));
    }

    @Test
    @DisplayName("urlOf：空对象键返回空串（不拼出以斜杠结尾的无效地址）")
    void urlOf_blankKeyReturnsBlank() {
        MinioConfig config = config("http://minio.local:9000", null, "smartwash");

        assertEquals("", config.urlOf(null));
        assertEquals("", config.urlOf(""));
        assertEquals("", config.urlOf("   "));
    }

    // ==================== toObjectKey ====================

    @Test
    @DisplayName("toObjectKey：已是对象键的值原样返回")
    void toObjectKey_passthroughForKey() {
        MinioConfig config = config("http://minio.local:9000", null, "smartwash");

        assertEquals("avatar/uuid.png", config.toObjectKey("avatar/uuid.png"));
        assertEquals("", config.toObjectKey(""));
    }

    @Test
    @DisplayName("toObjectKey：绝对 URL 还原为对象键")
    void toObjectKey_extractsFromAbsoluteUrl() {
        MinioConfig config = config("http://minio.local:9000", null, "smartwash");

        assertEquals("avatar/uuid.png",
                config.toObjectKey("http://minio.local:9000/smartwash/avatar/uuid.png"));
    }

    @Test
    @DisplayName("toObjectKey：端点已变更的存量 URL 仍能还原（按 /{bucket}/ 定位，不依赖已知端点）")
    void toObjectKey_worksAfterEndpointChanged() {
        // 旧数据是用 http://192.168.1.61:9000 拼的，当前配置已换成新域名
        MinioConfig config = config("http://minio.local:9000", "https://cdn.example.com", "smartwash");

        assertEquals("avatar/uuid.png",
                config.toObjectKey("http://192.168.1.61:9000/smartwash/avatar/uuid.png"),
                "换域名后旧数据必须仍能还原，否则历史头像全失效");
    }

    @Test
    @DisplayName("toObjectKey：URL 编码的存量值还原为真实对象键")
    void toObjectKey_decodesLegacyEncodedValue() {
        MinioConfig config = config("http://minio.local:9000", null, "smartwash");

        assertEquals("game/游戏/index.html",
                config.toObjectKey("http://minio.local:9000/smartwash/game/%E6%B8%B8%E6%88%8F/index.html"));
    }

    @Test
    @DisplayName("toObjectKey / urlOf 互为逆运算（含编码与中文）")
    void toObjectKey_roundTripsWithUrlOf() {
        MinioConfig config = config("http://minio.local:9000", null, "smartwash");

        for (String key : new String[]{
                "avatar/uuid.png",
                "game/snake/index.html",
                "game/游戏/index.html",
                "game/my game/index.html"}) {
            assertEquals(key, config.toObjectKey(config.urlOf(key)),
                    "对象键经 urlOf → toObjectKey 必须原样还原: " + key);
        }
    }

    @Test
    @DisplayName("toObjectKey：null 返回 null")
    void toObjectKey_nullReturnsNull() {
        assertNull(config("http://minio.local:9000", null, "smartwash").toObjectKey(null));
    }
}
