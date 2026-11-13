package com.smartwash.toolbox.toolkit;

/**
 * 私密文本（阅后即焚）状态（仅 contentType=7；四端映射展示文案的同步点）。
 */
public final class SecretState {

    /** 未领取：内容可被一次性揭示 */
    public static final String ACTIVE = "ACTIVE";
    /** 已焚毁：已被成功揭示，内容不可找回 */
    public static final String BURNED = "BURNED";
    /** 已过期：到期未领取，内容不可找回 */
    public static final String EXPIRED = "EXPIRED";

    private SecretState() {
    }
}
