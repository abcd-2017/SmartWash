package com.smartwash.ui.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smartwash.App
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.page.detail.OrderDetailPage
import com.smartwash.ui.page.home.HomePage
import com.smartwash.ui.page.laundry.LaundryPage
import com.smartwash.ui.page.login.LoginPage
import com.smartwash.ui.page.order.OrderPage
import com.smartwash.ui.page.payment.PaySuccessPage
import com.smartwash.ui.page.payment.PaymentPage
import com.smartwash.ui.page.recharge.RechargePage
import com.smartwash.ui.page.register.RegisterPage
import com.smartwash.ui.page.service.ServicePage
import com.smartwash.ui.page.setting.SettingPage
import com.smartwash.ui.page.update_userinfo.UpdateUserInfoPage
import com.smartwash.ui.theme.SmartWashAndroidTheme
import com.smartwash.utils.AppConstant
import com.smartwash.utils.SharePreferenceUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            val coroutineScope = rememberCoroutineScope()
            val context = LocalContext.current
            //请求前判断是否需要带上token，
            App.globalRequestBeforeCallback = {
                coroutineScope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "请登录",
                        Toast.LENGTH_SHORT
                    ).show()
                    navController.popBackStack()
                    navController.navigate(PageConstant.Login.text)
                }
            }
            //判断请求后响应码是否为401，是的话就重新登录
            App.globalRequestAfterCallback = {
                SharePreferenceUtils.saveData(AppConstant.TOKEN, "")
                coroutineScope.launch(Dispatchers.Main) {
                    delay(200)
                    Toast.makeText(
                        context,
                        "请重新登录",
                        Toast.LENGTH_SHORT
                    ).show()
                    navController.popBackStack()
                    navController.navigate(PageConstant.Login.text)
                }
            }

            SmartWashAndroidTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = PageConstant.Login.text,
                        enterTransition = {
                            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left) + fadeIn(
                                animationSpec = tween(300)
                            )
                        },
                        exitTransition = {
                            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right) + fadeOut(
                                animationSpec = tween(300)
                            )
                        }
                    ) {
                        composable(PageConstant.Login.text) {
                            LoginPage(navController)
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
                            SettingPage(navController)
                        }
                        composable(PageConstant.Recharge.text) {
                            RechargePage(navController)
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
                                entity.arguments?.getLong("itemId") ?: -1
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
                            )
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
                            )) { entity ->
                            PaySuccessPage(navController, entity.arguments?.getLong("orderId"))
                        }
                    }
                }
            }
        }
    }
}

