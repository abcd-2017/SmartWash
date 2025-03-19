package com.smartwash.controller.web;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwash.common.Result;
import com.smartwash.from.schools.SearchSchoolsFrom;
import com.smartwash.service.ISchoolsService;
import com.smartwash.vo.schools.SchoolsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@RestController
@RequestMapping("/web/schools")
public class WebSchoolsController {
    @Autowired
    private ISchoolsService schoolsService;

    //获取所有学校
    @GetMapping("/all")
    public Result<Page<SchoolsVo>> getAll(SearchSchoolsFrom schoolsFrom) {
        return Result.ok(schoolsService.getAllSchools(schoolsFrom));
    }
}
