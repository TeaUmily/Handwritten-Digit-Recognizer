package com.example.mydigitrecognizer.api

import com.example.mydigitrecognizer.model.ResponseResult
import com.example.mydigitrecognizer.model.ClassificationRequestData
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface RestInterface {

    @POST("execute?api-version=2.0&format=swagger")
    fun getClassificationResult(@Body requestData: ClassificationRequestData) : Call<ResponseResult>
}