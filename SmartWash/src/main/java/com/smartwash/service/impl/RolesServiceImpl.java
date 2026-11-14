package com.smartwash.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.entity.AdminUsers;
import com.smartwash.entity.Roles;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.from.roles.AddRolesFrom;
import com.smartwash.from.roles.SearchRolesFrom;
import com.smartwash.from.roles.UpdateRolesFrom;
import com.smartwash.mapper.AdminUsersMapper;
import com.smartwash.mapper.RolesMapper;
import com.smartwash.service.IRolesService;
import com.smartwash.vo.roles.RolesVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author
 * @since 2025-03-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RolesServiceImpl extends ServiceImpl<RolesMapper, Roles> implements IRolesService {

    /** 内置超级管理员角色 ID：不允许删除/改名，防止系统失去唯一超管入口 */
    private static final Integer BUILT_IN_ROLE_ID = 1;

    private final AdminUsersMapper adminUsersMapper;

    @Override
    public Page<RolesVo> getAllRoles(SearchRolesFrom rolesFrom) {
        Page<Roles> page = new Page<>(rolesFrom.getPage(), rolesFrom.getSize());
        LambdaQueryWrapper<Roles> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(rolesFrom.getRoleId() != null, b -> b.eq(Roles::getRoleId, rolesFrom.getRoleId()));
        queryWrapper.and(StringUtils.hasText(rolesFrom.getRoleName()),
                b -> b.like(Roles::getRoleName, rolesFrom.getRoleName()));

        List<Roles> Roles = this.list(page, queryWrapper);
        Page<RolesVo> RolesVoPage = new Page<>();
        BeanUtils.copyProperties(page, RolesVoPage);
        RolesVoPage.setRecords(Roles.stream().map(it -> {
            RolesVo RoleVo = new RolesVo();
            BeanUtils.copyProperties(it, RoleVo);
            return RoleVo;
        }).toList());

        return RolesVoPage;
    }

    @Override
    public Boolean addRoles(AddRolesFrom addRolesFrom) {
        Roles Roles = new Roles();
        BeanUtils.copyProperties(addRolesFrom, Roles);
        boolean result = save(Roles);
        log.info("新增角色, roleId: {}, roleName: {}", Roles.getRoleId(), Roles.getRoleName());
        return result;
    }

    @Override
    public Roles getByRoleName(String roleName) {
        return getOne(new QueryWrapper<Roles>().lambda().eq(Roles::getRoleName, roleName));
    }

    @Override
    public Boolean updateRoles(UpdateRolesFrom rolesFrom) {
        log.info("更新角色, roleId: {}", rolesFrom.getRoleId());
        Roles roles = getById(rolesFrom.getRoleId());
        if (roles == null) {
            throw new CustomExceptions("角色不存在");
        }
        // 内置超管角色保护：角色名不允许修改（描述等其他字段可正常维护）
        if (Objects.equals(BUILT_IN_ROLE_ID, roles.getRoleId())
                && StringUtils.hasText(rolesFrom.getRoleName())
                && !rolesFrom.getRoleName().equals(roles.getRoleName())) {
            log.warn("更新角色被拒绝：内置角色不可改名, roleId: {}, roleName: {}", roles.getRoleId(), rolesFrom.getRoleName());
            throw new CustomExceptions("内置角色不可改名");
        }
        BeanUtils.copyProperties(rolesFrom, roles);
        return updateById(roles);
    }

    @Override
    public Boolean deleteRoles(String ids) {
        log.info("删除角色, ids: {}", ids);
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::valueOf)
                .collect(Collectors.toList());
        // 内置超管角色保护：不允许删除，防止系统失去唯一超管入口
        if (idList.contains(BUILT_IN_ROLE_ID.longValue())) {
            log.warn("删除角色被拒绝：内置角色不可删除, ids: {}", ids);
            throw new CustomExceptions("内置角色不可删除");
        }
        // 删除前校验引用：角色仍被管理员账号占用时拒绝，防止误删导致账号失去权限（评审 P1）
        Long adminCount = adminUsersMapper.selectCount(
                new LambdaQueryWrapper<AdminUsers>().in(AdminUsers::getRoleId, idList));
        if (adminCount != null && adminCount > 0) {
            log.warn("删除角色被拒绝：角色仍被管理员引用, ids: {}, 管理员数: {}", ids, adminCount);
            throw new CustomExceptions("该角色下仍有 " + adminCount + " 个管理员,无法删除");
        }
        return removeByIds(idList);
    }
}
