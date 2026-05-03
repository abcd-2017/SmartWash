package com.smartwash.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwash.common.DefaultConstant;
import com.smartwash.entity.AdminUsers;
import com.smartwash.entity.Users;
import com.smartwash.mapper.AdminUsersMapper;
import com.smartwash.mapper.UsersMapper;
import com.smartwash.utils.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UsersMapper usersMapper;
    private final AdminUsersMapper adminUsersMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String[] split = username.split("-");
        if (split[0].equals(DefaultConstant.ADMIN_USER_LOGIN_TYPE)) {
            AdminUsers adminUsers = adminUsersMapper.selectOne(new LambdaQueryWrapper<AdminUsers>().eq(AdminUsers::getUsername, split[1]));
            if (adminUsers == null) {
                log.warn("管理员认证失败：用户名不存在, username: {}", split[1]);
                throw new UsernameNotFoundException("Admin User not found");
            }
            log.info("管理员认证成功, username: {}", split[1]);
            //将信息添加到User也可以说是UserDetails 对象中
            return new LoginUser(adminUsers.getAdminId(), adminUsers.getUsername(), adminUsers.getPasswordHash(),
                    null, split[0], List.of(new SimpleGrantedAuthority(String.format("ROLE_%s", split[0]))));
        } else if (split[0].equals(DefaultConstant.USER_LOGIN_TYPE)) {
            Users user = usersMapper.getUserByPhoneNumber(split[1]);
            if (user == null) {
                log.warn("用户认证失败：手机号未注册, phone: {}", split[1].replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
                throw new UsernameNotFoundException("User not found");
            }
            log.info("用户认证成功, phone: {}", split[1].replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
            //将信息添加到User也可以说是UserDetails 对象中
            return new LoginUser(user.getUserId(), null, user.getPassword(),
                    user.getPhoneNumber(), split[0], List.of(new SimpleGrantedAuthority(String.format("ROLE_%s", split[0]))));
        } else {
            log.warn("认证格式错误: {}", username);
            throw new UsernameNotFoundException("User not found");
        }
    }

}
