package com.example.mydigitrecognizer.api

import com.example.mydigitrecognizer.model.ResponseResult
import com.example.mydigitrecognizer.model.ClassificationRequestData
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Callback
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


class RepositoryRetriever {
    private val service: RestInterface
    companion object {
        const val BASE_URL = "https://ussouthcentral.services.azureml.net/workspaces/e29b29ca0ee947ecb2d813984123ce22/services/bfb6e7b6fdb8450394d13cd68d92458b/"
        const val API_KEY = "g8tB0ArWzdUI6zrguzv54yER4PSDN533k2yTLv2nWZKxEI/Fz0fNTz/ECr9Go2lPQzhrC5CuBw3149Me7ou2Bw=="
        private const val CONNECTION_TIMEOUT = 60000L // miliseconds
        private const val CONTENT_TIMEOUT = 90000L // miliseconds
    }

    init {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val newRequest: Request = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $API_KEY")
                .build()
            chain.proceed(newRequest)
        }.connectTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
            .readTimeout(CONTENT_TIMEOUT, TimeUnit.MILLISECONDS)
            .writeTimeout(CONTENT_TIMEOUT, TimeUnit.MILLISECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .client(client)
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        service = retrofit.create(RestInterface::class.java)
    }

    fun getClassificationResult(requestData: ClassificationRequestData, callback: Callback<ResponseResult>) {
        val call = service.getClassificationResult(requestData)
        call.enqueue(callback)
    }
}