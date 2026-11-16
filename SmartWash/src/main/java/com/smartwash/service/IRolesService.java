package com.smartwash.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.entity.Roles;
import com.smartwash.from.roles.AddRolesFrom;
import com.smartwash.from.roles.SearchRolesFrom;
import com.smartwash.from.roles.UpdateRolesFrom;
import com.smartwash.vo.roles.RolesVo;

public interface IRolesService extends IService<Roles> {

    Page<RolesVo> getAllRoles(SearchRolesFrom rolesFrom);

    Boolean addRoles(AddRolesFrom addRolesFrom);

    Roles getByRoleName(String roleName);

    Boolean updateRoles(UpdateRolesFrom rolesFrom);

    Boolean deleteRoles(String ids);
}
