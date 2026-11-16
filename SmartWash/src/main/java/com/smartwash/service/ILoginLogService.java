package com.smartwash.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.common.LoginResult;
import com.smartwash.entity.LoginLog;
import com.smartwash.from.login_log.SearchLoginLogFrom;
import com.smartwash.vo.login_log.LoginLogVo;

public interface ILoginLogService extends IService<LoginLog> {

    /**
     * 写入一条登录日志。
     *
     * <p>平台标识 / 项目渠道 / App 版本 / UA / 网络类型统一从 {@link com.smartwash.common.client.ClientContextHolder}
     * 取（由 ClientContextFilter 在请求进入时解析），调用方只需提供账号、用户 ID、IP 与结果，
     * 避免各登录分支重复读取请求头。
     *
     * @param account 登录账号（用户端手机号 / 管理端用户名）
     * @param userId  登录用户 ID，失败或无法确定时传 null
     * @param ip      客户端 IP（沿用登录接口的可信代理解析结果）
     * @param result  登录结果
     */
    void recordLoginLog(String account, Long userId, String ip, LoginResult result);

    /**
     * 分页查询登录日志（管理端），按登录时间倒序。
     */
    Page<LoginLogVo> pageLoginLogs(SearchLoginLogFrom from);
}
