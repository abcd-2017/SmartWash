package com.smartwash.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartwash.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * MinIO 上传桶匿名只读白名单策略（评审 #23 收敛）。
 * 匿名仅授予 {@link #PUBLIC_READ_PREFIXES} 三前缀的 s3:GetObject（游戏直链/用户头像/默认头像），
 * 不授予 ListBucket/写入，其余前缀不再匿名可读——堵住全桶公共读的桶级泄露面。
 * 独立纯函数便于单测：只做策略文档构造与判定，不触碰 MinIO 客户端。
 */
@Slf4j
public final class MinioPublicReadPolicy {

    /**
     * 匿名只读白名单前缀（新增前缀必须同步评估三端直链消费方，禁止静默扩白名单）。
     * <p>覆盖：游戏包(game/)、用户头像(avatar/)、默认头像(default/)、
     * 游戏封面(game-covers/)、工作台配图(workbench/covers/)、菜谱封面(dining/covers/)。
     * 前三者由端上 Coil 匿名直拉（不带 token），必须公共读，否则封面/配图 403。</p>
     * <p><b>dining/covers/ 三端直链消费方评估（吃啥菜谱封面，02 号票）：</b>
     * <ul>
     *   <li>Web 管理后台：菜谱列表与编辑弹窗以 img 直拉封面（不带 Authorization 头）——需公共读；</li>
     *   <li>Android（后续批次）：菜谱库页/新增菜谱页按「库内存对象键、读时拼直链」现行标准由 Coil 匿名直拉——需公共读；</li>
     *   <li>鸿蒙（本期 Out of Scope）：用户端菜单后续按同标准消费，接口契约已留文档（GLOSSARY dining 词条）。</li>
     * </ul>
     * 封面非私密内容（运营+用户共同维护的公共菜谱库配图），无按人鉴权诉求；
     * 白名单仅授 s3:GetObject，写路径仍走管理端/用户端鉴权接口，不扩大泄露面。</p>
     */
    public static final List<String> PUBLIC_READ_PREFIXES =
            List.of("game/", "avatar/", "default/", "game-covers/", "workbench/covers/", "dining/covers/");

    private static final String GET_OBJECT = "s3:GetObject";

    private MinioPublicReadPolicy() {
    }

    /**
     * 构造白名单策略：单条 statement、Resource 列表覆盖全部白名单前缀、仅 s3:GetObject。
     */
    public static String buildScopedPolicy(String bucket) {
        var root = JsonUtils.createObjectNode();
        root.put("Version", "2012-10-17");
        var statements = root.putArray("Statement");
        var statement = statements.addObject();
        statement.put("Effect", "Allow");
        statement.putObject("Principal").putArray("AWS").add("*");
        statement.putArray("Action").add(GET_OBJECT);
        var resources = statement.putArray("Resource");
        PUBLIC_READ_PREFIXES.stream()
                .map(prefix -> "arn:aws:s3:::" + bucket + "/" + prefix + "*")
                .forEach(resources::add);
        return JsonUtils.toJson(root);
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
        JsonNode root;
        try {
            root = JsonUtils.readTree(existingPolicy);
        } catch (Exception e) {
            // 非法策略文档按不满足处理（降级），由覆盖写入纠正；此处留痕便于发现桶策略被外部篡改
            log.warn("MinIO 桶策略文档解析失败，按不满足处理并触发覆盖写入, bucket: {}, 原因: {}", bucket, e.getMessage());
            return false;
        }
        JsonNode statements = root == null ? null : root.get("Statement");
        if (statements == null || !statements.isArray() || statements.isEmpty()) {
            return false;
        }
        boolean allPrefixesGranted = false;
        for (JsonNode statement : statements) {
            if (statement == null || !"Allow".equals(statement.path("Effect").asText())) {
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
    private static boolean grantsGetObject(JsonNode statement) {
        JsonNode action = statement.get("Action");
        if (action != null && action.isTextual()) {
            String s = action.asText();
            return GET_OBJECT.equals(s) || "*".equals(s) || "s3:*".equals(s);
        }
        if (action != null && action.isArray()) {
            for (JsonNode node : action) {
                String a = node.asText();
                if (GET_OBJECT.equals(a) || "*".equals(a) || "s3:*".equals(a)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 语句的 Resource 归一为 arn 列表（兼容字符串与数组形态） */
    private static List<String> resourceArns(JsonNode statement) {
        JsonNode resource = statement.get("Resource");
        if (resource != null && resource.isTextual()) {
            return List.of(resource.asText());
        }
        if (resource != null && resource.isArray()) {
            List<String> arns = new ArrayList<>(resource.size());
            for (JsonNode node : resource) {
                arns.add(node.asText());
            }
            return arns;
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
