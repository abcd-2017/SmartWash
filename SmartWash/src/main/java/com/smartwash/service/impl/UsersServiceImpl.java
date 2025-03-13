package com.smartwash.service.impl;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.common.DefaultConstant;
import com.smartwash.entity.Users;
import com.smartwash.from.users.AddUserFrom;
import com.smartwash.from.users.SearchUserFrom;
import com.smartwash.from.users.UpdateUserFrom;
import com.smartwash.mapper.UsersMapper;
import com.smartwash.service.ISchoolsService;
import com.smartwash.service.IUsersService;
import com.smartwash.vo.users.UserVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
public class UsersServiceImpl extends ServiceImpl<UsersMapper, Users> implements IUsersService {
    @Autowired
    private ISchoolsService schoolsService;

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
        usersVoPage.setRecords(users.stream().map(it -> {
            UserVo userVo = new UserVo();
            userVo.setSchools(schoolsService.getById(it.getSchoolId()));
            BeanUtils.copyProperties(it, userVo);
            return userVo;
        }).toList());

        return usersVoPage;
    }

    @Override
    public Boolean addUsers(AddUserFrom addUsersFrom) {
        Users users = new Users();
        //如果密码为空，设置初始密码
        String password = "";
        if (StringUtils.hasText(addUsersFrom.getPassword())) {
            password = SecureUtil.md5(addUsersFrom.getPassword());
        } else {
            password = SecureUtil.md5(DefaultConstant.DEFAULT_PASSWORD);
        }
        BeanUtils.copyProperties(addUsersFrom, users);
        users.setPassword(password);
        return save(users);
    }

    @Override
    public Boolean updateUser(UpdateUserFrom usersFrom) {
        Users users = getById(usersFrom.getUserId());
        BeanUtils.copyProperties(usersFrom, users);
        if (StringUtils.hasText(usersFrom.getPassword())) {
            users.setPassword(SecureUtil.md5(usersFrom.getPassword()));
        }
        return updateById(users);
    }

    @Override
    public Boolean deleteUsers(String ids) {
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
}
