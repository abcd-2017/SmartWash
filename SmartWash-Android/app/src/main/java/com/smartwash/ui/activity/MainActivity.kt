package com.smartwash.ui.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.smartwash.ui.page.update_userinfo.UpdateUserInfoPage
import com.smartwash.ui.theme.SmartWashAndroidTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionEventBus: SessionEventBus
    @Inject lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            val context = LocalContext.current
            val view = LocalView.current
            val reduceMotion = isReduceMotionEnabled(context)

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
                    }
                }
            }
        }
    }
}

