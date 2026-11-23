package com.smartwash.feature.user.impl.repository

import com.smartwash.common.network.exception.NetworkException
import com.smartwash.common.utils.model.ApiResult
import com.smartwash.feature.user.impl.FakeUserAccountApi
import com.smartwash.feature.user.impl.network.entity.user.LoginUser
import com.smartwash.feature.user.impl.network.entity.user.RegisterUser
import com.smartwash.feature.user.impl.network.vo.user.LoginVo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用户域数据仓库 JVM 单测（纯 JVM，UserRepository 无日志无 Context 依赖）。
 *
 * 锚点契约：登录/注册响应 data 缺失按失败处理上抛 NetworkException（不静默返回）；
 * 学号查重/校园卡绑定类布尔端点 data 缺失按 false 处理（不抛）；getUserSchoolId
 * data 缺失落 -1（对齐「未绑定学校」语义）。
 */
class UserRepositoryTest {

    private fun repo(api: FakeUserAccountApi) = UserRepository(api)

    // ==================== 登录 / 注册（注册即自动登录契约） ====================

    @Test
    fun `登录成功透传 LoginVo 双 token 与 role`() = runBlocking {
        val api = FakeUserAccountApi(
            loginResult = ApiResult(200, "成功", LoginVo(token = "jwt", refreshToken = "r", role = "user")),
        )
        val vo = repo(api).login(LoginUser(phoneNumber = "13800000000", password = "p"))
        assertEquals("jwt", vo.token)
        assertEquals("r", vo.refreshToken)
        assertEquals("user", vo.role)
        assertEquals(1, api.loginCalls)
    }

    @Test
    fun `登录响应 data 缺失上抛 NetworkException`() {
        val api = FakeUserAccountApi(loginResult = ApiResult(200, "成功", null))
        val ex = assertThrows(NetworkException::class.java) {
            runBlocking { repo(api).login(LoginUser(phoneNumber = "13800000000", password = "p")) }
        }
        assertEquals("登录响应数据为空", ex.message)
    }

    @Test
    fun `注册响应 data 缺失上抛 NetworkException（注册即自动登录结构）`() {
        val api = FakeUserAccountApi(registerResult = ApiResult(200, "成功", null))
        val ex = assertThrows(NetworkException::class.java) {
            runBlocking { repo(api).register(RegisterUser(phoneNumber = "13800000000", password = "p", code = "1234")) }
        }
        assertEquals("注册响应数据为空", ex.message)
    }

    @Test
    fun `注册成功透传 LoginVo`() = runBlocking {
        val api = FakeUserAccountApi(
            registerResult = ApiResult(200, "成功", LoginVo(token = "t", refreshToken = "rt", role = "user")),
        )
        val vo = repo(api).register(RegisterUser(phoneNumber = "13800000000", password = "p", code = "1234"))
        assertEquals("t", vo.token)
        assertEquals(1, api.registerCalls)
    }

    // ==================== 用户信息 ====================

    @Test
    fun `getUserInfo 响应 data 缺失上抛 NetworkException`() {
        val api = FakeUserAccountApi(userInfoResult = ApiResult(200, "成功", null))
        val ex = assertThrows(NetworkException::class.java) {
            runBlocking { repo(api).getUserInfo() }
        }
        assertEquals("用户信息为空", ex.message)
    }

    // ==================== 布尔/标量端点的空值降级 ====================

    @Test
    fun `getUserSchoolId data 缺失落 -1`() = runBlocking {
        val api = FakeUserAccountApi(schoolIdResult = ApiResult(200, "成功", null))
        assertEquals(-1L, repo(api).getUserSchoolId())
        val api2 = FakeUserAccountApi(schoolIdResult = ApiResult(200, "成功", 7L))
        assertEquals(7L, repo(api2).getUserSchoolId())
        Unit
    }

    @Test
    fun `学号查重 data 缺失按 false 不抛`() = runBlocking {
        val api = FakeUserAccountApi(studentIdResult = ApiResult(200, "成功", null))
        assertFalse(repo(api).getUserByStudentId("20260001"))
        val api2 = FakeUserAccountApi(studentIdResult = ApiResult(200, "成功", true))
        assertTrue(repo(api2).getUserByStudentId("20260001"))
        Unit
    }

    @Test
    fun `校园卡绑定 data 缺失按 false 不抛`() = runBlocking {
        val api = FakeUserAccountApi(bindCampusResult = ApiResult(200, "成功", null))
        assertFalse(repo(api).bindCampus("CARD-001"))
        val api2 = FakeUserAccountApi(bindCampusResult = ApiResult(200, "成功", true))
        assertTrue(repo(api2).bindCampus("CARD-001"))
        Unit
    }
}
