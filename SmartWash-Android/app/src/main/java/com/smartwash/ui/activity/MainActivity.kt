package com.smartwash.ui.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.work.WorkManager
import com.smartwash.R
import com.smartwash.common.ui.theme.AuthGradientBottom
import com.smartwash.common.ui.theme.AuthGradientTop
import com.smartwash.common.ui.theme.SmartWashAndroidTheme
import com.smartwash.common.utils.HapticEffect
import com.smartwash.common.utils.LocalReduceMotion
import com.smartwash.common.utils.isReduceMotionEnabled
import com.smartwash.common.utils.performHaptic
import com.smartwash.feature.coupon.ui.couponGraph
import com.smartwash.feature.divination.ui.divinationGraph
import com.smartwash.feature.laundry.ui.laundryGraph
import com.smartwash.feature.order.impl.ui.orderGraph
import com.smartwash.feature.payment.impl.ui.paymentGraph
import com.smartwash.feature.update.event.UpdateEventBus
import com.smartwash.feature.update.ui.UpdateViewModel
import com.smartwash.feature.user.api.UserApi
import com.smartwash.feature.user.api.UserRoute
import com.smartwash.feature.user.api.model.LoginEvent
import com.smartwash.feature.user.api.model.LogoutReason
import com.smartwash.feature.user.impl.ui.userGraph
import com.smartwash.ui.navigation.shellEnterTransition
import com.smartwash.ui.navigation.shellExitTransition
import com.smartwash.ui.navigation.shellGraph
import com.smartwash.ui.page.update.CheckUpdateSettingRow
import com.smartwash.ui.page.update.UpdateFlow
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * 单 Activity 壳（T8.1 拆路由后）：NavHost 只做聚合——壳层留守页面在
 * [shellGraph]，各 feature 域经 NavGraphBuilder.xxxGraph 扩展自注册
 * （路由常量单一事实来源在各域 api 模块）；壳层另持更新流程（[UpdateFlow]）
 * 与 401/登录态事件收集两项壳职责。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // 用户域经 UserApi 契约消费（T5.3）：会话事件收集 + 登录态判断，不触碰 impl 类型
    @Inject lateinit var userApi: UserApi
    @Inject lateinit var workManager: WorkManager
    @Inject lateinit var updateEventBus: UpdateEventBus

    /**
     * 启动时申请通知权限（Android 13+）：APK 下载进度需要在通知栏展示，
     * 未授权则通知不显示，但下载功能仍可用。
     */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // 无论授权与否都不阻塞用户，仅影响通知栏是否展示下载进度
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 启动时主动申请通知权限（Android 13+ 需要运行时授权）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val navController = rememberNavController()
            val context = LocalContext.current
            val view = LocalView.current
            // reduced motion 随导航刷新 — 既避免在组合期反复做 ContentResolver 查询，
            // 又能让用户在系统设置里改完「移除动画」切回应用后立即生效。
            val backStackEntry by navController.currentBackStackEntryAsState()
            val reduceMotion = remember(backStackEntry) { isReduceMotionEnabled(context) }

            // 收集用户域登录态事件（UserApi.loginEvents，T5.3 自 SessionEventBus 切换）：
            // 未登录拦截 / 401 登录失效 → 统一跳登录页，reason 保留文案与触感差异。
            // 事件源（user-impl 内 SessionEventBus）已去重 + navigate 加 launchSingleTop，
            // 避免连发 401 堆叠多个登录页；token 清理由拦截器幂等处理，这里不再重复清。
            LaunchedEffect(userApi) {
                userApi.loginEvents.collect { event ->
                    when (event) {
                        is LoginEvent.LoggedOut -> when (event.reason) {
                            LogoutReason.NEED_LOGIN -> {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.please_login),
                                    Toast.LENGTH_SHORT
                                ).show()
                                navController.popBackStack()
                                navController.navigate(UserRoute.Login.text) {
                                    launchSingleTop = true
                                }
                            }

                            LogoutReason.UNAUTHORIZED -> {
                                view.performHaptic(HapticEffect.ERROR)
                                delay(200)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.please_re_login),
                                    Toast.LENGTH_SHORT
                                ).show()
                                navController.popBackStack()
                                navController.navigate(UserRoute.Login.text) {
                                    launchSingleTop = true
                                }
                            }

                            // 主动登出：设置页自理导航，壳层不响应
                            LogoutReason.LOGOUT -> {}
                        }

                        // 登录/注册成功：登录页自理导航，壳层不响应
                        LoginEvent.LoggedIn -> {}
                    }
                }
            }

            SmartWashAndroidTheme {
                // 全应用动效降级开关（规范 7.8）：叶子节点经 motionSpec()/LocalReduceMotion 消费
                CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                    // 更新流程（事件收集 → 下载调度 → 弹窗展示；T8.1 自本文件抽至壳层 UpdateFlow）：
                    // 必须在主题包装内组合——否则更新弹窗位于 SmartWashAndroidTheme 之外，
                    // MaterialTheme/AppColors 均回落浅色默认值，深色模式下弹窗仍显示浅色样式
                    val updateViewModel: UpdateViewModel = hiltViewModel()
                    UpdateFlow(context, workManager, updateViewModel, updateEventBus)

                    Surface(modifier = Modifier.fillMaxSize()) {
                        // 转场露底色跟随当前目的地：导航命令执行时 currentBackStackEntry 即更新，
                        // 动画在其上播放——认证页透认证渐变（修复登录↔注册白闪），
                        // 其余页面透主题底色，避免全局纯绿背景在非认证转场中透色。
                        val currentRoute = backStackEntry?.destination?.route
                        val isAuthRoute = currentRoute == UserRoute.Login.text ||
                            currentRoute == UserRoute.Register.text
                        NavHost(
                            navController = navController,
                            startDestination = UserRoute.Login.text,
                            enterTransition = { shellEnterTransition(reduceMotion) },
                            exitTransition = { shellExitTransition(reduceMotion) },
                            popEnterTransition = { shellEnterTransition(reduceMotion) },
                            popExitTransition = { shellExitTransition(reduceMotion) },
                            modifier = Modifier.background(
                                if (isAuthRoute) {
                                    Brush.verticalGradient(listOf(AuthGradientTop, AuthGradientBottom))
                                } else {
                                    SolidColor(MaterialTheme.colorScheme.background)
                                }
                            )
                        ) {
                            // 壳层留守页面：主页壳 / 订单详情 / 寄件取件 / 取件
                            shellGraph(navController)
                            // 检查更新行插槽注入（feature:update 归壳层聚合，user-impl 不依赖它）
                            userGraph(
                                navController,
                                checkUpdateContent = { CheckUpdateSettingRow(updateViewModel) }
                            )
                            orderGraph(navController)
                            paymentGraph(navController, reduceMotion)
                            laundryGraph(navController)
                            couponGraph(navController)
                            divinationGraph(navController)
                        }
                    }
                }
            }
        }
    }
}
