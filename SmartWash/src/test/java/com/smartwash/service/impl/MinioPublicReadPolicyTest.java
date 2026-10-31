package com.smartwash.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MinIO 匿名只读白名单策略测试（纯函数，不连 MinIO）。
 * 覆盖：白名单文档结构（三前缀/仅 GetObject/无 ListBucket）、幂等判定（符合/历史全桶/部分授予/过宽共存/空策略）。
 * 头像链路回归锚点：avatar/* 与 default/* 必须在白名单内（策略收窄后三端直拉头像不受影响）。
 */
@DisplayName("MinioPublicReadPolicy 桶策略白名单测试")
class MinioPublicReadPolicyTest {

    private static final String BUCKET = "smartwash";

    @Test
    @DisplayName("白名单文档：单 statement、Resource 覆盖三前缀、仅 s3:GetObject、不授 ListBucket/写入")
    void buildScopedPolicy_structure() {
        String policy = MinioPublicReadPolicy.buildScopedPolicy(BUCKET);
        JSONObject root = JSON.parseObject(policy);

        assertEquals("2012-10-17", root.getString("Version"));
        JSONArray statements = root.getJSONArray("Statement");
        assertEquals(1, statements.size(), "必须为单条 statement（三前缀合入 Resource 列表）");

        JSONObject statement = statements.getJSONObject(0);
        assertEquals("Allow", statement.getString("Effect"));
        assertTrue(statement.getJSONArray("Action").contains("s3:GetObject"), "必须授予 s3:GetObject");
        assertEquals(1, statement.getJSONArray("Action").size(), "只授 GetObject，不得携带其它 Action");
        String actions = statement.getJSONArray("Action").toJSONString();
        assertFalse(actions.contains("ListBucket"), "不得授予 ListBucket");
        assertFalse(actions.contains("PutObject"), "不得授予写入");

        JSONArray resources = statement.getJSONArray("Resource");
        assertTrue(resources.contains("arn:aws:s3:::" + BUCKET + "/game/*"), "game/* 必须在白名单（游戏直链）");
        assertTrue(resources.contains("arn:aws:s3:::" + BUCKET + "/avatar/*"), "avatar/* 必须在白名单（用户头像直链，收窄后不得回归）");
        assertTrue(resources.contains("arn:aws:s3:::" + BUCKET + "/default/*"), "default/* 必须在白名单（默认头像直链，收窄后不得回归）");
        assertFalse(resources.toJavaList(String.class).stream()
                        .anyMatch(r -> r.equals("arn:aws:s3:::" + BUCKET + "/*")),
                "不得存在桶级通配（收窄目的即消除桶级泄露面）");
    }

    @Test
    @DisplayName("幂等判定：空/无策略 → 不满足（需写入）")
    void isScopedSatisfied_emptyPolicy() {
        assertFalse(MinioPublicReadPolicy.isScopedSatisfied(null, BUCKET));
        assertFalse(MinioPublicReadPolicy.isScopedSatisfied("", BUCKET));
        assertFalse(MinioPublicReadPolicy.isScopedSatisfied("not-json", BUCKET));
    }

    @Test
    @DisplayName("幂等判定：历史全桶公共读 → 不满足（必须收窄覆盖）")
    void isScopedSatisfied_legacyBucketWidePolicy() {
        String legacy = """
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},
                 "Action":["s3:GetObject"],"Resource":["arn:aws:s3:::smartwash/*"]}]}
                """;
        assertFalse(MinioPublicReadPolicy.isScopedSatisfied(legacy, BUCKET));
    }

    @Test
    @DisplayName("幂等判定：三前缀已授且无过宽语句 → 满足（跳过写入）")
    void isScopedSatisfied_scopedPolicy_matches() {
        assertTrue(MinioPublicReadPolicy.isScopedSatisfied(
                MinioPublicReadPolicy.buildScopedPolicy(BUCKET), BUCKET));
    }

    @Test
    @DisplayName("幂等判定：三前缀已授但混有过宽语句 → 不满足（泄露面仍在）")
    void isScopedSatisfied_scopedPlusWildcard_rejected() {
        String mixed = """
                {"Version":"2012-10-17","Statement":[
                 {"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],
                  "Resource":["arn:aws:s3:::smartwash/game/*","arn:aws:s3:::smartwash/avatar/*","arn:aws:s3:::smartwash/default/*"]},
                 {"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],
                  "Resource":["arn:aws:s3:::smartwash/*"]}]}
                """;
        assertFalse(MinioPublicReadPolicy.isScopedSatisfied(mixed, BUCKET));
    }

    @Test
    @DisplayName("幂等判定：部分前缀授予 → 不满足（头像等直链会 403，必须补齐）")
    void isScopedSatisfied_partialPrefixes_rejected() {
        String partial = """
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},
                 "Action":["s3:GetObject"],"Resource":["arn:aws:s3:::smartwash/game/*"]}]}
                """;
        assertFalse(MinioPublicReadPolicy.isScopedSatisfied(partial, BUCKET));
    }

    @Test
    @DisplayName("幂等判定：三前缀已授 + 部署侧自定义非读语句 → 满足（跳过时不触碰）")
    void isScopedSatisfied_withCustomWriteStatement_tolerated() {
        String withCustom = """
                {"Version":"2012-10-17","Statement":[
                 {"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],
                  "Resource":["arn:aws:s3:::smartwash/default/*","arn:aws:s3:::smartwash/avatar/*","arn:aws:s3:::smartwash/game/*"]},
                 {"Effect":"Deny","Principal":{"AWS":["*"]},"Action":["s3:DeleteObject"],
                  "Resource":["arn:aws:s3:::smartwash/asset-receipt/*"]}]}
                """;
        assertTrue(MinioPublicReadPolicy.isScopedSatisfied(withCustom, BUCKET));
    }

    @Test
    @DisplayName("幂等判定：Action 为通配 s3:* 的桶级授予 → 视为过宽，不满足")
    void isScopedSatisfied_wildcardAction_rejected() {
        String wildcard = """
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},
                 "Action":["s3:*"],"Resource":["arn:aws:s3:::smartwash/*"]}]}
                """;
        assertFalse(MinioPublicReadPolicy.isScopedSatisfied(wildcard, BUCKET));
    }

    @Test
    @DisplayName("前缀白名单常量锚点：game/avatar/default 三前缀，扩白名单须显式改此处并有评审")
    void publicReadPrefixes_anchor() {
        assertEquals(3, MinioPublicReadPolicy.PUBLIC_READ_PREFIXES.size());
        assertTrue(MinioPublicReadPolicy.PUBLIC_READ_PREFIXES.containsAll(
                java.util.List.of("game/", "avatar/", "default/")));
    }
}
