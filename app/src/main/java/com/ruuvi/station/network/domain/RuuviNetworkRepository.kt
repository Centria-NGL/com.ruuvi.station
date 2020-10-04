package com.ruuvi.station.network.domain

import com.ruuvi.station.network.data.ClaimTagRequest
import com.ruuvi.station.network.data.UserRegisterRequest
import com.ruuvi.station.network.data.UserRegisterResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RuuviNetworkRepository {

    private val interceptor: HttpLoggingInterceptor = HttpLoggingInterceptor().also {
        it.level = HttpLoggingInterceptor.Level.BODY;
    }

    private val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .client(client)
        .build()

    val retrofitService: RuuviNetworkApi by lazy {
        retrofit.create(RuuviNetworkApi::class.java)
    }

    fun registerUser(user: UserRegisterRequest, onResult: (UserRegisterResponse?) -> Unit) {
        retrofitService.registerUser(user).enqueue(
            object : Callback<UserRegisterResponse> {
                override fun onFailure(call: Call<UserRegisterResponse>, t: Throwable) {
                    println(t)
                    onResult(null)
                }

                override fun onResponse(call: Call<UserRegisterResponse>, response: Response<UserRegisterResponse>) {
                    val registeredUser = response.body()
                    onResult(registeredUser)
                }
            }
        )
    }

    fun verifyUser(token: String, onResult: (UserRegisterResponse?) -> Unit) {
        retrofitService.verifyUser(token).enqueue(
            object : Callback<UserRegisterResponse> {
                override fun onFailure(call: Call<UserRegisterResponse>, t: Throwable) {
                    println(t)
                    onResult(null)
                }

                override fun onResponse(call: Call<UserRegisterResponse>, response: Response<UserRegisterResponse>) {
                    val registeredUser = response.body()
                    onResult(registeredUser)
                }
            }
        )
    }

    fun getUserInfo(token: String, onResult: (UserRegisterResponse?) -> Unit) {
        retrofitService.getUserInfo("Bearer " + token).enqueue(
            object : Callback<UserRegisterResponse> {
                override fun onFailure(call: Call<UserRegisterResponse>, t: Throwable) {
                    println(t)
                    onResult(null)
                }

                override fun onResponse(call: Call<UserRegisterResponse>, response: Response<UserRegisterResponse>) {
                    val registeredUser = response.body()
                    onResult(registeredUser)
                }
            }
        )
    }

    fun claimTag(tag: String, token: String, onResult: (UserRegisterResponse?) -> Unit) {
        retrofitService.claimTag("Bearer " + token, ClaimTagRequest(tag)).enqueue(
            object : Callback<UserRegisterResponse> {
                override fun onFailure(call: Call<UserRegisterResponse>, t: Throwable) {
                    println(t)
                    onResult(null)
                }

                override fun onResponse(call: Call<UserRegisterResponse>, response: Response<UserRegisterResponse>) {
                    val registeredUser = response.body()
                    onResult(registeredUser)
                }
            }
        )
    }

    companion object {
        private const val BASE_URL = "https://dhv743unoc.execute-api.eu-central-1.amazonaws.com/"
    }
}