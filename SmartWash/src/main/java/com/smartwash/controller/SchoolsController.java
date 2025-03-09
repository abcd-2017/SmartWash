package com.smartwash.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.from.schools_from.AddSchoolsFrom;
import com.smartwash.from.schools_from.SearchSchoolsFrom;
import com.smartwash.from.schools_from.UpdateSchoolsFrom;
import com.smartwash.service.ISchoolsService;
import com.smartwash.vo.schools_vo.SchoolsVo;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@RestController
@RequestMapping("/schools")
public class SchoolsController {
    @Autowired
    private ISchoolsService schoolsService;

    //获取所有学校
    @GetMapping("/all")
    public Result<Page<SchoolsVo>> getAll(SearchSchoolsFrom schoolsFrom) {
        return Result.ok(schoolsService.getAllSchools(schoolsFrom));
    }

    //添加学校
    @PostMapping("/add")
    public Result<String> addSchool(@RequestBody @Valid AddSchoolsFrom addSchoolsFrom) {
        if (schoolsService.getSearchByName(addSchoolsFrom.getSchoolName()) != null) {
            return Result.fail("该学校已经存在了");
        }
        schoolsService.addSchools(addSchoolsFrom);
        return Result.ok("添加成功");
    }

    //修改学校
    @PostMapping("/update")
    public Result<String> updateSchool(@RequestBody @Valid UpdateSchoolsFrom schoolsFrom) {
        if (schoolsService.getSearchByName(schoolsFrom.getSchoolName()) != null) {
            return Result.fail("该学校已经存在了");
        }
        schoolsService.updateSchool(schoolsFrom);
        return Result.ok("修改成功");
    }

    @DeleteMapping("/delete/{ids}")
    public Result<Boolean> deleteSchools(@PathVariable("ids") String ids) {
        return Result.ok(schoolsService.deleteSchools(ids));
    }
}
