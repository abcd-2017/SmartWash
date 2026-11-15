package com.smartwash.feature.divination.di

import com.smartwash.common.ui.navigation.NavGraphContributor
import com.smartwash.feature.divination.ui.divinationGraph
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** 观象台图贡献者（首页/问事/摇卦/卦盘/解读/追问/卦历七页）。 */
@Module
@InstallIn(SingletonComponent::class)
object DivinationGraphModule {

    @Provides
    @IntoSet
    fun provideDivinationGraph(): NavGraphContributor = NavGraphContributor { nc ->
        divinationGraph(nc)
    }
}
