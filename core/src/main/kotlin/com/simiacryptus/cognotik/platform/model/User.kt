package com.simiacryptus.cognotik.platform.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class User(
  @get:JsonProperty("email") val email: String,
  @get:JsonProperty("name") val name: String? = null,
  @get:JsonProperty("id") val id: String? = null,
  @get:JsonProperty("picture") val picture: String? = null,
  @get:JsonIgnore val credential: Any? = null,
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

}