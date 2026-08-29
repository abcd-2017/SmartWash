package com.smartwash.filter;

import com.smartwash.common.Result;
import com.smartwash.service.JwtBlacklistService;
import com.smartwash.utils.JwtUtil;
import com.smartwash.utils.LoginUser;
import com.smartwash.utils.UserContextHolder;
import io.jsonwebtoken.Claims;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * JwtAuthenticationFilter 401 语义回归测试（评审报告后端 P0 #10）。
 * 闸门语义：受保护路径（/admin/**、/web/auth/**）token 缺失/无效/已拉黑/用户不存在时，
 * 必须直接写 HTTP 401 + 统一信封 {code:401,...} 并终止过滤器链，不得向下游抛 RuntimeException（否则变 500）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter 401 语义测试")
class JwtAuthenticationFilterTest {

    private static final String TEST_SECRET = "dGVzdFNlY3JldEtleUZvckpXVFRlc3RpbmdQdXJwb3Nlcw==";

    private JwtUtil jwtUtil;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private JwtBlacklistService jwtBlacklistService;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_SECRET);
        filter = new JwtAuthenticationFilter(jwtUtil, userDetailsService, jwtBlacklistService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        UserContextHolder.clear();
    }

    private MockHttpServletResponse pass(MockHttpServletRequest request, MockFilterChain chain)
            throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private MockHttpServletRequest protectedRequest() {
        return new MockHttpServletRequest("GET", "/admin/users/list");
    }

    private LoginUser loginUser() {
        return new LoginUser(1L, "user-13800138000", "pwd", "13800138000", "user",
                List.of(() -> "ROLE_user"));
    }

    @Test
    @DisplayName("token 缺失访问受保护路径：返回 401 且 body 含 code=401，过滤器链终止")
    void missingToken_returns401EnvelopeAndStopsChain() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = pass(protectedRequest(), chain);

        assertEquals(401, response.getStatus(), "受保护路径无 token 必须返回 401（而非 500）");
        assertEquals("application/json;charset=UTF-8", response.getContentType());
        assertTrue(response.getContentAsString().contains("\"code\":401"), "响应体必须是统一信封且 code=401，实际：" + response.getContentAsString());
        assertNull(chain.getRequest(), "认证失败后不得继续执行过滤器链");
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("Bearer 前缀但 token 为空：同样按缺失处理返回 401")
    void emptyBearerToken_returns401() throws Exception {
        MockHttpServletRequest request = protectedRequest();
        request.addHeader("Authorization", "Bearer ");
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = pass(request, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("伪造/非法 token：返回 401 且不继续过滤器链")
    void invalidToken_returns401AndStopsChain() throws Exception {
        MockHttpServletRequest request = protectedRequest();
        request.addHeader("Authorization", "Bearer invalid.token.here");
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = pass(request, chain);

        assertEquals(401, response.getStatus(), "非法 token 必须返回 401（修复前抛 RuntimeException 变 500）");
        assertTrue(response.getContentAsString().contains("\"code\":401"));
        assertNull(chain.getRequest());
        verifyNoInteractions(jwtBlacklistService);
    }

    @Test
    @DisplayName("token 有效但 jti 已在登出黑名单：返回 401 并终止链")
    void blacklistedToken_returns401() throws Exception {
        LoginUser user = loginUser();
        String token = jwtUtil.generatorToken(user.getUsername());
        Claims claims = jwtUtil.getPayloadFromToken(token);
        // 黑名单校验在用户加载之前，此处不得 stub loadUserByUsername（否则产生无用桩）
        when(jwtBlacklistService.isBlacklisted(claims.get("jti", String.class))).thenReturn(true);

        MockHttpServletRequest request = protectedRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = pass(request, chain);

        assertEquals(401, response.getStatus(), "已登出（黑名单）token 必须被拒绝");
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("token 有效且用户存在：继续过滤器链，设置认证上下文，链结束后清理 UserContextHolder")
    void validToken_proceedsChainAndSetsContext() throws Exception {
        LoginUser user = loginUser();
        String token = jwtUtil.generatorToken(user.getUsername());
        when(userDetailsService.loadUserByUsername(user.getUsername())).thenReturn(user);
        when(jwtBlacklistService.isBlacklisted(anyString())).thenReturn(false);

        MockHttpServletRequest request = protectedRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = pass(request, chain);

        assertEquals(200, response.getStatus(), "有效 token 应放行（默认响应 200）");
        assertNotNull(chain.getRequest(), "认证通过必须继续过滤器链");
        assertNotNull(SecurityContextHolder.getContext().getAuthentication(), "认证通过必须设置 SecurityContext");
        assertEquals(1L, ((LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUserId());
        assertNull(UserContextHolder.getUser(), "链结束后 finally 必须清理 ThreadLocal 用户上下文，防止线程池串号");
    }

    @Test
    @DisplayName("token 对应用户不存在（已注销）：返回 401 并终止链")
    void unknownUser_returns401() throws Exception {
        String token = jwtUtil.generatorToken("user-13800138000");
        when(userDetailsService.loadUserByUsername("user-13800138000")).thenThrow(new UsernameNotFoundException("用户不存在"));

        MockHttpServletRequest request = protectedRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = pass(request, chain);

        assertEquals(401, response.getStatus(), "用户加载失败必须收敛为 401，不得向上抛异常");
        assertNull(chain.getRequest());
    }

    @Test
    @DisplayName("公开路径（/auth/**）无 token：直接放行，不做认证校验")
    void publicPath_proceedsWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/user/login");
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = pass(request, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest(), "公开路径无 token 也必须放行");
        verifyNoInteractions(userDetailsService);
        verifyNoInteractions(jwtBlacklistService);
    }
}
