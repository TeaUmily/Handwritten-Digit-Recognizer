package com.example.mydigitrecognizer.model

import com.google.gson.annotations.SerializedName

data class Output(
    @SerializedName("Scored Labels") val recognizedNumber : String,
)