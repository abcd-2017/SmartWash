package com.smartwash.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.entity.Users;
import com.smartwash.from.users.*;
import com.smartwash.vo.users.UserInfoVo;
import com.smartwash.vo.users.UserVo;
import jakarta.validation.Valid;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author
 * @since 2025-03-06
 */
public interface IUsersService extends IService<Users> {

    //获取所有用户
    Page<UserVo> getAllUsers(SearchUserFrom usersFrom);

    //添加用户
    Boolean addUsers(AddUserFrom addUsersFrom);

    //修改用户
    Boolean updateUser(UpdateUserFrom usersFrom);

    Boolean deleteUsers(String ids);

    //根据手机号查用户
    Users getUserByPhone(String phoneNumber);

    Users getUserByStudentId(String studentId);

    Users getUserByCampusCard(String campusCard);

    Boolean registerUser(UserRegisterFrom userRegisterFrom);

    //完善用户学校信息
    Boolean updateUserInfo(@Valid UpdateUserInfo updateUserInfo, Long userId);

    UserInfoVo getUserInfo(Long userId);

    Boolean bingCampus(String campusCard, Long userId);

    Boolean unBingCampus(Long userId);
}
