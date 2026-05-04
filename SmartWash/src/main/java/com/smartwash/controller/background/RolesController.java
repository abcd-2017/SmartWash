package com.smartwash.controller.background;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "角色管理", description = "系统角色的增删改查")
@Slf4j
@RestController
@RequestMapping("/admin/roles")
public class RolesController {
    @Autowired
    private IRolesService rolesService;

    @Operation(summary = "分页查询角色列表", description = "根据条件分页查询系统角色列表")
    @GetMapping("/all")
    public Result<Page<RolesVo>> getAll(SearchRolesFrom rolesFrom) {
        return Result.ok(rolesService.getAllRoles(rolesFrom));
    }

    @Operation(summary = "新增角色", description = "新增系统角色，角色名不可重复")
    @PostMapping("/add")
    public Result<String> addSchool(@RequestBody @Valid AddRolesFrom addRolesFrom) {
        if (StringUtils.hasText(addRolesFrom.getRoleName()) && rolesService.getByRoleName(addRolesFrom.getRoleName()) != null) {
            return Result.failMsg("该角色名重复");
        }
        rolesService.addRoles(addRolesFrom);
        return Result.ok("添加成功");
    }

    @Operation(summary = "更新角色", description = "修改角色信息")
    @PostMapping("/update")
    public Result<String> updateSchool(@RequestBody @Valid UpdateRolesFrom rolesFrom) {
        Roles user = rolesService.getById(rolesFrom.getRoleId());
        if (!Objects.equals(user.getRoleName(), rolesFrom.getRoleName()) && rolesService.getByRoleName(rolesFrom.getRoleName()) != null) {
            return Result.failMsg("该角色名重复");
        }
        rolesService.updateRoles(rolesFrom);
        return Result.ok("修改成功");
    }

    @Operation(summary = "批量删除角色", description = "根据ID批量删除角色，多个ID用逗号分隔")
    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deleteRoles(@PathVariable("ids") @Parameter(description = "角色ID列表，多个ID用逗号分隔", required = true, example = "1,2,3") String ids) {
        return Result.ok(rolesService.deleteRoles(ids));
    }
}
