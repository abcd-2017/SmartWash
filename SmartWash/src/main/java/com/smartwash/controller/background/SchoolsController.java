package com.smartwash.controller.background;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.common.audit.Audited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.smartwash.entity.Schools;
import com.smartwash.from.schools.AddSchoolsFrom;
import com.smartwash.from.schools.SearchSchoolsFrom;
import com.smartwash.from.schools.UpdateSchoolsFrom;
import com.smartwash.service.ISchoolsService;
import com.smartwash.vo.schools.SchoolsVo;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@Tag(name = "学校管理", description = "学校的增删改查")
@Slf4j
@RestController
@RequestMapping("/admin/schools")
public class SchoolsController {
    @Autowired
    private ISchoolsService schoolsService;

    @Operation(summary = "分页查询学校列表", description = "根据条件分页查询学校列表")
    @GetMapping("/all")
    public Result<Page<SchoolsVo>> getAll(SearchSchoolsFrom schoolsFrom) {
        return Result.ok(schoolsService.getAllSchools(schoolsFrom));
    }

    @Operation(summary = "新增学校", description = "新增学校信息，学校名称不可重复")
    @Audited(action = "SCHOOL_ADD", targetType = "school")
    @PostMapping("/add")
    public Result<String> addSchool(@RequestBody @Valid AddSchoolsFrom addSchoolsFrom) {
        if (schoolsService.getSearchByName(addSchoolsFrom.getSchoolName()) != null) {
            log.warn("新增学校被拒绝：学校名已存在, schoolName: {}", addSchoolsFrom.getSchoolName());
            return Result.failMsg("该学校已经存在了");
        }
        schoolsService.addSchools(addSchoolsFrom);
        log.info("新增学校成功, schoolName: {}", addSchoolsFrom.getSchoolName());
        return Result.ok("添加成功");
    }

    @Operation(summary = "更新学校", description = "修改学校信息")
    @Audited(action = "SCHOOL_UPDATE", targetType = "school", targetIdSpEL = "#schoolsFrom.schoolId")
    @PostMapping("/update")
    public Result<String> updateSchool(@RequestBody @Valid UpdateSchoolsFrom schoolsFrom) {
        if (schoolsService.getById(schoolsFrom.getSchoolId()) == null) {
            log.warn("更新学校被拒绝：学校不存在, schoolId: {}", schoolsFrom.getSchoolId());
            return Result.failMsg("学校不存在");
        }
        Schools school = schoolsService.getSearchByName(schoolsFrom.getSchoolName());
        if (school != null && !Objects.equals(school.getSchoolId(), schoolsFrom.getSchoolId())) {
            log.warn("更新学校被拒绝：学校名已存在, schoolId: {}, schoolName: {}", schoolsFrom.getSchoolId(), schoolsFrom.getSchoolName());
            return Result.failMsg("该学校已经存在了");
        }
        schoolsService.updateSchool(schoolsFrom);
        log.info("更新学校成功, schoolId: {}", schoolsFrom.getSchoolId());
        return Result.ok("修改成功");
    }

    @Operation(summary = "批量删除学校", description = "根据ID批量删除学校，多个ID用逗号分隔")
    @Audited(action = "SCHOOL_DELETE", targetType = "school", targetIdSpEL = "#ids")
    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deleteSchools(@PathVariable("ids") @Parameter(description = "学校ID列表，多个ID用逗号分隔", required = true, example = "1,2,3") String ids) {
        log.info("批量删除学校, ids: {}", ids);
        return Result.ok(schoolsService.deleteSchools(ids));
    }
}
