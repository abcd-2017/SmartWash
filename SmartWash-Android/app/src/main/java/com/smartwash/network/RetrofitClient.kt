package com.smartwash.network

import android.content.Context
import androidx.room.Room
import com.smartwash.BuildConfig
import com.smartwash.database.AppDatabase
import com.smartwash.database.dao.CouponVoDao
import com.smartwash.database.dao.LaundryItemDao
import com.smartwash.database.dao.SchoolNameDao
import com.smartwash.network.api.CouponApi
import com.smartwash.network.api.LaundryItemsApi
import com.smartwash.network.api.OrderApi
import com.smartwash.network.api.PaymentApi
import com.smartwash.network.api.RechargeApi
import com.smartwash.network.api.SchoolApi
import com.smartwash.network.api.UserApi
import com.smartwash.network.interceptor.RequestInterceptor
import com.smartwash.network.interceptor.ResponseInterceptor
import com.smartwash.utils.AppConstant
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class RetrofitClient {
    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, "http_cache")
        val cache = Cache(cacheDir, 10 * 1024 * 1024)
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .cache(cache)
            .addInterceptor(RequestInterceptor())
            .addInterceptor(ResponseInterceptor())

        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor()
            loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun getRetrofitClient(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl(AppConstant.DEFAULT_SERVER)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    fun getUserApi(retrofit: Retrofit): UserApi = retrofit.create(UserApi::class.java)

    @Provides
    fun getSchoolApi(retrofit: Retrofit): SchoolApi = retrofit.create(SchoolApi::class.java)

    @Provides
    fun getRechargeApi(retrofit: Retrofit): RechargeApi = retrofit.create(RechargeApi::class.java)

    @Provides
    fun getLaundryItemsApi(retrofit: Retrofit): LaundryItemsApi = retrofit.create(LaundryItemsApi::class.java)

    @Provides
    fun getOrderApi(retrofit: Retrofit): OrderApi = retrofit.create(OrderApi::class.java)

    @Provides
    fun getPaymentApi(retrofit: Retrofit): PaymentApi = retrofit.create(PaymentApi::class.java)

    @Provides
    fun getCouponApi(retrofit: Retrofit): CouponApi = retrofit.create(CouponApi::class.java)

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "smartwash_db",
        ).build()
    }

    @Provides
    fun provideLaundryItemDao(database: AppDatabase): LaundryItemDao {
        return database.laundryItemDao()
    }

    @Provides
    fun provideSchoolNameDao(database: AppDatabase): SchoolNameDao {
        return database.schoolNameDao()
    }

    @Provides
    fun provideCouponVoDao(database: AppDatabase): CouponVoDao {
        return database.couponVoDao()
    }
}
