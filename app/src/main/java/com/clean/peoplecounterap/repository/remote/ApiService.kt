package com.clean.peoplecounterap.repository.remote

import com.clean.peoplecounterap.repository.remote.request.PostRequest
import com.clean.peoplecounterap.repository.remote.response.TokenResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.QueryMap

interface ApiService {

    @POST(API_PATH)
    suspend fun somePost(@Body list: List<PostRequest>): Response<Unit>

    @POST("hygieiaScreen/pairpeoplecounterapp")
    suspend fun generateToken(@QueryMap request: Map<String, String>): Response<TokenResponse>

    companion object {
        private const val SCHEME = "https://"
        private const val HOSTNAME = "metrocal.azurewebsites.net"
        private const val API_PATH = "hygieiaScreen/send?deviceIotID="

        const val SERVER = "$SCHEME$HOSTNAME/api/"
    }
}