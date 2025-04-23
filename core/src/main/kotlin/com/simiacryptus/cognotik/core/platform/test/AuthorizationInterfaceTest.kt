import com.simiacryptus.cognotik.core.platform.model.AuthorizationInterface
import com.simiacryptus.cognotik.core.platform.model.User
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

open class AuthorizationInterfaceTest(
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
