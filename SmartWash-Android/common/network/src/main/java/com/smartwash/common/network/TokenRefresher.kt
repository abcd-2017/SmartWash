package com.smartwash.common.network

/**
 * 跨模块双 token 续签契约（JWT 双 token 消费侧）：本模块拦截器遇 401 时经此接口
 * 用 refreshToken 换取新的 access/refresh 双 token，实现方为 :feature:user:impl 的
 * TokenRefresherImpl（内部经 SessionManager 读写会话），由其 UserImplModule @Binds 绑定。
 *
 * 线程模型与阻塞语义：ResponseInterceptor 恒在 OkHttp 调度器线程执行（主通道全部为
 * Retrofit suspend 调用 + 专用下载/SSE 客户端均不挂本模块拦截器，无主线程路径），
 * 因此本方法允许在拦截器线程内阻塞——实现内部 runBlocking 挂起链路（读 refreshToken /
 * 持久化新 token 走 DataStore 的独立 IO 线程池），不会阻塞主线程；
 * 续签请求必须走独立裸 OkHttp 客户端，不与主客户端共享 Dispatcher
 * （主客户端 async 并发上限会把 refresh 排在等待者之后，造成饿死死锁）。
 *
 * 单飞语义：并发多个请求同时 401 时，实现须只发起一次刷新请求，其余调用等待并共享
 * 同一结果——refreshToken 为一次性凭据（后端刷新即轮换拉黑旧值），重复刷新会把后续
 * 请求全部打成重放失败。
 */
interface TokenRefresher {

    /**
     * 阻塞刷新 access/refresh 双 token（供拦截器线程调用）。
     *
     * @return true 表示刷新成功且新双 token 已写入会话存储（调用方随后经
     * [TokenProvider.currentToken] 取新 token 重试原请求）；false 表示刷新失败
     * （无 refreshToken / 凭据无效被拒 / 网络异常），调用方应走登录失效处理。
     * 本方法不负责清会话/通知 UI——登录失效统一由 ResponseInterceptor 处理。
     */
    fun refreshBlocking(): Boolean
}
