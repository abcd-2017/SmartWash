package com.smartwash.service.impl;

import cn.hutool.core.util.DesensitizedUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.common.DefaultConstant;
import com.smartwash.entity.Schools;
import com.smartwash.entity.Users;
import com.smartwash.from.users.*;
import com.smartwash.mapper.UsersMapper;
import com.smartwash.service.ISchoolsService;
import com.smartwash.service.IUsersService;
import com.smartwash.vo.schools.SchoolsVo;
import com.smartwash.vo.users.UserInfoVo;
import com.smartwash.vo.users.UserVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsersServiceImpl extends ServiceImpl<UsersMapper, Users> implements IUsersService {
    private final ISchoolsService schoolsService;

    @Override
    public Page<UserVo> getAllUsers(SearchUserFrom usersFrom) {
        Page<Users> page = new Page<>(usersFrom.getPage(), usersFrom.getSize());
        LambdaQueryWrapper<Users> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(usersFrom.getUserId() != null, b -> b.eq(Users::getUserId, usersFrom.getUserId()));
        queryWrapper.and(usersFrom.getSchoolId() != null, b -> b.eq(Users::getSchoolId, usersFrom.getSchoolId()));
        queryWrapper.and(StringUtils.hasText(usersFrom.getPhoneNumber()), b -> b.like(Users::getPhoneNumber, usersFrom.getPhoneNumber()));
        queryWrapper.and(StringUtils.hasText(usersFrom.getStudentId()), b -> b.eq(Users::getStudentId, usersFrom.getStudentId()));
        queryWrapper.and(StringUtils.hasText(usersFrom.getCampusCard()), b -> b.eq(Users::getCampusCard, usersFrom.getCampusCard()));

        List<Users> users = this.list(page, queryWrapper);
        Page<UserVo> usersVoPage = new Page<>();
        BeanUtils.copyProperties(page, usersVoPage);

        // 批量查询学校数据，避免N+1问题
        Set<Long> schoolIds = users.stream().map(Users::getSchoolId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Schools> schoolMap = schoolIds.isEmpty() ? Collections.emptyMap()
                : schoolsService.listByIds(schoolIds).stream().collect(Collectors.toMap(Schools::getSchoolId, Function.identity()));

        usersVoPage.setRecords(users.stream().map(it -> {
            UserVo userVo = new UserVo();
            userVo.setSchools(schoolMap.get(it.getSchoolId()));
            BeanUtils.copyProperties(it, userVo);
            return userVo;
        }).toList());

        return usersVoPage;
    }

    @Override
    public Boolean addUsers(AddUserFrom addUsersFrom) {
        Users users = new Users();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        //如果密码为空，设置初始密码
        String password = "";
        if (StringUtils.hasText(addUsersFrom.getPassword())) {
            password = encoder.encode(addUsersFrom.getPassword());
        } else {
            password = encoder.encode(DefaultConstant.DEFAULT_PASSWORD);
        }
        BeanUtils.copyProperties(addUsersFrom, users);
        users.setPassword(password);
        boolean result = save(users);
        log.info("管理员新增用户, userId: {}", users.getUserId());
        return result;
    }

    @Override
    public Boolean updateUser(UpdateUserFrom usersFrom) {
        log.info("更新用户信息, userId: {}", usersFrom.getUserId());
        Users users = getById(usersFrom.getUserId());
        BeanUtils.copyProperties(usersFrom, users);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        if (StringUtils.hasText(usersFrom.getPassword())) {
            users.setPassword(encoder.encode(usersFrom.getPassword()));
        }
        return updateById(users);
    }

    @Override
    public Boolean deleteUsers(String ids) {
        log.info("删除用户, ids: {}", ids);
        String[] idList = ids.split(",");
        for (String id : idList) {
            removeById(Integer.parseInt(id));
        }
        return true;
    }

    @Override
    public Users getUserByPhone(String phoneNumber) {
        return getOne(new QueryWrapper<Users>().lambda().eq(Users::getPhoneNumber, phoneNumber));
    }

    @Override
    public Users getUserByStudentId(String studentId) {
        return getOne(new QueryWrapper<Users>().lambda().eq(Users::getStudentId, studentId));
    }

    @Override
    public Users getUserByCampusCard(String campusCard) {
        return getOne(new QueryWrapper<Users>().lambda().eq(Users::getCampusCard, campusCard));
    }

    @Override
    public Boolean registerUser(UserRegisterFrom userRegisterFrom) {
        Users users = new Users();
        users.setPhoneNumber(userRegisterFrom.getPhoneNumber());
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        users.setPassword(encoder.encode(userRegisterFrom.getPassword()));
        boolean result = save(users);
        log.info("用户注册成功, userId: {}, phone: {}", users.getUserId(), DesensitizedUtil.mobilePhone(userRegisterFrom.getPhoneNumber()));
        return result;
    }

    @Override
    public Boolean updateUserInfo(UpdateUserInfo updateUserInfo, Long userId) {
        log.info("用户更新个人信息, userId: {}", userId);
        Users user = getById(userId);
        user.setSchoolId(updateUserInfo.getSchoolId());
        user.setStudentId(updateUserInfo.getStudentId());
        return updateById(user);
    }

    @Override
    public UserInfoVo getUserInfo(Long userId) {
        Users users = getById(userId);
        UserInfoVo userInfoVo = new UserInfoVo();
        BeanUtils.copyProperties(users, userInfoVo);
        userInfoVo.setPhoneNumber(DesensitizedUtil.mobilePhone(users.getPhoneNumber()));
        userInfoVo.setBalance(String.format("%.2f", users.getBalance().floatValue()));
        SchoolsVo schoolsVo = new SchoolsVo();
        BeanUtils.copyProperties(schoolsService.getById(users.getSchoolId()), schoolsVo);
        userInfoVo.setSchoolVo(schoolsVo);
        return userInfoVo;
    }

    @Override
    public Boolean bingCampus(String campusCard, Long userId) {
        log.info("用户绑定校园卡, userId: {}", userId);
        LambdaUpdateWrapper<Users> updateWrapper = new LambdaUpdateWrapper<Users>().eq(Users::getUserId, userId).set(Users::getCampusCard, campusCard);
        return update(updateWrapper);
    }

    @Override
    public Boolean unBingCampus(Long userId) {
        log.info("用户解绑校园卡, userId: {}", userId);
        return update(new LambdaUpdateWrapper<Users>().eq(Users::getUserId, userId).set(Users::getCampusCard, null));
    }
}
