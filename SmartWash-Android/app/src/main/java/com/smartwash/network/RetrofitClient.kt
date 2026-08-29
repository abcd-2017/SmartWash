package com.smartwash.network

import android.content.Context
import androidx.room.Room
import com.smartwash.BuildConfig
import com.smartwash.database.AppDatabase
import com.smartwash.database.DivRecordMigration
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
import com.smartwash.network.session.SessionEventBus
import com.smartwash.network.session.SessionManager
import com.smartwash.utils.AppConstant
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class RetrofitClient {
    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        sessionManager: SessionManager,
        sessionEventBus: SessionEventBus,
    ): OkHttpClient {
        // 不配置 HTTP 缓存：后端未返回缓存头，缓存恒不命中反而占用磁盘
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(RequestInterceptor(sessionManager, sessionEventBus))
            .addInterceptor(ResponseInterceptor(sessionManager, sessionEventBus))

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
            .baseUrl(BuildConfig.BASE_URL)
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
        )
            // 缓存库无存量数据，schema 变更时允许破坏性重建（正式数据表迁移到服务端）；
            // v2 新增 div_records 卦历表由 DivRecordMigration 显式迁移，升级时保留用户卦历。
            .addMigrations(DivRecordMigration())
            .fallbackToDestructiveMigration()
            .build()
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
