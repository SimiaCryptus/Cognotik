package com.simiacryptus.cognotik.platform.file

import com.simiacryptus.cognotik.platform.model.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.*

abstract class AuthenticationInterfaceTest(
    private val authInterface: AuthenticationInterface
) {

    private val validAccessToken = UUID.randomUUID().toString()
    private val newUser = User(
        email = "newuser@example.com",
        name = "Jane Smith",
        id = "2",
    )

    @Test
    fun `getUser should return null when no user is associated with access token`() {
        val user = authInterface.getUser(validAccessToken)
        assertEquals(user, null)
    }

    @Test
    fun `putUser should add a new user and return the user`() {
        val returnedUser = authInterface.putUser(validAccessToken, newUser)
        assertEquals(newUser, returnedUser)
    }

    @Test
    fun `getUser should return User after putUser is called`() {
        authInterface.putUser(validAccessToken, newUser)
        val user = authInterface.getUser(validAccessToken)
        assertNotNull(user)
        assertEquals(newUser, user)
    }
/*
*
loving the new user and return the user`() {
        val returnedUser = authInterface.putUser(validAccessToken, newUser)
        assertEquals(newUser, returnedUser)
*
* */
    @Test
    fun `logout should remove the user associated with the access token`() {
        authInterface.putUser(validAccessToken, newUser)
        assertNotNull(authInterface.getUser(validAccessToken))

        authInterface.logout(validAccessToken, newUser)
        assertEquals(authInterface.getUser(validAccessToken), null)
    }

}

class AuthenticationManagerTest : AuthenticationInterfaceTest(AuthenticationManager())