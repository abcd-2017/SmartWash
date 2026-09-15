package com.smartwash.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 管理员演示用验证码查询响应视图对象。
 * 仅用于演示/调试场景，严禁在其他接口返回验证码明文。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminCaptchaVo {

    /** 查询的手机号（明文，便于管理员确认） */
    private String phone;

    /** 该手机号当前有效的验证码列表（register/reset 两种用途，仅返回实际查到的） */
    private List<CaptchaInfo> captchas;

    /**
     * 单个验证码信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaptchaInfo {

        /** 验证码用途标识：register（注册）/ reset（重置密码） */
        private String purpose;

        /** 验证码用途中文描述：注册 / 重置密码 */
        private String purposeDesc;

        /** 验证码明文（仅演示接口返回） */
        private String code;

        /** 剩余有效秒数 */
        private Long remainSeconds;
    }
}
