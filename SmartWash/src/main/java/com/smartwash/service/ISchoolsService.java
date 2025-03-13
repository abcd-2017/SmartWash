package com.smartwash.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.entity.Schools;
import com.smartwash.from.schools.AddSchoolsFrom;
import com.smartwash.from.schools.SearchSchoolsFrom;
import com.smartwash.from.schools.UpdateSchoolsFrom;
import com.smartwash.vo.schools.SchoolsVo;
import jakarta.validation.Valid;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
public interface ISchoolsService extends IService<Schools> {

    Page<SchoolsVo> getAllSchools(SearchSchoolsFrom schoolsFrom);

    Boolean addSchools(AddSchoolsFrom addSchoolsFrom);

    Boolean updateSchool(@Valid UpdateSchoolsFrom schoolsFrom);

    Boolean deleteSchools(String ids);

    Schools getSearchByName(String schoolName);
}
