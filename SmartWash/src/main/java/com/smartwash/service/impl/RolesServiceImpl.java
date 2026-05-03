package com.smartwash.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.entity.Roles;
import com.smartwash.from.roles.AddRolesFrom;
import com.smartwash.from.roles.SearchRolesFrom;
import com.smartwash.from.roles.UpdateRolesFrom;
import com.smartwash.mapper.RolesMapper;
import com.smartwash.service.IRolesService;
import com.smartwash.vo.roles.RolesVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author
 * @since 2025-03-09
 */
@Service
public class RolesServiceImpl extends ServiceImpl<RolesMapper, Roles> implements IRolesService {

    @Override
    public Page<RolesVo> getAllRoles(SearchRolesFrom rolesFrom) {
        Page<Roles> page = new Page<>(rolesFrom.getPage(), rolesFrom.getSize());
        LambdaQueryWrapper<Roles> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(rolesFrom.getRoleId() != null, b -> b.eq(Roles::getRoleId, rolesFrom.getRoleId()));

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
        return save(Roles);
    }

    @Override
    public Roles getByRoleName(String roleName) {
        return getOne(new QueryWrapper<Roles>().lambda().eq(Roles::getRoleName, roleName));
    }

    @Override
    public Boolean updateRoles(UpdateRolesFrom rolesFrom) {
        Roles roles = getById(rolesFrom.getRoleId());
        BeanUtils.copyProperties(rolesFrom, roles);
        return updateById(roles);
    }

    @Override
    public Boolean deleteRoles(String ids) {
        String[] idList = ids.split(",");
        for (String id : idList) {
            removeById(Integer.parseInt(id));
        }
        return true;
    }
}
