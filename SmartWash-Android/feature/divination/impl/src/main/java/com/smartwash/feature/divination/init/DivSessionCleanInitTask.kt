package com.smartwash.feature.divination.init

import com.smartwash.common.init.InitTask
import com.smartwash.common.utils.AppLogger
import com.smartwash.feature.divination.database.DivRecordDao
import com.smartwash.feature.user.api.UserApi
import com.smartwash.feature.user.api.model.LoginEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 观象台本地案卷会话清理任务（P2-7 换账号隔离）：启动期拉起常驻收集，监听 user 域
 * 登录态广播（[UserApi.loginEvents]，feature 间仅 impl→他人 api 铁律的合规消费），
 * 任一会话终止事件（主动登出 LOGOUT / 401 登录失效 UNAUTHORIZED / 未登录拦截
 * NEED_LOGIN，对齐 SessionEventBus→UserApiImpl 桥接口径）即清空 divination_db
 * 本地卦例表——本地库无账号列（不升级 schema、不做破坏性迁移），以「会话终止即清表」
 * 保证换账号登录后旧账号的卦历/首页案卷不再展示。
 *
 * 非阻塞（blocking=false）：execute 启动收集后立即返回；收集协程挂在自持 SupervisorJob
 * scope，不受 InitEngine 单任务超时（withTimeout 只覆盖 execute 本体）影响，存活至进程结束。
 *
 * 残余边界（demo 口径）：进程被杀时无登出事件，本地数据保留，重启后同账号继续展示属预期；
 * 已建档服务端的卦例仍留档服务端，本地未建档记录随清表丢弃。
 */
class DivSessionCleanInitTask(
    private val dao: DivRecordDao,
    private val userApi: UserApi,
) : InitTask() {

    override val taskId: String = "divSessionClean"
    override val priority: Int = 150
    override val blocking: Boolean = false

    /** 收集协程宿主：自持 SupervisorJob，与 InitEngine 的任务超时/取消域隔离 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun execute() {
        scope.launch {
            userApi.loginEvents.collect { event ->
                if (event is LoginEvent.LoggedOut) {
                    try {
                        dao.clearAll()
                        AppLogger.info(TAG, "会话终止（${event.reason}）：观象台本地案卷已清空")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // 清库失败不拖垮事件流（下次会话终止仍会重试），留日志排查
                        AppLogger.warn(TAG, "观象台本地案卷清空失败: ${e.message}")
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "DivSessionCleanInitTask"
    }
}
