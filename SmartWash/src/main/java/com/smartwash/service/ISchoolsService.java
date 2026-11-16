package com.smartwash.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.entity.Schools;
import com.smartwash.from.schools.AddSchoolsFrom;
import com.smartwash.from.schools.SearchSchoolsFrom;
import com.smartwash.from.schools.UpdateSchoolsFrom;
import com.smartwash.vo.schools.SchoolNameVo;
import com.smartwash.vo.schools.SchoolsVo;
import jakarta.validation.Valid;

import java.util.List;

public interface ISchoolsService extends IService<Schools> {

    Page<SchoolsVo> getAllSchools(SearchSchoolsFrom schoolsFrom);

    Boolean addSchools(AddSchoolsFrom addSchoolsFrom);

    Boolean updateSchool(@Valid UpdateSchoolsFrom schoolsFrom);

    Boolean deleteSchools(String ids);

    Schools getSearchByName(String schoolName);

    List<SchoolNameVo> getAllSchoolsName(String schoolName);
}
