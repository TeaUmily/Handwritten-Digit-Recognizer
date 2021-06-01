package com.example.mydigitrecognizer.model

import com.google.gson.annotations.SerializedName

data class Results(
    @SerializedName("output1") val output: List<Output>
)