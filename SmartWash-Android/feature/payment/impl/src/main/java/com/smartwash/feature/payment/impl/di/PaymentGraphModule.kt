package com.smartwash.feature.payment.impl.di

import com.smartwash.common.ui.navigation.NavGraphContributor
import com.smartwash.feature.payment.impl.ui.paymentGraph
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** 支付域图贡献者（支付/支付成功/充值/充值记录四页）。 */
@Module
@InstallIn(SingletonComponent::class)
object PaymentGraphModule {

    @Provides
    @IntoSet
    fun providePaymentGraph(): NavGraphContributor = NavGraphContributor { nc ->
        paymentGraph(nc)
    }
}
