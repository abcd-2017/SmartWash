package com.smartwash.service.impl;


import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.common.DefaultConstant;
import com.smartwash.entity.AdminUsers;
import com.smartwash.from.admin_users.AddAdminUserFrom;
import com.smartwash.from.admin_users.SearchAdminUserFrom;
import com.smartwash.from.admin_users.UpdateAdminUserFrom;
import com.smartwash.mapper.AdminUsersMapper;
import com.smartwash.service.IAdminUsersService;
import com.smartwash.service.IRolesService;
import com.smartwash.vo.admin_users.AdminUserVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@Service
public class AdminUsersServiceImpl extends ServiceImpl<AdminUsersMapper, AdminUsers> implements IAdminUsersService {
    @Autowired
    private IRolesService rolesService;

    @Override
    public Page<AdminUserVo> getAllAdminUsers(SearchAdminUserFrom searchUserFrom) {
        Page<AdminUsers> page = new Page<>(searchUserFrom.getPage(), searchUserFrom.getSize());
        LambdaQueryWrapper<AdminUsers> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(searchUserFrom.getAdminId() != null, b -> b.eq(AdminUsers::getAdminId, searchUserFrom.getAdminId()));
        queryWrapper.and(searchUserFrom.getUsername() != null, b -> b.like(AdminUsers::getUsername, searchUserFrom.getUsername()));
        queryWrapper.and(searchUserFrom.getRoleId() != null, b -> b.eq(AdminUsers::getRoleId, searchUserFrom.getRoleId()));

        List<AdminUsers> AdminUsers = this.list(page, queryWrapper);
        Page<AdminUserVo> AdminUsersVoPage = new Page<>();
        BeanUtils.copyProperties(page, AdminUsersVoPage);
        AdminUsersVoPage.setRecords(AdminUsers.stream().map(it -> {
            AdminUserVo AdminUserVo = new AdminUserVo();
            AdminUserVo.setRoles(rolesService.getById(it.getRoleId()));
            BeanUtils.copyProperties(it, AdminUserVo);
            return AdminUserVo;
        }).toList());

        return AdminUsersVoPage;
    }

    @Override
    public Boolean addAdminUsers(AddAdminUserFrom addAdminUsersFrom) {
        AdminUsers AdminUsers = new AdminUsers();
        //如果密码为空，设置初始密码
        String password = "";
        if (StringUtils.hasText(addAdminUsersFrom.getPasswordHash())) {
            password = SecureUtil.md5(addAdminUsersFrom.getPasswordHash());
        } else {
            password = SecureUtil.md5(DefaultConstant.ADMIN_DEFAULT_PASSWORD);
        }
        BeanUtils.copyProperties(addAdminUsersFrom, AdminUsers);
        AdminUsers.setPasswordHash(password);
        return save(AdminUsers);
    }

    @Override
    public Boolean updateAdminUsers(UpdateAdminUserFrom adminUserFrom) {
        AdminUsers AdminUsers = getById(adminUserFrom.getAdminId());
        BeanUtils.copyProperties(adminUserFrom, AdminUsers);
        if (StringUtils.hasText(adminUserFrom.getPasswordHash())) {
            AdminUsers.setPasswordHash(SecureUtil.md5(adminUserFrom.getPasswordHash()));
        }
        return updateById(AdminUsers);
    }

    @Override
    public AdminUsers getAdminUserByName(String username) {
        return getOne(new LambdaQueryWrapper<AdminUsers>().eq(AdminUsers::getUsername, username));
    }

    @Override
    public Boolean deleteAdminUsers(String ids) {
        String[] idList = ids.split(",");
        for (String id : idList) {
            removeById(Integer.parseInt(id));
        }
        return true;
    }
}
