package com.smartwash.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartwash.entity.Users;
import com.smartwash.from.users.*;
import com.smartwash.vo.users.UserInfoVo;
import com.smartwash.vo.users.TransactionVo;
import com.smartwash.vo.users.UserVo;
import jakarta.validation.Valid;

public interface IUsersService extends IService<Users> {

    Page<UserVo> getAllUsers(SearchUserFrom usersFrom);

    Boolean addUsers(AddUserFrom addUsersFrom);

    Boolean updateUser(UpdateUserFrom usersFrom);

    Boolean deleteUsers(String ids);

    Users getUserByPhone(String phoneNumber);

    Users getUserByStudentId(String studentId);

    Users getUserByCampusCard(String campusCard);

    Boolean registerUser(UserRegisterFrom userRegisterFrom);

    Boolean updateUserInfo(@Valid UpdateUserInfo updateUserInfo, Long userId);

    UserInfoVo getUserInfo(Long userId);

    Boolean bindCampus(String campusCard, Long userId);

    Boolean unBindCampus(Long userId);

    Boolean resetPassword(String phoneNumber, String newPassword);

    /**
     * 登录态修改密码：校验原密码通过后更新为新密码
     *
     * @param changePasswordFrom 原密码与新密码（校验已在 Form 层完成）
     * @param userId             当前登录用户 ID
     * @return true=修改成功；false=原密码不正确
     */
    Boolean changePassword(ChangePasswordFrom changePasswordFrom, Long userId);

    /**
     * 交易流水分页查询（充值+支付合并，数据库层分页，评审报告后端 #24）
     *
     * @param userId   用户 ID
     * @param page     页码（从 1 开始，非法值回退 1）
     * @param pageSize 每页条数（默认 10，上限 50）
     */
    Page<TransactionVo> getTransactionHistory(Long userId, Integer page, Integer pageSize);
}
