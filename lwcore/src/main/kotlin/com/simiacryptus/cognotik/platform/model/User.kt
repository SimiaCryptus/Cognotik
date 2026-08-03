package com.simiacryptus.cognotik.platform.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

data class User(
  @get:JsonProperty("email") val email: String,
  @get:JsonProperty("name") val name: String = email,
  @get:JsonProperty("id") val id: String = email,
) {
  override fun toString() = email

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as User
    return email == other.email
  }

  override fun hashCode(): Int {
    return email.hashCode()
  }

    companion object {
        val NULL: User = User(
            id = "0",
            email = "null@localhost"
        )
    }

}

@JsonIgnore
var defaultUser = User(
  id = "1",
  email = "user@localhost"
)

interface UserProvider {
  fun authenticate(
    request: HttpServletRequest,
    response: HttpServletResponse?
  ): User?
}