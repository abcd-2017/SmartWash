package com.smartwash.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.common.DefaultConstant;
import com.smartwash.entity.AdminUsers;
import com.smartwash.entity.Roles;
import com.smartwash.from.admin_users.AddAdminUserFrom;
import com.smartwash.from.admin_users.SearchAdminUserFrom;
import com.smartwash.from.admin_users.UpdateAdminUserFrom;
import com.smartwash.mapper.AdminUsersMapper;
import com.smartwash.service.IAdminUsersService;
import com.smartwash.service.IRolesService;
import com.smartwash.vo.admin_users.AdminUserVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUsersServiceImpl extends ServiceImpl<AdminUsersMapper, AdminUsers> implements IAdminUsersService {
    private final IRolesService rolesService;

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

        // 批量查询角色数据，避免N+1问题
        Set<Long> roleIds = AdminUsers.stream().map(com.smartwash.entity.AdminUsers::getRoleId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Roles> roleMap = roleIds.isEmpty() ? Collections.emptyMap()
                : rolesService.listByIds(roleIds).stream().collect(Collectors.toMap(r -> r.getRoleId().longValue(), Function.identity()));

        AdminUsersVoPage.setRecords(AdminUsers.stream().map(it -> {
            AdminUserVo AdminUserVo = new AdminUserVo();
            AdminUserVo.setRoles(roleMap.get(it.getRoleId()));
            BeanUtils.copyProperties(it, AdminUserVo);
            return AdminUserVo;
        }).toList());

        return AdminUsersVoPage;
    }

    @Override
    public Boolean addAdminUsers(AddAdminUserFrom addAdminUsersFrom) {
        AdminUsers adminUsers = new AdminUsers();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        //如果密码为空，设置初始密码
        String password = "";
        if (StringUtils.hasText(addAdminUsersFrom.getPasswordHash())) {
            password = encoder.encode(addAdminUsersFrom.getPasswordHash());
        } else {
            password = encoder.encode(DefaultConstant.generateDefaultPassword());
        }
        BeanUtils.copyProperties(addAdminUsersFrom, adminUsers);
        adminUsers.setPasswordHash(password);
        boolean result = save(adminUsers);
        log.info("新增管理员, adminId: {}, username: {}", adminUsers.getAdminId(), adminUsers.getUsername());
        return result;
    }

    @Override
    public Boolean updateAdminUsers(UpdateAdminUserFrom adminUserFrom) {
        log.info("更新管理员, adminId: {}", adminUserFrom.getAdminId());
        AdminUsers adminUsers = getById(adminUserFrom.getAdminId());
        BeanUtils.copyProperties(adminUserFrom, adminUsers);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        if (StringUtils.hasText(adminUserFrom.getPassword())) {
            adminUsers.setPasswordHash(encoder.encode(adminUserFrom.getPassword()));
        }
        return updateById(adminUsers);
    }

    @Override
    public AdminUsers getAdminUserByName(String username) {
        return getOne(new LambdaQueryWrapper<AdminUsers>().eq(AdminUsers::getUsername, username));
    }

    @Override
    public AdminUserVo getUserById(Long userId) {
        AdminUsers users = getById(userId);
        AdminUserVo adminUserVo = new AdminUserVo();
        BeanUtils.copyProperties(users, adminUserVo);
        return adminUserVo;
    }

    @Override
    public Boolean deleteAdminUsers(String ids) {
        log.info("删除管理员, ids: {}", ids);
        String[] idList = ids.split(",");
        for (String id : idList) {
            removeById(Integer.parseInt(id));
        }
        return true;
    }
}
