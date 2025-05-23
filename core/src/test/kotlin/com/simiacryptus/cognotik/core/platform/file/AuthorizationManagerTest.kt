package com.simiacryptus.cognotik.platform.file

import com.simiacryptus.cognotik.platform.model.AuthorizationInterface
import com.simiacryptus.cognotik.platform.model.User
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

abstract open class AuthorizationInterfaceTest(
    private val authInterface: AuthorizationInterface
) {

    open val user = User(
        email = "newuser@example.com",
        name = "Jane Smith",
        id = "2",
        picture = "http://example.com/newpicture.jpg"
    )

    @Test
    fun `newUser has admin`() {
        assertFalse(authInterface.isAuthorized(this.javaClass, user, AuthorizationInterface.OperationType.Admin))
    }

}

class AuthorizationManagerTest : AuthorizationInterfaceTest(AuthorizationManager())