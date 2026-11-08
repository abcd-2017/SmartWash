package com.smartwash.common.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 审计 HMAC 防篡改链服务（参考 spring-aiact 的思路：hash = HMAC(prev_hash + payload)）。
 *
 * <p><b>链定义</b>：{@code hash = HMAC-SHA256(secret, prev_hash + 规范化payload)}，
 * 其中规范化 payload = {@code adminUser|action|targetType|targetId|beforeJson|afterJson|ip|createdAt(秒)}，
 * 以 {@code |} 分隔、null 以空串占位；首条 prev_hash 取固定 {@link #GENESIS_HASH}。
 * createdAt 参与规范化时归一到秒——与表字段 DATETIME 秒级精度一致，保证入库后校验可复算。
 *
 * <p><b>密钥纪律</b>：密钥来自环境变量 {@code AUDIT_HMAC_SECRET}（application.yaml 不落生产默认值，
 * 与 JWT_SECRET 同一口径）。未配置时<b>退化为无密钥 SHA-256 摘要链</b>：仍可串链、可校验（防意外
 * 篡改/误删），但不具备防恶意重算能力（任何人都能重算 SHA-256），启动时 warn 提示；
 * 审计链路绝不因密钥缺失而中断业务。
 *
 * <p><b>校验语义</b>（{@link #verifyChain}，对按 id 升序的窗口逐条做两层检查）：
 * <ol>
 *   <li>内容完整性：行内 hash 必须等于 HMAC(该行 prev_hash, 该行规范化 payload)——检出内容篡改/hash 改写；</li>
 *   <li>链接完整性：后一条 prev_hash 必须等于前一条 hash——检出删行/换行断链。</li>
 * </ol>
 * 局限：窗口首条的 prev_hash 指向窗外前驱，不做窗外校验；删掉<b>末尾</b>条目无法从窗口内检出。
 */
@Slf4j
@Component
public class AuditHashService {

    /** 固定 genesis 值：64 个 0，首条审计记录的 prev_hash */
    public static final String GENESIS_HASH = "0".repeat(64);

    private static final char SEP = '|';

    private static final DateTimeFormatter SECONDS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** HMAC 密钥字节；未配置时为 null（退化为无密钥 SHA-256 链） */
    private final byte[] secretBytes;

    public AuditHashService(@Value("${audit.hmac-secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            // 退化而非阻断：审计不阻塞业务（对齐「审计失败绝不影响业务」约束），warn 一次由日志暴露
            log.warn("未配置 audit.hmac-secret（AUDIT_HMAC_SECRET），审计链退化为无密钥 SHA-256 摘要，仅能防意外篡改，生产必须注入随机密钥");
            this.secretBytes = null;
        } else {
            this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * 计算链哈希：有密钥时 HMAC-SHA256(secret, prevHash + payload)，无密钥退化 SHA-256(prevHash + payload)，
     * 返回 64 位小写十六进制。compute 与 verify 走同一路径，两种模式下链均自洽。
     */
    public String computeHash(String prevHash, String normalizedPayload) {
        byte[] input = (prevHash + normalizedPayload).getBytes(StandardCharsets.UTF_8);
        try {
            byte[] digest;
            if (secretBytes == null) {
                digest = java.security.MessageDigest.getInstance("SHA-256").digest(input);
            } else {
                Mac mac = Mac.getInstance(HMAC_ALGORITHM);
                mac.init(new SecretKeySpec(secretBytes, HMAC_ALGORITHM));
                digest = mac.doFinal(input);
            }
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            // JDK 内置算法，理论不可达；防御式包装（保持审计链路异常形态可预期）
            throw new IllegalStateException("审计 hash 计算失败", e);
        }
    }

    /**
     * 规范化 payload：全字段 {@code |} 分隔，null 以空串占位，createdAt 归一到秒。
     * 对同一入库条目（列值不变）必须产出同一串，否则校验不可复算。
     */
    public String buildPayload(AdminAuditLog entry) {
        return nvl(entry.getAdminUser()) + SEP
                + nvl(entry.getAction()) + SEP
                + nvl(entry.getTargetType()) + SEP
                + nvl(entry.getTargetId()) + SEP
                + nvl(entry.getBeforeJson()) + SEP
                + nvl(entry.getAfterJson()) + SEP
                + nvl(entry.getIp()) + SEP
                + formatSeconds(entry.getCreatedAt());
    }

    /**
     * 计算单条链哈希（prevHash 取链上前驱的 hash 或 genesis）。
     */
    public String computeEntryHash(AdminAuditLog entry, String prevHash) {
        return computeHash(prevHash, buildPayload(entry));
    }

    /**
     * 校验按 id 升序的条目窗口（两层检查见类注释），返回首个断裂点。
     */
    public AuditVerifyVo verifyChain(List<AdminAuditLog> ascending) {
        AuditVerifyVo vo = new AuditVerifyVo();
        vo.setCheckedCount(ascending == null ? 0 : ascending.size());
        vo.setValid(true);
        vo.setMessage("校验通过");
        if (ascending == null || ascending.isEmpty()) {
            return vo;
        }
        for (int i = 0; i < ascending.size(); i++) {
            AdminAuditLog cur = ascending.get(i);
            // 1) 内容完整性：行内 hash 必须等于 HMAC(行内 prevHash, 行内规范化 payload)
            if (!Objects.equals(computeHash(cur.getPrevHash(), buildPayload(cur)), lower(cur.getHash()))) {
                vo.setValid(false);
                vo.setBrokenId(cur.getId());
                vo.setMessage("第 " + (i + 1) + " 条(id=" + cur.getId() + ")内容校验失败：hash 与内容不符，疑似被篡改");
                return vo;
            }
            // 2) 链接完整性（首条 prevHash 指向窗外前驱，不校验）
            if (i > 0 && !Objects.equals(cur.getPrevHash(), lower(ascending.get(i - 1).getHash()))) {
                vo.setValid(false);
                vo.setBrokenId(cur.getId());
                vo.setMessage("第 " + (i + 1) + " 条(id=" + cur.getId() + ")链断裂：prev_hash 与前一条 hash 不衔接，疑似删行/换行");
                return vo;
            }
        }
        return vo;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    /** 归一到秒级格式串；null 以空串占位（payload 不抛异常） */
    private static String formatSeconds(LocalDateTime t) {
        return t == null ? "" : t.withNano(0).format(SECONDS_FORMATTER);
    }

    private static String lower(String hex) {
        return hex == null ? null : hex.toLowerCase();
    }
}
