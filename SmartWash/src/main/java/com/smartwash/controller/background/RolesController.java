package com.smartwash.controller.background;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.entity.Roles;
import com.smartwash.from.roles.AddRolesFrom;
import com.smartwash.from.roles.SearchRolesFrom;
import com.smartwash.from.roles.UpdateRolesFrom;
import com.smartwash.service.IRolesService;
import com.smartwash.vo.roles.RolesVo;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author
 * @since 2025-03-09
 */
@Slf4j
@RestController
@RequestMapping("/admin/roles")
public class RolesController {
    @Autowired
    private IRolesService rolesService;

    //获取所有角色
    @GetMapping("/all")
    public Result<Page<RolesVo>> getAll(SearchRolesFrom rolesFrom) {
        return Result.ok(rolesService.getAllRoles(rolesFrom));
    }

    //添加角色
    @PostMapping("/add")
    public Result<String> addSchool(@RequestBody @Valid AddRolesFrom addRolesFrom) {
        if (StringUtils.hasText(addRolesFrom.getRoleName()) && rolesService.getByRoleName(addRolesFrom.getRoleName()) != null) {
            return Result.failMsg("该角色名重复");
        }
        rolesService.addRoles(addRolesFrom);
        return Result.ok("添加成功");
    }

    //修改角色
    @PostMapping("/update")
    public Result<String> updateSchool(@RequestBody @Valid UpdateRolesFrom rolesFrom) {
        Roles user = rolesService.getById(rolesFrom.getRoleId());
        if (!Objects.equals(user.getRoleName(), rolesFrom.getRoleName()) && rolesService.getByRoleName(rolesFrom.getRoleName()) != null) {
            return Result.failMsg("该角色名重复");
        }
        rolesService.updateRoles(rolesFrom);
        return Result.ok("修改成功");
    }

    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deleteRoles(@PathVariable("ids") String ids) {
        return Result.ok(rolesService.deleteRoles(ids));
    }
}
