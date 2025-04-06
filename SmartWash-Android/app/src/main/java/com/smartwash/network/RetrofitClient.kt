package com.smartwash.network

import com.smartwash.network.api.CouponApi
import com.smartwash.network.api.LaundryItemsApi
import com.smartwash.network.api.OrderApi
import com.smartwash.network.api.PaymentApi
import com.smartwash.network.api.RechargeApi
import com.smartwash.network.api.SchoolApi
import com.smartwash.network.api.UserApi
import com.smartwash.network.interceptor.RequestInterceptor
import com.smartwash.network.interceptor.ResponseInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

/**
 * 网络请求工具
 */
@Module
@InstallIn(SingletonComponent::class)
class RetrofitClient() {
    private var baseurl: String = "http://192.168.5.16:8080/"

    private val okHttpClient = OkHttpClient
        .Builder()
        .addInterceptor(RequestInterceptor())
        .addInterceptor(ResponseInterceptor())
        .build();

    @Provides
    @Singleton
    fun getRetrofitClient(): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl(baseurl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    fun getUserApi(retrofit: Retrofit): UserApi {
        return retrofit.create(UserApi::class.java)
    }

    @Provides
    fun getSchoolApi(retrofit: Retrofit): SchoolApi {
        return retrofit.create(SchoolApi::class.java)
    }

    @Provides
    fun getRechargeApi(retrofit: Retrofit): RechargeApi {
        return retrofit.create(RechargeApi::class.java)
    }

    @Provides
    fun getLaundryItemsApi(retrofit: Retrofit): LaundryItemsApi {
        return retrofit.create(LaundryItemsApi::class.java)
    }

    @Provides
    fun getOrderApi(retrofit: Retrofit): OrderApi {
        return retrofit.create(OrderApi::class.java)
    }

    @Provides
    fun getPaymentApi(retrofit: Retrofit): PaymentApi {
        return retrofit.create(PaymentApi::class.java)
    }

    @Provides
    fun getCouponApi(retrofit: Retrofit): CouponApi {
        return retrofit.create(CouponApi::class.java)
    }
}