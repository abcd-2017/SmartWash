package com.smartwash.controller.web;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.from.schools.SearchSchoolsFrom;
import com.smartwash.service.ISchoolsService;
import com.smartwash.vo.schools.SchoolNameVo;
import com.smartwash.vo.schools.SchoolsVo;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@Slf4j
@RestController
@RequestMapping("/web")
public class WebSchoolsController {
    @Autowired
    private ISchoolsService schoolsService;

    //获取所有学校
    @GetMapping("/auth/schools/all")
    public Result<Page<SchoolsVo>> getAll(SearchSchoolsFrom schoolsFrom) {
        return Result.ok(schoolsService.getAllSchools(schoolsFrom));
    }

    //获取所有学校的名字
    @GetMapping("/schools/allName")
    public Result<List<SchoolNameVo>> getAllName(@RequestParam(value = "schoolName", required = false) String schoolName) {
        return Result.ok(schoolsService.getAllSchoolsName(schoolName));
    }
}
