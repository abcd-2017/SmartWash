package com.smartwash.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.common.DefaultConstant;
import com.smartwash.entity.AdminUsers;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.entity.Roles;
import com.smartwash.from.admin_users.AddAdminUserFrom;
import com.smartwash.from.admin_users.SearchAdminUserFrom;
import com.smartwash.from.admin_users.UpdateAdminUserFrom;
import com.smartwash.mapper.AdminUsersMapper;
import com.smartwash.service.IAdminUsersService;
import com.smartwash.service.IRolesService;
import com.smartwash.service.JwtBlacklistService;
import com.smartwash.vo.admin_users.AdminUserVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUsersServiceImpl extends ServiceImpl<AdminUsersMapper, AdminUsers> implements IAdminUsersService {
    private final IRolesService rolesService;
    private final JwtBlacklistService jwtBlacklistService;

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
        if (StringUtils.hasText(addAdminUsersFrom.getPassword())) {
            password = encoder.encode(addAdminUsersFrom.getPassword());
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
        if (adminUsers == null) {
            throw new CustomExceptions("管理员不存在");
        }
        // 记录改密前的用户名：表单可能同时改名，旧令牌的 sub 仍是旧名，失效标记必须落在旧名上
        String usernameBeforeUpdate = adminUsers.getUsername();
        BeanUtils.copyProperties(adminUserFrom, adminUsers);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        boolean passwordChanged = StringUtils.hasText(adminUserFrom.getPassword());
        if (passwordChanged) {
            adminUsers.setPasswordHash(encoder.encode(adminUserFrom.getPassword()));
        }
        boolean result = updateById(adminUsers);
        // 改密后使该管理员的全部既有会话失效（access/refresh 的签发时间早于改密时间戳即拒绝），
        // 旧 refresh 因未持久化 jti 无法批量拉黑，故以改密时间戳为会话分界；改名场景下新名不产生旧令牌，无需标记
        if (passwordChanged) {
            jwtBlacklistService.invalidateUserSessions(
                    DefaultConstant.ADMIN_USER_LOGIN_TYPE + "-" + usernameBeforeUpdate);
        }
        return result;
    }

    @Override
    public AdminUsers getAdminUserByName(String username) {
        return getOne(new LambdaQueryWrapper<AdminUsers>().eq(AdminUsers::getUsername, username));
    }

    @Override
    public String getAdminRoleName(String username) {
        AdminUsers adminUsers = getAdminUserByName(username);
        if (adminUsers == null || adminUsers.getRoleId() == null) {
            // 管理员不存在或未绑定角色时返回兜底角色名，避免登录响应缺少角色字段
            return DefaultConstant.ADMIN_USER_LOGIN_TYPE;
        }
        Roles role = rolesService.getById(adminUsers.getRoleId());
        if (role == null || !StringUtils.hasText(role.getRoleName())) {
            log.warn("管理员角色不存在或角色名为空，登录角色回退为 admin, username: {}", username);
            return DefaultConstant.ADMIN_USER_LOGIN_TYPE;
        }
        return role.getRoleName();
    }

    @Override
    public AdminUserVo getUserById(Long userId) {
        AdminUsers users = getById(userId);
        if (users == null) {
            return null;
        }
        AdminUserVo adminUserVo = new AdminUserVo();
        BeanUtils.copyProperties(users, adminUserVo);
        return adminUserVo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteAdminUsers(String ids, Long currentAdminId) {
        log.info("删除管理员, ids: {}, currentAdminId: {}", ids, currentAdminId);
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::valueOf)
                .collect(Collectors.toList());
        // 防自删：删除列表包含当前登录管理员时拒绝，避免自己把自己登出系统
        if (currentAdminId != null && idList.contains(currentAdminId)) {
            log.warn("删除管理员被拒绝：不能删除当前登录账号, currentAdminId: {}", currentAdminId);
            throw new CustomExceptions("不能删除当前登录账号");
        }
        // 防删光 TOCTOU：先锁定全表行（FOR UPDATE），串行化所有删除路径，
        // 使「计数校验 + 删除」之间不存在被并发事务删空的窗口
        baseMapper.selectCount(new LambdaQueryWrapper<AdminUsers>().last("FOR UPDATE"));
        // 删除后剩余管理员数必须 >= 1，拒绝删除全部管理员
        Long remainCount = baseMapper.selectCount(
                new LambdaQueryWrapper<AdminUsers>().notIn(AdminUsers::getAdminId, idList));
        if (remainCount == null || remainCount < 1) {
            log.warn("删除管理员被拒绝：至少保留一个管理员账号, ids: {}", ids);
            throw new CustomExceptions("至少保留一个管理员账号");
        }
        return removeByIds(idList);
    }
}
