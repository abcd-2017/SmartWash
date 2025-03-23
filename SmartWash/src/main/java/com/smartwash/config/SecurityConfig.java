package com.smartwash.config;

import com.smartwash.common.DefaultConstant;
import com.smartwash.filter.JwtAuthenticationFilter;
import com.smartwash.service.impl.CustomUserDetailsService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @Resource
    private CustomUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationEntryPoint authenticationEntryPoint) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())  // 启用 CORS
            .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint)) // 使用自定义异常处理器
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/admin/**").hasRole(DefaultConstant.ADMIN_USER_LOGIN_TYPE) // "ADMIN" -> "ROLE_ADMIN"
                    .requestMatchers("/web/auth/**").hasRole(DefaultConstant.USER_LOGIN_TYPE)       // "USER" -> "ROLE_USER"
                    .requestMatchers("/auth/**", "/web/**").permitAll() // 公开接口
                    .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .userDetailsService(userDetailsService) //配置自定义的 UserDetailsService 用于加载用户信息
            .formLogin(AbstractHttpConfigurer::disable) // Spring Security 默认启用基于表单的登录认证。如果你使用的是前后端分离的方式，通常不会使用传统的表单登录，因此禁用它。
            .logout(LogoutConfigurer::permitAll); // 配置登出功能，允许所有用户进行登出。

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
        authenticationProvider.setUserDetailsService(userDetailsService); // 配置 UserDetailsService，用于加载用户信息
        authenticationProvider.setPasswordEncoder(passwordEncoder); // 配置密码编码器，用于密码校验

        return new ProviderManager(authenticationProvider); // 返回一个 ProviderManager，管理多个认证提供者
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}