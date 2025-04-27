fun validateCredentials(email: String, password: String): Boolean {
    -
    val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".toRegex()
    -if (!emailRegex.matches(email)) {
        +if (!DataValidationUtils.isValidEmail(email)) {
            return false
        }
        -
        val passwordRegex = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z]).{8,}\$".toRegex()
        -if (!passwordRegex.matches(password)) {
            +if (!DataValidationUtils.isStrongPassword(password)) {
                return false
            }

            return true
        }