package com.example.mydigitrecognizer.model

import com.google.gson.annotations.SerializedName

data class Output(
    @SerializedName("Recognized Number") val recognizedNumber : Int,
    @SerializedName("Minimal Distance") val minimalDistance : Double
)