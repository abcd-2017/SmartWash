package com.smartwash.toolbox.toolkit;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * 短码生成器：SecureRandom + 去混淆字母表（剔除 0 O 1 l I J L j 等易混字符）。
 * 唯一性由 toolbox_short_code.uk_code 唯一索引最终裁决，本类只负责抽签。
 */
@Component
public class ShortCodeGenerator {

    /** 54 字符去混淆字母表（Kutt 方案），54^7 ≈ 1.3 万亿空间 */
    private static final char[] ALPHABET =
            "23456789abcdefghkmnpqrstuvwxyzABCDEFGHKLMNPQRSTUVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 7;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 自定义别名规则：4-16 位，字母数字及 -_ */
    private static final Pattern CUSTOM_CODE = Pattern.compile("^[0-9a-zA-Z-_]{4,16}$");

    public String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }

    /** 校验自定义别名，通过返回 true */
    public boolean validCustomCode(String code) {
        return code != null && CUSTOM_CODE.matcher(code).matches();
    }
}
