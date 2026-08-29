package com.smartwash.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.entity.AdminUsers;
import com.smartwash.from.admin_users.AddAdminUserFrom;
import com.smartwash.from.admin_users.SearchAdminUserFrom;
import com.smartwash.from.admin_users.UpdateAdminUserFrom;
import com.smartwash.vo.admin_users.AdminUserVo;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
public interface IAdminUsersService extends IService<AdminUsers> {

    Page<AdminUserVo> getAllAdminUsers(SearchAdminUserFrom adminUserFrom);

    Boolean addAdminUsers(AddAdminUserFrom adminUserFrom);

    Boolean deleteAdminUsers(String ids);

    Boolean updateAdminUsers(UpdateAdminUserFrom adminUsersFrom);

    AdminUsers getAdminUserByName(String username);

    /**
     * 查询管理员的实际角色名（roles.role_name），登录响应用。
     *
     * @param username 管理员用户名（不带 admin- 前缀）
     * @return 角色名；管理员不存在或未绑定角色时返回兜底值 admin
     */
    String getAdminRoleName(String username);

    AdminUserVo getUserById(Long userId);
}
