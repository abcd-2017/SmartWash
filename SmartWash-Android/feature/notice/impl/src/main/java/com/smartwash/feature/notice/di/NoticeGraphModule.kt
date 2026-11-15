package com.smartwash.feature.notice.di

import com.smartwash.common.ui.navigation.NavGraphContributor
import com.smartwash.feature.notice.ui.noticeGraph
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** 站内信域图贡献者（消息中心收件箱页）。 */
@Module
@InstallIn(SingletonComponent::class)
object NoticeGraphModule {

    @Provides
    @IntoSet
    fun provideNoticeGraph(): NavGraphContributor = NavGraphContributor { nc ->
        noticeGraph(nc)
    }
}
