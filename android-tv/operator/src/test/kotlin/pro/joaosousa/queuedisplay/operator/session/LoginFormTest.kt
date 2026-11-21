package pro.joaosousa.queuedisplay.operator.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginFormTest {

    @Test
    fun `requires an email and a password`() {
        assertEquals(
            LoginForm.Errors(LoginForm.Error.MissingEmail, LoginForm.Error.MissingPassword),
            LoginForm.validate(email = "  ", password = ""),
        )
    }

    @Test
    fun `a password of spaces is still a password`() {
        assertTrue(LoginForm.validate(email = "op@example.com", password = " ").isEmpty)
    }
}
