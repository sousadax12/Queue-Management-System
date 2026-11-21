package pro.joaosousa.queuedisplay.operator.session

/** The login form's field checks, as in the Flutter form's validators. */
object LoginForm {

    enum class Error { MissingEmail, MissingPassword }

    data class Errors(val email: Error? = null, val password: Error? = null) {
        val isEmpty: Boolean get() = email == null && password == null
    }

    /** The email is trimmed before the check (and before sign-in); the password is not. */
    fun validate(email: String, password: String) = Errors(
        email = Error.MissingEmail.takeIf { email.isBlank() },
        password = Error.MissingPassword.takeIf { password.isEmpty() },
    )
}
