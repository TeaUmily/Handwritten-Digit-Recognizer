package com.example.mydigitrecognizer.model

import com.google.gson.annotations.SerializedName

class ClassificationRequestData (
    @SerializedName("Inputs") val inputs : Inputs
)

data class Inputs (
    @SerializedName("input1") val input1 : List<Map<String, Int>>
)
