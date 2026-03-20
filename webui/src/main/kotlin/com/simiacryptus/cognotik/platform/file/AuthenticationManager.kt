package com.simiacryptus.cognotik.platform.file

import com.simiacryptus.cognotik.platform.model.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.User

open class AuthenticationManager : AuthenticationInterface {

  private val users = HashMap<String, User>()

  override fun getUser(accessToken: String?): User {
    val user = users[accessToken]
    return if (user != null) user else
      throw RuntimeException("User not found for access token")
  }

  override fun putUser(accessToken: String, user: User): User {
    users[accessToken] = user
    return user
  }

  override fun logout(accessToken: String, user: User) {
    require(users[accessToken] == user) { "Invalid user" }
    users.remove(accessToken)
  }

}