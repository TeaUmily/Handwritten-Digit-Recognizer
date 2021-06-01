package com.example.mydigitrecognizer.model

import com.google.gson.annotations.SerializedName

data class ResponseResult(
    @SerializedName("Results") val results: Results
)