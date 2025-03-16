package com.smartwash.network

import android.content.Context
import com.smartwash.network.interceptor.RequestInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * 网络请求工具
 */
class RetrofitClient(context: Context) {
    private var baseurl: String = "http://192.168.5.20:8080/"

    private val okHttpClient = OkHttpClient
        .Builder()
        .addInterceptor(RequestInterceptor())
        .build();

    private fun getRetrofitClient(): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl(baseurl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun <T> getRequestApi(t: Class<T>): T {
        return getRetrofitClient().create(t)
    }
}