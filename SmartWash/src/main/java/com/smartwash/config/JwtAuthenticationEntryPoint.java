package com.smartwash.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwash.common.Result;
import com.smartwash.common.ResultCodeEnum;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, IOException {
        log.warn(authException.getMessage());
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // 自定义返回 JSON 结构
        Result<String> result = Result.build(null, ResultCodeEnum.UNAUTHORIZED);
        response.getWriter().write(new ObjectMapper().writeValueAsString(result));
    }
}
