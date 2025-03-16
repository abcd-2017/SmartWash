package com.smartwash.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwash.common.DefaultConstant;
import com.smartwash.entity.AdminUsers;
import com.smartwash.entity.Users;
import com.smartwash.mapper.AdminUsersMapper;
import com.smartwash.mapper.UsersMapper;
import com.smartwash.utils.LoginUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    @Autowired
    private UsersMapper usersMapper;
    @Autowired
    private AdminUsersMapper adminUsersMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String[] split = username.split("-");
        if (split[0].equals(DefaultConstant.ADMIN_USER_LOGIN_TYPE)) {
            AdminUsers adminUsers = adminUsersMapper.selectOne(new LambdaQueryWrapper<AdminUsers>().eq(AdminUsers::getUsername, split[1]));
            if (adminUsers == null) {
                throw new UsernameNotFoundException("Admin User not found");
            }

            //将信息添加到User也可以说是UserDetails 对象中
            return new LoginUser(adminUsers.getAdminId(), adminUsers.getUsername(), adminUsers.getPasswordHash(),
                    null, split[0], List.of(new SimpleGrantedAuthority(String.format("ROLE_%s", split[0]))));
        } else if (split[0].equals(DefaultConstant.USER_LOGIN_TYPE)) {
            Users user = usersMapper.getUserByPhoneNumber(split[1]);
            if (user == null) {
                throw new UsernameNotFoundException("User not found");
            }

            //将信息添加到User也可以说是UserDetails 对象中
            return new LoginUser(user.getUserId(), null, user.getPassword(),
                    user.getPhoneNumber(), split[0], List.of(new SimpleGrantedAuthority(String.format("ROLE_%s", split[0]))));
        } else {
            throw new UsernameNotFoundException("User not found");
        }
    }

}
