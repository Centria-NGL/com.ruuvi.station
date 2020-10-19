package com.ruuvi.station.network.domain

import com.ruuvi.station.database.tables.RuuviTagEntity
import com.ruuvi.station.network.data.NetworkTokenInfo
import com.ruuvi.station.network.data.request.ClaimSensorRequest
import com.ruuvi.station.network.data.request.GetSensorDataRequest
import com.ruuvi.station.network.data.request.ShareSensorRequest
import com.ruuvi.station.network.data.request.UserRegisterRequest
import com.ruuvi.station.network.data.response.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RuuviNetworkInteractor (
    private val tokenRepository: NetworkTokenRepository,
    private val networkRepository: RuuviNetworkRepository
) {
    val signedIn: Boolean
        get() = getToken() != null

    fun getEmail() = getToken()?.email

    private fun getToken() = tokenRepository.getTokenInfo()

    private var userInfo: UserInfoResponse? = null

    val mainScope = CoroutineScope(Dispatchers.Main)

    init {
        getUserInfo {}
    }

    fun registerUser(user: UserRegisterRequest, onResult: (UserRegisterResponse?) -> Unit) {
        networkRepository.registerUser(user) {
            onResult(it)
        }
    }

    fun verifyUser(token: String, onResult: (UserVerifyResponse?) -> Unit) {
        networkRepository.verifyUser(token) { response ->
            response?.let {
                if (response.error.isNullOrEmpty() && response.data != null) {
                    tokenRepository.saveTokenInfo(
                        NetworkTokenInfo(response.data.email, response.data.accessToken))
                    getUserInfo() {}
                }
            }
            onResult(response)
        }
    }

    fun getUserInfo(onResult: (UserInfoResponse?) -> Unit) {
        val token = getToken()
        if (token != null) {
            networkRepository.getUserInfo(token.token) {
                userInfo = it
                onResult(it)
            }
        } else {
            onResult(null)
        }
    }

    fun tagIsClaimed(mac: String): Boolean {
        val userInfo = userInfo
        userInfo?.let {
            if (userInfo.data != null) {
                return userInfo.data.sensors.any { it.sensor == mac }
            }
        }
        return false
    }

    fun claimSensor(tag: RuuviTagEntity, onResult: (ClaimSensorResponse?) -> Unit) {
        val token = getToken()?.token
        token?.let {
            val request = ClaimSensorRequest(tag.displayName, tag.id.toString())
            networkRepository.claimSensor(request, token) { claimResponse ->
                getUserInfo {
                    onResult(claimResponse)
                }
            }
        }
    }

    fun shareSensor(recipientEmail: String, tagId: String, onResult: (ShareSensorResponse?) -> Unit) {
        val token = getToken()?.token
        token?.let {
            val request = ShareSensorRequest(recipientEmail, tagId)
            networkRepository.shareSensor(request, token) { shareResponse ->
                onResult(shareResponse)
            }
        }
    }

    fun getSensorData(request: GetSensorDataRequest, onResult: (GetSensorDataResponse?) -> Unit) {
        val token = getToken()?.token
        mainScope.launch {
            token?.let {
                val result = networkRepository.getSensorData(token, request )
                onResult(result)
            }
        }
    }

    suspend fun getSensorData(request: GetSensorDataRequest):GetSensorDataResponse? = withContext(Dispatchers.IO) {
        val token = getToken()?.token
        token?.let {
            return@withContext networkRepository.getSensorData(token, request)
        }
    }
}
