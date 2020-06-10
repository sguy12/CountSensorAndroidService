package com.clean.peoplecounterapp.repository.remote.request

import com.google.gson.annotations.SerializedName

data class PostRequest(
        @SerializedName("dateCreated") val dateCreated: String, // format 2020-06-02T09:25:43.511Z
        @SerializedName("valueInt") val size: Int,
        @SerializedName("sensorTypeId") val sensorTypeId: Int = 30)