package com.clean.peoplecounterap.repository.remote

import android.content.Context
import com.clean.peoplecounterap.BuildConfig
import com.clean.peoplecounterap.data.local.SPManager
import com.clean.peoplecounterap.repository.remote.request.PostRequest
import com.clean.peoplecounterap.uitls.HttpLogger
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

class RestManager(private val context: Context) {

    companion object {
        private const val TIME_OUT = 30L
    }

    private lateinit var api: ApiService
    private lateinit var gson: Gson

    init {
        initServices(createRetrofit())
    }

    private fun initServices(retrofit: Retrofit) {
        api = retrofit.create(ApiService::class.java)
    }

    private fun createRetrofit() = Retrofit.Builder().apply {
        baseUrl(ApiService.SERVER)
        addConverterFactory(ScalarsConverterFactory.create())
        addConverterFactory(createGsonConverter())
        client(createClient())
    }.build()

    private fun createClient() = OkHttpClient.Builder().apply {
        addInterceptor {
            val original = it.request()
            val type = if (SPManager(context).token.isNullOrEmpty()) {
                "Application/x-www-form-urlencoded"
            } else {
                "application/json"
            }
            it.proceed(original.newBuilder()
                    .header("Content-type", type)
                    .header(
                            "x-token-screen-mc",
                            SPManager(context).token ?: ""
                            /*"UUxzaW9EcUx4QmE0eWVDOElsK0VEU0dONlA2dDhGcDdsR2JTbHJzTU5NNjRYM2tieDVyWHpsZHdQNW1JS2pwWDlDUm5GcFhEajJNL0xLWk1CYjhhSEg5dzQydG5GV0N1RGZ1STBKUXU0RnluTzNTVnA1TmlsaEJBWkxiZzFESzl1QldzYXlndC8xRT0="*/).build())
        }

        connectTimeout(TIME_OUT, TimeUnit.SECONDS)
        writeTimeout(TIME_OUT, TimeUnit.SECONDS)
        readTimeout(TIME_OUT, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            interceptors().add(
                    HttpLogger(context = context).apply {
                        level = HttpLogger.Level.BODY
                    })
        }

    }.build()

    private fun createGsonConverter() =
            GsonConverterFactory.create(GsonBuilder().apply {
                setLenient()
            }.create().also {
                gson = it
            })

    suspend fun somePost(list: List<PostRequest>) = api.somePost(list)

    suspend fun generateToken(request: Map<String, String>) = api.generateToken(request)
}