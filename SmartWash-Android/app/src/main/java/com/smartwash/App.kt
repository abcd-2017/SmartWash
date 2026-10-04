package com.smartwash

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.smartwash.common.init.InitEngine
import com.smartwash.common.init.InitTaskRegistry
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject
    lateinit var initTaskRegistry: InitTaskRegistry

    /**
     * @HiltWorker 的 WorkerFactory：ApkDownloadWorker 经 @AssistedInject 实例化，
     * 默认反射工厂找不到两参构造（下载链路不可用）——配合 Manifest 移除默认
     * WorkManagerInitializer，本配置才会被按需初始化采用
     */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /** 应用级协程作用域：进程级生命周期，SupervisorJob 保证子协程异常互不拖垮 */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // 初始化引擎接入：各模块 InitTask 经 @IntoSet 注册自动纳入调度
        //（会话预热 sessionWarmUp 已迁 :feature:user:impl 的 SessionInitTask——阻塞高优先级，
        //  挂起读 DataStore 保证拦截器首请求即可命中 token；另有 feature:update 的启动静默检查）
        applicationScope.launch {
            InitEngine(initTaskRegistry.getTasks()).executeAll()
        }
    }

    /** WorkManager 按需初始化配置：注入 HiltWorkerFactory 供 @HiltWorker 实例化 */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
