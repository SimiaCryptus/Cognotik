package com.simiacryptus.cognotik.platform.model

interface AuthenticationInterface {
    fun getUser(accessToken: String?): User?

    fun putUser(accessToken: String, user: User): User
    fun logout(accessToken: String, user: User)


    companion object {
        const val AUTH_COOKIE = "sessionId"
    }
}