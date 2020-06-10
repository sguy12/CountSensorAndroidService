package com.clean.peoplecounterapp.repository.remote

import com.clean.peoplecounterapp.repository.remote.request.PostRequest
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST(API_PATH)
    suspend fun somePost(@Body map: List<PostRequest>): Response<Unit>

    companion object {
        private const val SCHEME = "https://"
        private const val HOSTNAME = "metrocal.azurewebsites.net"
        private const val API_PATH = "hygieiaScreen/send?deviceIotID="

        const val SERVER = "$SCHEME$HOSTNAME/api/"
    }
}