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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smartwash.App
import com.smartwash.ui.page.PageConstant
import com.smartwash.ui.page.home.HomePage
import com.smartwash.ui.page.login.LoginPage
import com.smartwash.ui.page.order.OrderPage
import com.smartwash.ui.page.recharge.RechargePage
import com.smartwash.ui.page.register.RegisterPage
import com.smartwash.ui.page.service.ServicePage
import com.smartwash.ui.page.setting.SettingPage
import com.smartwash.ui.page.update_userinfo.UpdateUserInfoPage
import com.smartwash.ui.payment.PaymentPage
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
                        composable(PageConstant.Order.text) {
                            OrderPage(navController)
                        }
                        composable(PageConstant.Service.text) {
                            ServicePage()
                        }
                        composable(PageConstant.Payment.text) {
                            PaymentPage()
                        }
                    }
                }
            }
        }
    }
}

