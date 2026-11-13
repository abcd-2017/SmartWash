package com.smartwash.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.entity.SysConfig;

import java.util.List;

/**
 * 系统参数配置服务
 *
 * <p>管理端查询/更新走本服务；业务方（订单超时调度、登录锁定等）通过
 * {@link #getIntValue(String, int)} 读取，值修改后立即生效（缓存同步失效）。</p>
 */
public interface ISysConfigService extends IService<SysConfig> {

    /**
     * 全量参数列表（参数总量少，不分页）。
     */
    List<SysConfig> getAllConfigs();

    /**
     * 读取整数型参数（缓存命中不查库；值非法或缺失时回退默认值）。
     *
     * @param key          参数键
     * @param defaultValue 参数缺失/非法时的回退值
     */
    int getIntValue(String key, int defaultValue);

    /**
     * 更新参数值与备注（管理端入口）。
     *
     * @return 参数不存在返回 false
     */
    boolean updateValue(Long id, String configValue, String remark);
}
