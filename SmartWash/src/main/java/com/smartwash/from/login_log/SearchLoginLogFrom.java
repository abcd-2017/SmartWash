package com.smartwash.from.login_log;

import com.smartwash.from.BaseSearchFrom;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 登录日志分页查询参数（均可空 = 不限）。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SearchLoginLogFrom extends BaseSearchFrom {

    /** 用户 ID（用户端 users.user_id / 管理端 admin_users.admin_id） */
    private Long userId;

    /** 登录账号（手机号/用户名），模糊匹配 */
    private String phone;

    /** 平台标识：android/ios/harmonyos/web/unknown */
    private String platform;

    /** 登录结果：success/fail */
    private String result;

    /** 起始时间（含） */
    private LocalDateTime startTime;

    /** 结束时间（含） */
    private LocalDateTime endTime;
}
