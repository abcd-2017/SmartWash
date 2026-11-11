package com.smartwash.divination.support;

import com.smartwash.exception.CustomExceptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 外呼 URL 安全校验（SSRF 边界）单测：结构/快拒/DNS 全解析逐 IP 判定全覆盖。
 * 注意：依赖真实 DNS 的用例只使用「保证可解析」与「保证不可解析」的主机名；
 * 内网 IP 均为字面量用例（不依赖 DNS）。
 */
class DivUrlSafetyValidatorTest {

    /** DNS 层开着的实例（生产默认口径）：IP 字面量/结构/快拒/DNS 全覆盖 */
    private final DivUrlSafetyValidator validator = new DivUrlSafetyValidator(true);

    /** DNS 层关着的实例（Fake-IP 调试环境口径）：仅结构/快拒/IP 字面量 */
    private final DivUrlSafetyValidator noDnsValidator = new DivUrlSafetyValidator(false);

    // ---------- 结构 ----------

    @Test
    void rejectsNullBlankAndNonHttps() {
        assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(CustomExceptions.class);
        assertThatThrownBy(() -> validator.validate("  ")).isInstanceOf(CustomExceptions.class);
        assertThatThrownBy(() -> validator.validate("http://api.example.com")).isInstanceOf(CustomExceptions.class);
        assertThatThrownBy(() -> validator.validate("ftp://api.example.com")).isInstanceOf(CustomExceptions.class);
    }

    @Test
    void rejectsUserinfoAndMissingHost() {
        assertThatThrownBy(() -> validator.validate("https://user:pass@api.example.com"))
                .isInstanceOf(CustomExceptions.class).hasMessageContaining("凭据");
        assertThatThrownBy(() -> validator.validate("https:///path/only"))
                .isInstanceOf(CustomExceptions.class).hasMessageContaining("主机名");
    }

    @Test
    void rejectsBadPort() {
        assertThatThrownBy(() -> validator.validate("https://api.example.com:0/"))
                .isInstanceOf(CustomExceptions.class).hasMessageContaining("端口");
        assertThatThrownBy(() -> validator.validate("https://api.example.com:70000/"))
                .isInstanceOf(CustomExceptions.class).hasMessageContaining("端口");
    }

    // ---------- 主机名快拒 ----------

    @Test
    void rejectsLocalhostVariants() {
        assertThatThrownBy(() -> validator.validate("https://localhost/v1"))
                .isInstanceOf(CustomExceptions.class).hasMessageContaining("内网或本机");
        assertThatThrownBy(() -> validator.validate("https://API.LOCAL/v1"))
                .isInstanceOf(CustomExceptions.class).hasMessageContaining("内网或本机");
        assertThatThrownBy(() -> validator.validate("https://gw.internal/v1"))
                .isInstanceOf(CustomExceptions.class).hasMessageContaining("内网或本机");
        assertThatThrownBy(() -> validator.validate("https://printer.home.arpa/v1"))
                .isInstanceOf(CustomExceptions.class).hasMessageContaining("内网或本机");
    }

    // ---------- IP 字面量（不依赖 DNS） ----------

    @Test
    void rejectsLoopbackAndPrivateV4() {
        assertThatThrownBy(() -> validator.validate("https://127.0.0.1/v1"))
                .isInstanceOf(CustomExceptions.class).hasMessageContaining("内网或本机");
        assertThatThrownBy(() -> validator.validate("https://0.0.0.0/v1"))
                .isInstanceOf(CustomExceptions.class).hasMessageContaining("内网或本机");
        assertThatThrownBy(() -> validator.validate("https://10.1.2.3/v1"))
                .isInstanceOf(CustomExceptions.class);
        assertThatThrownBy(() -> validator.validate("https://192.168.1.1/v1"))
                .isInstanceOf(CustomExceptions.class);
        assertThatThrownBy(() -> validator.validate("https://172.16.0.9/v1"))
                .isInstanceOf(CustomExceptions.class);
        assertThatThrownBy(() -> validator.validate("https://100.64.0.10/v1"))
                .isInstanceOf(CustomExceptions.class).hasMessageContaining("内网或本机");
        assertThatThrownBy(() -> validator.validate("https://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(CustomExceptions.class).hasMessageContaining("内网或本机");
    }

    @Test
    void rejectsForbiddenV6() {
        assertThatThrownBy(() -> validator.validate("https://[::1]/v1"))
                .isInstanceOf(CustomExceptions.class).hasMessageContaining("内网或本机");
        assertThatThrownBy(() -> validator.validate("https://[fe80::1]/v1"))
                .isInstanceOf(CustomExceptions.class).hasMessageContaining("内网或本机");
        // ULA fd00::/8（唯一本地）
        assertThatThrownBy(() -> validator.validate("https://[fd00::1234]/v1"))
                .isInstanceOf(CustomExceptions.class).hasMessageContaining("内网或本机");
        // IPv4 映射内网 ::ffff:10.0.0.5
        assertThatThrownBy(() -> validator.validate("https://[::ffff:10.0.0.5]/v1"))
                .isInstanceOf(CustomExceptions.class).hasMessageContaining("内网或本机");
    }

    @Test
    void acceptsPublicIpLiteral() {
        // 8.8.8.8 / 1.1.1.1 为公网字面量（不依赖 DNS，IP 字面量不走域名解析）
        assertThatCode(() -> validator.validate("https://8.8.8.8/v1")).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate("https://1.1.1.1:443/v1")).doesNotThrowAnyException();
        // 公网 v6 字面量
        assertThatCode(() -> validator.validate("https://[2606:4700:4700::1111]/v1")).doesNotThrowAnyException();
    }

    // ---------- 域名 DNS（仅用确定性主机名） ----------

    @Test
    void rejectsUnresolvableHostOrHijackedIp() {
        // .invalid TLD 由 RFC 2606 保证永不解析；但本机代理（Fake-IP 模式）会把任意域名劫持到
        // RFC 2544/5180 benchmark 保留段（198.18.0.0/15、2001:2::/64）——两段均非公网可路由，
        // validator 对解析结果逐 IP 判定，无论走「解析失败」还是「劫持 IP」分支都必须拒绝
        assertThatThrownBy(() -> validator.validate("https://definitely-not-resolvable.invalid/v1"))
                .isInstanceOf(CustomExceptions.class);
    }

    @Test
    void dnsOffKeepsStructureAndLiteralChecks() {
        // dns-check=false（Fake-IP 调试环境口径）：域名解析层跳过，但结构/快拒/IP 字面量仍生效
        assertThatCode(() -> noDnsValidator.validate("https://api.example.com/v1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> noDnsValidator.validate("https://127.0.0.1/v1"))
                .isInstanceOf(CustomExceptions.class).hasMessageContaining("内网或本机");
        assertThatThrownBy(() -> noDnsValidator.validate("https://[fd00::1234]/v1"))
                .isInstanceOf(CustomExceptions.class);
        assertThatThrownBy(() -> noDnsValidator.validate("https://localhost/v1"))
                .isInstanceOf(CustomExceptions.class);
        assertThatThrownBy(() -> noDnsValidator.validate("http://api.example.com/v1"))
                .isInstanceOf(CustomExceptions.class);
    }
}
