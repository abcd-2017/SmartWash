package com.smartwash.common.network

/**
 * 跨模块 token 供给契约：本模块拦截器经此接口取/清 token，
 * 实现方为 :feature:user:impl 的 SessionManager，由其 UserImplModule @Binds 绑定。
 */
interface TokenProvider {

    /** 同步读取当前 token（供拦截器使用，无 IO）；未登录/未预热完成时返回空串或 null */
    fun currentToken(): String?

    /** 清除会话（401 登录失效时调用）；实现须幂等，避免并发 401 重复清 token */
    fun clearToken()
}
