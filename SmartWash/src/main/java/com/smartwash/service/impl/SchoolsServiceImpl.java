package com.smartwash.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.common.LockerStatusEnum;
import com.smartwash.entity.Lockers;
import com.smartwash.entity.Schools;
import com.smartwash.from.schools.AddSchoolsFrom;
import com.smartwash.from.schools.SearchSchoolsFrom;
import com.smartwash.from.schools.UpdateSchoolsFrom;
import com.smartwash.mapper.SchoolsMapper;
import com.smartwash.service.ILockersService;
import com.smartwash.service.ISchoolsService;
import com.smartwash.vo.schools.SchoolNameVo;
import com.smartwash.vo.schools.SchoolsVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchoolsServiceImpl extends ServiceImpl<SchoolsMapper, Schools> implements ISchoolsService {
    private final ILockersService lockersService;

    @Lazy
    @Autowired
    private SchoolsServiceImpl self;

    //获取所有学校
    @Override
    public Page<SchoolsVo> getAllSchools(SearchSchoolsFrom schoolsFrom) {
        Page<Schools> page = new Page<>(schoolsFrom.getPage(), schoolsFrom.getSize());
        LambdaQueryWrapper<Schools> queryWrapper = new LambdaQueryWrapper<>();

        queryWrapper.and(schoolsFrom.getSchoolId() != null, b -> b.eq(Schools::getSchoolId, schoolsFrom.getSchoolId()));
        queryWrapper.and(StringUtils.hasText(schoolsFrom.getSchoolName()), b -> b.like(Schools::getSchoolName, schoolsFrom.getSchoolName()));
        queryWrapper.and(StringUtils.hasText(schoolsFrom.getSchoolCode()), b -> b.like(Schools::getSchoolCode, schoolsFrom.getSchoolCode()));
        queryWrapper.and(StringUtils.hasText(schoolsFrom.getProvince()), b -> b.eq(Schools::getProvince, schoolsFrom.getProvince()));
        queryWrapper.and(StringUtils.hasText(schoolsFrom.getCity()), b -> b.eq(Schools::getCity, schoolsFrom.getCity()));
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
    @Transactional
    @CacheEvict(value = "schools", allEntries = true)
    public Boolean addSchools(AddSchoolsFrom addSchoolsFrom) {
        Schools schools = new Schools();
        BeanUtils.copyProperties(addSchoolsFrom, schools);

        //添加学校
        save(schools);

        //插入选中存储柜数量的存储柜
        List<Lockers> lockerList = new ArrayList<>();
        // 假设每个学校的柜子编号从1开始（如果学校已有数据，编号逻辑可做调整）
        for (int i = 1; i <= addSchoolsFrom.getLockerCount(); i++) {
            Lockers locker = new Lockers();
            locker.setSchoolId(schools.getSchoolId());
            locker.setLockerNumber(i);
            locker.setStatus(LockerStatusEnum.FREE.getValue()); // '0'表示空闲状态
            lockerList.add(locker);
        }
        int insertedCount = 0;
        for (Lockers locker : lockerList) {
            lockersService.save(locker);
            insertedCount++;
        }
        log.info("添加学校成功, schoolId: {}, schoolName: {}, lockerCount: {}", schools.getSchoolId(), schools.getSchoolName(), insertedCount);
        return true;
    }

    //修改学校
    @Override
    @CacheEvict(value = "schools", allEntries = true)
    public Boolean updateSchool(UpdateSchoolsFrom schoolsFrom) {
        log.info("修改学校, schoolId: {}", schoolsFrom.getSchoolId());
        Schools school = getById(schoolsFrom.getSchoolId());
        BeanUtils.copyProperties(schoolsFrom, school);
        return updateById(school);
    }

    //删除学校
    @Override
    @Transactional
    @CacheEvict(value = "schools", allEntries = true)
    public Boolean deleteSchools(String ids) {
        log.info("删除学校, ids: {}", ids);
        String[] idList = ids.split(",");
        for (String id : idList) {
            Long schoolId = Long.parseLong(id);
            removeById(schoolId);
            lockersService.deleteLockersBySchoolId(schoolId);
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

    @Override
    public List<SchoolNameVo> getAllSchoolsName(String schoolName) {
        if (!StringUtils.hasText(schoolName)) {
            return self.getAllSchoolsNameFromCache();
        }
        // 有搜索关键词时直接查库，不缓存
        LambdaQueryWrapper<Schools> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(Schools::getSchoolName, schoolName);
        return list(queryWrapper).stream().map(s -> {
            SchoolNameVo schoolNameVo = new SchoolNameVo();
            BeanUtils.copyProperties(s, schoolNameVo);
            return schoolNameVo;
        }).toList();
    }

    @Cacheable(value = "schools", key = "'allName'")
    public List<SchoolNameVo> getAllSchoolsNameFromCache() {
        return list().stream().map(s -> {
            SchoolNameVo schoolNameVo = new SchoolNameVo();
            BeanUtils.copyProperties(s, schoolNameVo);
            return schoolNameVo;
        }).toList();
    }
}
