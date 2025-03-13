package com.smartwash.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.entity.Schools;
import com.smartwash.from.schools.AddSchoolsFrom;
import com.smartwash.from.schools.SearchSchoolsFrom;
import com.smartwash.from.schools.UpdateSchoolsFrom;
import com.smartwash.mapper.SchoolsMapper;
import com.smartwash.service.ISchoolsService;
import com.smartwash.vo.schools.SchoolsVo;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
@Service
public class SchoolsServiceImpl extends ServiceImpl<SchoolsMapper, Schools> implements ISchoolsService {

    //获取所有学校
    @Override
    public Page<SchoolsVo> getAllSchools(SearchSchoolsFrom schoolsFrom) {
        Page<Schools> page = new Page<>(schoolsFrom.getPage(), schoolsFrom.getSize());
        LambdaQueryWrapper<Schools> queryWrapper = new LambdaQueryWrapper<>();

        queryWrapper.and(schoolsFrom.getSchoolId() != null, b -> b.eq(Schools::getSchoolId, schoolsFrom.getSchoolId()));
        queryWrapper.and(StringUtils.hasText(schoolsFrom.getSchoolName()), b -> b.like(Schools::getSchoolName, schoolsFrom.getSchoolName()));
        //查询大于等于多少寄存柜的学校
        queryWrapper.and(schoolsFrom.getLockerCount() != null, b -> b.ge(Schools::getLockerCount, schoolsFrom.getLockerCount()));

        List<Schools> schools = this.list(page, queryWrapper);
        Page<SchoolsVo> schoolsVoPage = new Page<>();
        BeanUtils.copyProperties(page, schoolsVoPage);
        schoolsVoPage.setRecords(schools.stream().map(it -> {
            SchoolsVo schoolsVo = new SchoolsVo();
            BeanUtils.copyProperties(it, schoolsVo);
            return schoolsVo;
        }).toList());

        return schoolsVoPage;
    }

    //添加学校
    @Override
    public Boolean addSchools(AddSchoolsFrom addSchoolsFrom) {
        Schools schools = new Schools();
        BeanUtils.copyProperties(addSchoolsFrom, schools);
        return save(schools);
    }

    //修改学校
    @Override
    public Boolean updateSchool(UpdateSchoolsFrom schoolsFrom) {
        Schools school = getById(schoolsFrom.getSchoolId());
        BeanUtils.copyProperties(schoolsFrom, school);
        return updateById(school);
    }

    //删除学校
    @Override
    public Boolean deleteSchools(String ids) {
        String[] idList = ids.split(",");
        for (String id : idList) {
            removeById(Integer.parseInt(id));
        }
        return true;
    }

    @Override
    public Schools getSearchByName(String schoolName) {
        if (!StringUtils.hasText(schoolName)) return null;
        LambdaQueryWrapper<Schools> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Schools::getSchoolName, schoolName);
        return getOne(queryWrapper);
    }
}
