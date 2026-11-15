package com.smartwash.feature.user.impl.di

import com.smartwash.common.ui.navigation.NavGraphContributor
import com.smartwash.feature.user.impl.ui.userGraph
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** 用户域图贡献者（登录/注册/资料/设置/权限/钱包/密码等页面）。 */
@Module
@InstallIn(SingletonComponent::class)
object UserGraphModule {

    @Provides
    @IntoSet
    fun provideUserGraph(): NavGraphContributor = NavGraphContributor { nc ->
        userGraph(nc)
    }
}
