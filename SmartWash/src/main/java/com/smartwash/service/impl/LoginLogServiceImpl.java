package com.smartwash.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.common.LoginResult;
import com.smartwash.common.client.ClientContext;
import com.smartwash.common.client.ClientContextHolder;
import com.smartwash.entity.LoginLog;
import com.smartwash.from.login_log.SearchLoginLogFrom;
import com.smartwash.mapper.LoginLogMapper;
import com.smartwash.service.ILoginLogService;
import com.smartwash.vo.login_log.LoginLogVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 登录日志服务实现。
 */
@Slf4j
@Service
public class LoginLogServiceImpl extends ServiceImpl<LoginLogMapper, LoginLog> implements ILoginLogService {

    @Override
    public void recordLoginLog(String account, Long userId, String ip, LoginResult result) {
        // 非 Web 线程（理论上不会发生）无上下文，退化为 unknown，不阻断写入
        ClientContext context = ClientContextHolder.get();
        LoginLog loginLog = new LoginLog();
        loginLog.setPhone(account);
        loginLog.setUserId(userId);
        loginLog.setIp(ip);
        loginLog.setResult(result == null ? LoginResult.FAIL.getStatus() : result.getStatus());
        if (context != null) {
            loginLog.setPlatform(context.getPlatform());
            loginLog.setAppChannel(context.getAppChannel());
            loginLog.setAppVersion(context.getAppVersion());
            loginLog.setUa(context.getUserAgent());
            loginLog.setNetworkType(context.getNetworkType());
        } else {
            loginLog.setPlatform(ClientContext.PLATFORM_UNKNOWN);
            loginLog.setAppChannel(ClientContext.CHANNEL_UNKNOWN);
            loginLog.setNetworkType(ClientContext.NETWORK_TYPE_UNKNOWN);
        }
        save(loginLog);
    }

    @Override
    public Page<LoginLogVo> pageLoginLogs(SearchLoginLogFrom from) {
        Page<LoginLog> page = new Page<>(from.getPage(), from.getSize());
        LambdaQueryWrapper<LoginLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(from.getUserId() != null, LoginLog::getUserId, from.getUserId());
        queryWrapper.like(StringUtils.hasText(from.getPhone()), LoginLog::getPhone, from.getPhone());
        queryWrapper.eq(StringUtils.hasText(from.getPlatform()), LoginLog::getPlatform, from.getPlatform());
        queryWrapper.eq(StringUtils.hasText(from.getAppChannel()), LoginLog::getAppChannel, from.getAppChannel());
        queryWrapper.eq(StringUtils.hasText(from.getResult()), LoginLog::getResult, from.getResult());
        queryWrapper.ge(from.getStartTime() != null, LoginLog::getCreatedAt, from.getStartTime());
        queryWrapper.le(from.getEndTime() != null, LoginLog::getCreatedAt, from.getEndTime());
        queryWrapper.orderByDesc(LoginLog::getCreatedAt);

        Page<LoginLog> result = this.page(page, queryWrapper);
        Page<LoginLogVo> voPage = new Page<>();
        BeanUtils.copyProperties(result, voPage);
        voPage.setRecords(result.getRecords().stream().map(it -> {
            LoginLogVo vo = new LoginLogVo();
            BeanUtils.copyProperties(it, vo);
            vo.setResultDesc(resolveResultDesc(it.getResult()));
            return vo;
        }).toList());
        return voPage;
    }

    /** 结果码转文案；未知码原样返回兜底文案，避免管理端出现空白 */
    private String resolveResultDesc(String result) {
        for (LoginResult loginResult : LoginResult.values()) {
            if (loginResult.getStatus().equals(result)) {
                return loginResult.getDescription();
            }
        }
        return "未知";
    }
}
