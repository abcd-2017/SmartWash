package com.smartwash.service.impl;

import cn.hutool.core.util.DesensitizedUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartwash.common.DefaultConstant;
import com.smartwash.common.PaymentStatus;
import com.smartwash.entity.Schools;
import com.smartwash.entity.Users;
import com.smartwash.config.MinioConfig;
import com.smartwash.exception.CustomExceptions;
import com.smartwash.from.users.*;
import com.smartwash.mapper.UsersMapper;
import com.smartwash.service.ISchoolsService;
import com.smartwash.service.IUsersService;
import com.smartwash.vo.schools.SchoolsVo;
import com.smartwash.vo.users.UserInfoVo;
import com.smartwash.vo.users.TransactionVo;
import com.smartwash.vo.users.UserVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsersServiceImpl extends ServiceImpl<UsersMapper, Users> implements IUsersService {
    private final ISchoolsService schoolsService;
    private final MinioConfig minioConfig;

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
            password = encoder.encode(DefaultConstant.generateDefaultPassword());
        }
        BeanUtils.copyProperties(addUsersFrom, users);
        users.setPassword(password);
        try {
            boolean result = save(users);
            log.info("管理员新增用户, userId: {}", users.getUserId());
            return result;
        } catch (DuplicateKeyException e) {
            // 唯一索引兜底：并发窗口下手机号/学号/校园卡撞车时转友好提示，避免裸 500
            // （正常路径已由 Controller 层逐项查重拦截，此处与 updateUser 对称覆盖并发窗口，评审报告后端 #29）
            log.warn("管理员新增用户命中唯一索引冲突, phone: {}", DesensitizedUtil.mobilePhone(addUsersFrom.getPhoneNumber()));
            throw resolveDuplicateFieldMessage(addUsersFrom);
        }
    }

    /**
     * 新增/更新用户命中唯一索引后，回查定位冲突字段并给出对应友好提示；
     * 回查也未命中（如刚被删除）时返回通用兜底提示
     */
    private CustomExceptions resolveDuplicateFieldMessage(AddUserFrom addUsersFrom) {
        if (StringUtils.hasText(addUsersFrom.getPhoneNumber()) && getUserByPhone(addUsersFrom.getPhoneNumber()) != null) {
            return new CustomExceptions("该手机号已被注册");
        }
        if (StringUtils.hasText(addUsersFrom.getStudentId()) && getUserByStudentId(addUsersFrom.getStudentId()) != null) {
            return new CustomExceptions("该学号已注册账号");
        }
        if (StringUtils.hasText(addUsersFrom.getCampusCard()) && getUserByCampusCard(addUsersFrom.getCampusCard()) != null) {
            return new CustomExceptions("该校园卡已绑定账号");
        }
        return new CustomExceptions("保存失败：手机号/学号/校园卡已被其他账号使用");
    }

    @Override
    public Boolean updateUser(UpdateUserFrom usersFrom) {
        log.info("更新用户信息, userId: {}", usersFrom.getUserId());
        Users users = getById(usersFrom.getUserId());
        if (users == null) {
            throw new CustomExceptions("用户不存在");
        }
        BeanUtils.copyProperties(usersFrom, users);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        if (StringUtils.hasText(usersFrom.getPassword())) {
            users.setPassword(encoder.encode(usersFrom.getPassword()));
        }
        try {
            return updateById(users);
        } catch (DuplicateKeyException e) {
            // 唯一索引兜底：手机号/学号/校园卡与并发更新撞车时转友好提示，避免裸 500
            // （正常路径已由 Controller 层逐项查重拦截，此处仅覆盖并发窗口，评审报告后端 #29）
            log.warn("更新用户命中唯一索引冲突, userId: {}", usersFrom.getUserId());
            throw new CustomExceptions("保存失败：手机号/学号/校园卡已被其他账号使用");
        }
    }

    @Override
    public Boolean deleteUsers(String ids) {
        log.info("删除用户, ids: {}", ids);
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::valueOf)
                .collect(Collectors.toList());
        return removeByIds(idList);
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
        // 检查手机号是否已注册
        Users existingUser = getUserByPhone(userRegisterFrom.getPhoneNumber());
        if (existingUser != null) {
            throw new CustomExceptions("该手机号已注册");
        }

        Users users = new Users();
        users.setPhoneNumber(userRegisterFrom.getPhoneNumber());
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        users.setPassword(encoder.encode(userRegisterFrom.getPassword()));
        users.setAvatar(minioConfig.getEndpoint() + "/" + minioConfig.getBucketName() + "/default/avatar.png");
        boolean result = save(users);
        log.info("用户注册成功, userId: {}, phone: {}", users.getUserId(), DesensitizedUtil.mobilePhone(userRegisterFrom.getPhoneNumber()));
        return result;
    }

    @Override
    public Boolean updateUserInfo(UpdateUserInfo updateUserInfo, Long userId) {
        log.info("用户更新个人信息, userId: {}", userId);
        Users user = getById(userId);
        if (user == null) {
            throw new CustomExceptions("用户不存在");
        }
        user.setSchoolId(updateUserInfo.getSchoolId());
        user.setStudentId(updateUserInfo.getStudentId());
        return updateById(user);
    }

    @Override
    public UserInfoVo getUserInfo(Long userId) {
        Users users = getById(userId);
        if (users == null) {
            throw new CustomExceptions("用户不存在");
        }
        UserInfoVo userInfoVo = new UserInfoVo();
        BeanUtils.copyProperties(users, userInfoVo);
        userInfoVo.setPhoneNumber(DesensitizedUtil.mobilePhone(users.getPhoneNumber()));
        userInfoVo.setBalance(users.getBalance() != null ? users.getBalance() : BigDecimal.ZERO);
        SchoolsVo schoolsVo = new SchoolsVo();
        Schools school = schoolsService.getById(users.getSchoolId());
        if (school != null) {
            BeanUtils.copyProperties(school, schoolsVo);
        }
        userInfoVo.setSchoolVo(schoolsVo);
        return userInfoVo;
    }

    @Override
    public Boolean bingCampus(String campusCard, Long userId) {
        log.info("用户绑定校园卡, userId: {}", userId);
        // 绑定前查重：同一张校园卡只允许绑定一个账号，已被他人绑定则拒绝（评审报告后端 #15）
        Users boundUser = getUserByCampusCard(campusCard);
        if (boundUser != null && !Objects.equals(boundUser.getUserId(), userId)) {
            log.warn("校园卡绑定被拒绝：该卡已绑定其他账号, userId: {}, boundUserId: {}", userId, boundUser.getUserId());
            throw new CustomExceptions("该校园卡已被其他账号绑定");
        }
        LambdaUpdateWrapper<Users> updateWrapper = new LambdaUpdateWrapper<Users>().eq(Users::getUserId, userId).set(Users::getCampusCard, campusCard);
        try {
            return update(updateWrapper);
        } catch (DuplicateKeyException e) {
            // V7 迁移 uk_users_campus_card 唯一索引兜底：两个用户并发绑定同一张卡时第二方失败
            log.warn("校园卡并发绑定命中唯一索引, userId: {}", userId);
            throw new CustomExceptions("该校园卡已被其他账号绑定");
        }
    }

    @Override
    public Boolean unBingCampus(Long userId) {
        log.info("用户解绑校园卡, userId: {}", userId);
        return update(new LambdaUpdateWrapper<Users>().eq(Users::getUserId, userId).set(Users::getCampusCard, null));
    }

    @Override
    public Boolean resetPassword(String phoneNumber, String newPassword) {
        Users user = getUserByPhone(phoneNumber);
        if (user == null) {
            return false;
        }
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String encodedPassword = encoder.encode(newPassword);
        log.info("用户密码重置, userId: {}", user.getUserId());
        return update(new LambdaUpdateWrapper<Users>()
                .eq(Users::getUserId, user.getUserId())
                .set(Users::getPassword, encodedPassword));
    }

    /** 交易流水分页默认每页条数 */
    private static final int TRANSACTION_DEFAULT_PAGE_SIZE = 10;
    /** 交易流水分页每页条数上限：防止超大 pageSize 变相全量拉取（评审报告后端 #24） */
    private static final int TRANSACTION_MAX_PAGE_SIZE = 50;

    @Override
    public Page<TransactionVo> getTransactionHistory(Long userId, Integer page, Integer pageSize) {
        // 分页参数兜底：页码非法回退 1，每页条数非法回退默认值并封顶 50
        int current = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1)
                ? TRANSACTION_DEFAULT_PAGE_SIZE
                : Math.min(pageSize, TRANSACTION_MAX_PAGE_SIZE);
        // 消费流水仅统计已支付状态（口径与原实现一致，状态码走 PaymentStatus 枚举，避免魔法值）
        String successStatus = PaymentStatus.SUCCESS.getStatus();

        Page<TransactionVo> result = new Page<>(current, size);
        long total = baseMapper.countTransactionHistory(userId, successStatus);
        result.setTotal(total);
        if (total == 0) {
            // 无流水时不再下探分页查询，直接返回空记录
            result.setRecords(Collections.emptyList());
            return result;
        }
        // 合并排序、截取全部下沉到数据库（UNION ALL + LIMIT/OFFSET），不再把两表全量拉进内存
        long offset = (long) (current - 1) * size;
        result.setRecords(baseMapper.selectTransactionPage(userId, successStatus, offset, size));
        return result;
    }
}
