package com.smartwash.ui.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.smartwash.utils.HapticEffect
import com.smartwash.utils.defaultSpring
import com.smartwash.utils.isReduceMotionEnabled
import com.smartwash.utils.momentumSpring
import com.smartwash.utils.performHaptic
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smartwash.R
import com.smartwash.network.session.SessionEvent
import com.smartwash.network.session.SessionEventBus
import com.smartwash.network.session.SessionManager
import com.smartwash.ui.page.PageConstant
import com.smartwash.divination.ui.page.ask.DivAskPage
import com.smartwash.divination.ui.page.cast.DivCastPage
import com.smartwash.divination.ui.page.chart.DivChartPage
import com.smartwash.divination.ui.page.followup.DivFollowUpPage
import com.smartwash.divination.ui.page.history.DivHistoryPage
import com.smartwash.divination.ui.page.home.DivHomePage
import com.smartwash.divination.ui.page.reading.DivReadingPage
import com.smartwash.ui.page.coupon.CouponPage
import com.smartwash.ui.page.detail.OrderDetailPage
import com.smartwash.ui.page.home.HomePage
import com.smartwash.ui.page.laundry.LaundryPage
import com.smartwash.ui.page.login.LoginPage
import com.smartwash.ui.page.order.OrderPage
import com.smartwash.ui.page.payment.PaySuccessPage
import com.smartwash.ui.page.payment.PaymentPage
import com.smartwash.ui.page.pickup.PickupDeliveryPage
import com.smartwash.ui.page.pickup.PickupPage
import com.smartwash.ui.page.recharge.RechargePage
import com.smartwash.ui.page.recharge.RechargeRecordPage
import com.smartwash.ui.page.register.RegisterPage
import com.smartwash.ui.page.service.ServicePage
import com.smartwash.ui.page.setting.SettingPage
import com.smartwash.ui.page.update.UpdateState
import com.smartwash.ui.page.update.UpdateViewModel
import com.smartwash.ui.page.update.UpdateAvailableDialog
import com.smartwash.ui.page.update.DownloadProgressDialog
import com.smartwash.ui.page.update.DownloadCompleteDialog
import com.smartwash.ui.page.update.ForceUpdateRequiredDialog
import com.smartwash.ui.page.update_userinfo.UpdateUserInfoPage
import com.smartwash.service.ApkDownloadWorker
import com.smartwash.service.ApkInstaller
import com.smartwash.ui.theme.SmartWashAndroidTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionEventBus: SessionEventBus
    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var workManager: WorkManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            val context = LocalContext.current
            val view = LocalView.current
            val reduceMotion = isReduceMotionEnabled(context)

            // 更新 ViewModel
            val updateViewModel: UpdateViewModel = viewModel()
            val updateState by updateViewModel.state.collectAsState()

            // 启动时延迟静默检查更新（等主 UI 渲染完）
            LaunchedEffect(Unit) {
                delay(1500)
                updateViewModel.checkForUpdate(silent = true)
            }

            // 下载中的 WorkManager 进度监听（用户点「立即更新」后由 enqueueApkDownload 入队）
            // 声明必须早于下方 LaunchedEffect(updateViewModel) 的首次使用
            val currentDownloadWorkId = remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

            // 监听预签名下载地址：获取成功后调度 Worker 开始下载
            LaunchedEffect(updateViewModel) {
                updateViewModel.downloadUrl.collect { url ->
                    if (url != null) {
                        val version = updateViewModel.getLatestVersion() ?: return@collect
                        enqueueApkDownload(context, workManager, url, version, currentDownloadWorkId)
                        updateViewModel.consumeDownloadUrl()
                    }
                }
            }

            LaunchedEffect(currentDownloadWorkId.value) {
                val workId = currentDownloadWorkId.value ?: return@LaunchedEffect
                val uuid = try { java.util.UUID.fromString(workId) } catch (_: Exception) { return@LaunchedEffect }
                workManager.getWorkInfoByIdFlow(uuid).collect { workInfo ->
                    when (workInfo?.state) {
                        WorkInfo.State.RUNNING -> {
                            val progress = workInfo.progress.getInt(ApkDownloadWorker.KEY_PROGRESS, 0)
                            updateViewModel.onDownloadProgress(progress)
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            val apkPath = workInfo.outputData.getString(ApkDownloadWorker.KEY_APK_PATH)
                            if (apkPath != null) {
                                updateViewModel.onDownloadComplete(java.io.File(apkPath))
                            } else {
                                updateViewModel.onDownloadFailed(context.getString(R.string.download_failed))
                            }
                            currentDownloadWorkId.value = null
                        }
                        WorkInfo.State.FAILED -> {
                            val error = workInfo.outputData.getString(ApkDownloadWorker.KEY_ERROR)
                            updateViewModel.onDownloadFailed(error ?: context.getString(R.string.download_failed))
                            currentDownloadWorkId.value = null
                        }
                        WorkInfo.State.CANCELLED -> {
                            updateViewModel.reset()
                            currentDownloadWorkId.value = null
                        }
                        else -> {}
                    }
                }
            }

            // 更新弹窗 UI — 根据状态展示对应弹窗
            val currentState = updateState
            when (currentState) {
                is UpdateState.UpdateAvailable -> {
                    val version = currentState.version
                    if (version.forceUpdate) {
                        ForceUpdateRequiredDialog(
                            onUpdateNow = {
                                updateViewModel.startDownload(context)
                            }
                        )
                    } else {
                        UpdateAvailableDialog(
                            version = version,
                            onUpdateNow = {
                                updateViewModel.startDownload(context)
                            },
                            onUpdateLater = { updateViewModel.reset() }
                        )
                    }
                }
                is UpdateState.Downloading -> {
                    DownloadProgressDialog(
                        progress = currentState.progress,
                        downloadedBytes = 0L,
                        totalBytes = (updateViewModel.getLatestVersion()?.fileSize ?: 0L),
                        onCancel = {
                            currentDownloadWorkId.value?.let { id ->
                                try { workManager.cancelWorkById(java.util.UUID.fromString(id)) } catch (_: Exception) {}
                            }
                            updateViewModel.reset()
                        },
                    )
                }
                is UpdateState.Downloaded -> {
                    DownloadCompleteDialog(
                        onInstallNow = {
                            handleApkInstall(context, updateViewModel, currentState.file)
                        },
                        onInstallLater = { updateViewModel.reset() },
                    )
                }
                else -> { /* Idle / Checking / LatestVersion / Error / Installing 不弹窗 */ }
            }

            // 收集网络层会话事件：未登录拦截 / 401 登录失效 → 统一跳登录页。
            // 事件总线侧已去重 + navigate 加 launchSingleTop，避免连发 401 堆叠多个登录页；
            // token 清理由拦截器经 SessionManager 幂等处理，这里不再重复清。
            LaunchedEffect(sessionEventBus) {
                sessionEventBus.events.collect { event ->
                    when (event) {
                        SessionEvent.NeedLogin -> {
                            Toast.makeText(
                                context,
                                context.getString(R.string.please_login),
                                Toast.LENGTH_SHORT
                            ).show()
                            navController.popBackStack()
                            navController.navigate(PageConstant.Login.text) {
                                launchSingleTop = true
                            }
                        }

                        SessionEvent.Unauthorized -> {
                            view.performHaptic(HapticEffect.ERROR)
                            delay(200)
                            Toast.makeText(
                                context,
                                context.getString(R.string.please_re_login),
                                Toast.LENGTH_SHORT
                            ).show()
                            navController.popBackStack()
                            navController.navigate(PageConstant.Login.text) {
                                launchSingleTop = true
                            }
                        }
                    }
                }
            }

            SmartWashAndroidTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = PageConstant.Login.text,
                        enterTransition = {
                            if (reduceMotion) {
                                fadeIn(animationSpec = tween(200))
                            } else {
                                fadeIn(animationSpec = defaultSpring()) + scaleIn(
                                    initialScale = 0.95f,
                                    animationSpec = defaultSpring()
                                )
                            }
                        },
                        exitTransition = {
                            if (reduceMotion) {
                                fadeOut(animationSpec = tween(200))
                            } else {
                                fadeOut(animationSpec = defaultSpring()) + scaleOut(
                                    targetScale = 0.95f,
                                    animationSpec = defaultSpring()
                                )
                            }
                        },
                        popEnterTransition = {
                            if (reduceMotion) {
                                fadeIn(animationSpec = tween(200))
                            } else {
                                fadeIn(animationSpec = defaultSpring()) + scaleIn(
                                    initialScale = 0.95f,
                                    animationSpec = defaultSpring()
                                )
                            }
                        },
                        popExitTransition = {
                            if (reduceMotion) {
                                fadeOut(animationSpec = tween(200))
                            } else {
                                fadeOut(animationSpec = defaultSpring()) + scaleOut(
                                    targetScale = 0.95f,
                                    animationSpec = defaultSpring()
                                )
                            }
                        }
                    ) {
                        composable(PageConstant.Login.text) {
                            LoginPage(navController, sessionManager)
                        }
                        composable(PageConstant.Register.text) {
                            RegisterPage(navController)
                        }
                        composable(PageConstant.Home.text) {
                            HomePage(navController)
                        }
                        composable(PageConstant.UpdateUserInfoPage.text) {
                            UpdateUserInfoPage(navController)
                        }
                        composable(PageConstant.Setting.text) {
                            SettingPage(navController, sessionManager)
                        }
                        composable(PageConstant.Recharge.text) {
                            RechargePage(navController)
                        }
                        composable(PageConstant.RechargeRecord.text) {
                            RechargeRecordPage(navController)
                        }
                        composable(
                            route = "${PageConstant.Order.text}/{itemId}", arguments = listOf(
                                navArgument("itemId") {
                                    type = NavType.IntType
                                    defaultValue = 0
                                }
                            )
                        ) { entity ->
                            OrderPage(navController, entity.arguments?.getInt("itemId") ?: 0)
                        }
                        composable(
                            route = "${PageConstant.OrderDetail.text}/{orderId}",
                            arguments = listOf(
                                navArgument("orderId") {
                                    type = NavType.LongType
                                    defaultValue = -1
                                }
                            )
                        ) { entity ->
                            OrderDetailPage(
                                navController,
                                entity.arguments?.getLong("orderId") ?: -1
                            )
                        }
                        composable(PageConstant.Service.text) {
                            ServicePage()
                        }
                        composable(
                            route = "${PageConstant.Payment.text}/{orderId}",
                            arguments = listOf(
                                navArgument("orderId") {
                                    type = NavType.LongType
                                    defaultValue = -1
                                }
                            ),
                            enterTransition = {
                                slideInVertically(
                                    initialOffsetY = { (it * 0.3f).toInt() },
                                    animationSpec = if (reduceMotion) tween(250) else momentumSpring()
                                ) + fadeIn(animationSpec = if (reduceMotion) tween(250) else momentumSpring())
                            },
                            exitTransition = {
                                slideOutVertically(
                                    targetOffsetY = { (it * 0.3f).toInt() },
                                    animationSpec = if (reduceMotion) tween(250) else momentumSpring()
                                ) + fadeOut(animationSpec = if (reduceMotion) tween(250) else momentumSpring())
                            },
                            popEnterTransition = {
                                slideInVertically(
                                    initialOffsetY = { (it * 0.3f).toInt() },
                                    animationSpec = if (reduceMotion) tween(250) else momentumSpring()
                                ) + fadeIn(animationSpec = if (reduceMotion) tween(250) else momentumSpring())
                            },
                            popExitTransition = {
                                slideOutVertically(
                                    targetOffsetY = { (it * 0.3f).toInt() },
                                    animationSpec = if (reduceMotion) tween(250) else momentumSpring()
                                ) + fadeOut(animationSpec = if (reduceMotion) tween(250) else momentumSpring())
                            }
                        ) { entity ->
                            PaymentPage(navController, entity.arguments?.getLong("orderId"))
                        }
                        composable(PageConstant.Laundry.text) {
                            LaundryPage(navController)
                        }
                        composable(
                            route = "${PageConstant.PaySuccess.text}/{orderId}",
                            arguments = listOf(
                                navArgument("orderId") {
                                    type = NavType.LongType
                                    defaultValue = -1
                                }
                            ),
                            enterTransition = {
                                slideInVertically(
                                    initialOffsetY = { (it * 0.3f).toInt() },
                                    animationSpec = if (reduceMotion) tween(250) else momentumSpring()
                                ) + fadeIn(animationSpec = if (reduceMotion) tween(250) else momentumSpring())
                            },
                            exitTransition = {
                                slideOutVertically(
                                    targetOffsetY = { (it * 0.3f).toInt() },
                                    animationSpec = if (reduceMotion) tween(250) else momentumSpring()
                                ) + fadeOut(animationSpec = if (reduceMotion) tween(250) else momentumSpring())
                            },
                            popEnterTransition = {
                                slideInVertically(
                                    initialOffsetY = { (it * 0.3f).toInt() },
                                    animationSpec = if (reduceMotion) tween(250) else momentumSpring()
                                ) + fadeIn(animationSpec = if (reduceMotion) tween(250) else momentumSpring())
                            },
                            popExitTransition = {
                                slideOutVertically(
                                    targetOffsetY = { (it * 0.3f).toInt() },
                                    animationSpec = if (reduceMotion) tween(250) else momentumSpring()
                                ) + fadeOut(animationSpec = if (reduceMotion) tween(250) else momentumSpring())
                            }
                        ) { entity ->
                            PaySuccessPage(
                                navController,
                                entity.arguments?.getLong("orderId") ?: -1
                            )
                        }
                        composable(
                            route = "${PageConstant.PickupDelivery.text}/{orderId}/{pickupType}",
                            arguments = listOf(
                                navArgument("orderId") {
                                    type = NavType.LongType
                                    defaultValue = -1L
                                }, navArgument("pickupType") {
                                    type = NavType.IntType
                                    defaultValue = 0
                                }
                            )
                        ) { entity ->
                            PickupDeliveryPage(
                                entity.arguments?.getInt("pickupType") ?: 0,
                                navController,
                                entity.arguments?.getLong("orderId") ?: -1L
                            )
                        }
                        composable(PageConstant.Coupon.text) {
                            CouponPage(navController)
                        }
                        composable(PageConstant.Pickup.text) {
                            PickupPage(navController)
                        }
                        // ==================== 观象台（占卜模块，追加） ====================
                        composable(PageConstant.DivHome.text) {
                            DivHomePage(navController)
                        }
                        composable(
                            route = "${PageConstant.DivAsk.text}?method={method}",
                            arguments = listOf(
                                navArgument("method") {
                                    type = NavType.StringType
                                    defaultValue = "liuyao"
                                }
                            )
                        ) { entity ->
                            DivAskPage(navController, entity.arguments?.getString("method") ?: "liuyao")
                        }
                        composable(
                            route = "${PageConstant.DivCast.text}?question={question}&category={category}",
                            arguments = listOf(
                                navArgument("question") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("category") {
                                    type = NavType.StringType
                                    defaultValue = "other"
                                }
                            )
                        ) { entity ->
                            DivCastPage(
                                navController,
                                question = entity.arguments?.getString("question").orEmpty(),
                                categoryId = entity.arguments?.getString("category") ?: "other",
                            )
                        }
                        composable(
                            route = "${PageConstant.DivChart.text}/{recordId}?animate={animate}",
                            arguments = listOf(
                                navArgument("recordId") {
                                    type = NavType.LongType
                                },
                                navArgument("animate") {
                                    type = NavType.BoolType
                                    defaultValue = false
                                }
                            )
                        ) { entity ->
                            DivChartPage(
                                navController,
                                recordId = entity.arguments?.getLong("recordId") ?: -1L,
                                animateEntry = entity.arguments?.getBoolean("animate") ?: false,
                            )
                        }
                        composable(PageConstant.DivHistory.text) {
                            DivHistoryPage(navController)
                        }
                        composable(
                            route = "${PageConstant.DivReading.text}/{recordId}",
                            arguments = listOf(
                                navArgument("recordId") {
                                    type = NavType.LongType
                                }
                            )
                        ) { entity ->
                            DivReadingPage(
                                navController,
                                recordId = entity.arguments?.getLong("recordId") ?: -1L,
                            )
                        }
                        composable(
                            route = "${PageConstant.DivFollowUp.text}/{recordId}",
                            arguments = listOf(
                                navArgument("recordId") {
                                    type = NavType.LongType
                                }
                            )
                        ) { entity ->
                            DivFollowUpPage(
                                navController,
                                recordId = entity.arguments?.getLong("recordId") ?: -1L,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 入队 APK 下载 Worker，并将 workId 写入 state 触发 LaunchedEffect 监听进度
 * @param downloadUrl 预签名下载地址（从后端 /web/app/download 获取）
 * @param version 版本信息（用于 fileSize、sha256 校验与 workName 唯一标识）
 */
private fun enqueueApkDownload(
    context: android.content.Context,
    workManager: WorkManager,
    downloadUrl: String,
    version: com.smartwash.network.vo.AppVersionVo,
    currentDownloadWorkId: androidx.compose.runtime.MutableState<String?>,
) {
    val inputData = workDataOf(
        ApkDownloadWorker.KEY_APK_URL to downloadUrl,
        ApkDownloadWorker.KEY_SHA256 to version.sha256,
    )

    val downloadRequest = OneTimeWorkRequestBuilder<ApkDownloadWorker>()
        .setInputData(inputData)
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .build()

    workManager.enqueueUniqueWork(
        "apk_download_${version.versionCode}",
        ExistingWorkPolicy.REPLACE,
        downloadRequest
    )

    // 记录 workId，触发 LaunchedEffect 收集进度
    currentDownloadWorkId.value = downloadRequest.id.toString()
}

/**
 * 处理 APK 安装：检查权限后调起系统安装器
 */
private fun handleApkInstall(
    context: android.content.Context,
    updateViewModel: com.smartwash.ui.page.update.UpdateViewModel,
    apkFile: java.io.File,
) {
    if (ApkInstaller.canInstallApk(context)) {
        updateViewModel.onInstallStarted()
        ApkInstaller.installViaIntent(context, apkFile)
        ApkInstaller.deleteLocalApk(context)
        updateViewModel.reset()
    } else {
        // 无安装权限，跳转系统设置
        ApkInstaller.openInstallPermissionSettings(context)
    }
}

