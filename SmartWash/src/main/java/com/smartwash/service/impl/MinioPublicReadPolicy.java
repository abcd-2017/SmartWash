package com.smartwash.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.List;

/**
 * MinIO 上传桶匿名只读白名单策略（评审 #23 收敛）。
 * 匿名仅授予 {@link #PUBLIC_READ_PREFIXES} 三前缀的 s3:GetObject（游戏直链/用户头像/默认头像），
 * 不授予 ListBucket/写入，其余前缀不再匿名可读——堵住全桶公共读的桶级泄露面。
 * 独立纯函数便于单测：只做策略文档构造与判定，不触碰 MinIO 客户端。
 */
public final class MinioPublicReadPolicy {

    /** 匿名只读白名单前缀（新增前缀必须同步评估三端直链消费方，禁止静默扩白名单） */
    public static final List<String> PUBLIC_READ_PREFIXES = List.of("game/", "avatar/", "default/");

    private static final String GET_OBJECT = "s3:GetObject";

    private MinioPublicReadPolicy() {
    }

    /**
     * 构造白名单策略：单条 statement、Resource 列表覆盖全部白名单前缀、仅 s3:GetObject。
     */
    public static String buildScopedPolicy(String bucket) {
        JSONObject root = new JSONObject();
        root.put("Version", "2012-10-17");
        JSONArray statements = new JSONArray();
        JSONObject statement = new JSONObject();
        statement.put("Effect", "Allow");
        statement.put("Principal", JSONObject.of("AWS", List.of("*")));
        statement.put("Action", List.of(GET_OBJECT));
        statement.put("Resource", PUBLIC_READ_PREFIXES.stream()
                .map(prefix -> "arn:aws:s3:::" + bucket + "/" + prefix + "*")
                .toList());
        statements.add(statement);
        root.put("Statement", statements);
        return root.toJSONString();
    }

    /**
     * 判定现有策略是否已满足白名单语义（幂等跳过依据）：
     * 三前缀的匿名 GetObject 均已授予，且不存在过宽 GetObject 授予（桶级 arn 或 *）。
     * 其它非 GetObject 语句的存在不影响判定（视为部署侧自定义，跳过时不触碰）。
     */
    public static boolean isScopedSatisfied(String existingPolicy, String bucket) {
        if (existingPolicy == null || existingPolicy.isBlank()) {
            return false;
        }
        JSONObject root;
        try {
            root = JSON.parseObject(existingPolicy);
        } catch (Exception e) {
            // 非法策略文档按不满足处理，由覆盖写入纠正
            return false;
        }
        JSONArray statements = root == null ? null : root.getJSONArray("Statement");
        if (statements == null || statements.isEmpty()) {
            return false;
        }
        boolean allPrefixesGranted = false;
        for (int i = 0; i < statements.size(); i++) {
            JSONObject statement = statements.getJSONObject(i);
            if (statement == null || !"Allow".equals(statement.getString("Effect"))) {
                continue;
            }
            if (!grantsGetObject(statement)) {
                continue;
            }
            List<String> resources = resourceArns(statement);
            if (containsWildcardResource(resources, bucket)) {
                return false;
            }
            if (resources.containsAll(prefixArns(bucket))) {
                allPrefixesGranted = true;
            }
        }
        return allPrefixesGranted;
    }

    /** 语句的 Action 是否授予 s3:GetObject（兼容字符串与数组形态） */
    private static boolean grantsGetObject(JSONObject statement) {
        Object action = statement.get("Action");
        if (action instanceof String s) {
            return GET_OBJECT.equals(s) || "*".equals(s) || "s3:*".equals(s);
        }
        if (action instanceof JSONArray array) {
            for (int i = 0; i < array.size(); i++) {
                String a = array.getString(i);
                if (GET_OBJECT.equals(a) || "*".equals(a) || "s3:*".equals(a)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 语句的 Resource 归一为 arn 列表（兼容字符串与数组形态） */
    private static List<String> resourceArns(JSONObject statement) {
        Object resource = statement.get("Resource");
        if (resource instanceof String s) {
            return List.of(s);
        }
        if (resource instanceof JSONArray array) {
            return array.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    /** 是否存在过宽资源（桶级 arn 或全桶 *）——白名单语义下不允许 */
    private static boolean containsWildcardResource(List<String> resources, String bucket) {
        String bucketArn = "arn:aws:s3:::" + bucket;
        return resources.stream().anyMatch(r ->
                r.equals(bucketArn) || r.equals(bucketArn + "/*") || "*".equals(r) || "arn:aws:s3:::*".equals(r));
    }

    private static List<String> prefixArns(String bucket) {
        return PUBLIC_READ_PREFIXES.stream()
                .map(prefix -> "arn:aws:s3:::" + bucket + "/" + prefix + "*")
                .toList();
    }
}
