fun registerUser(email: String, phoneNumber: String, password: String): Boolean {
    -if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".toRegex())) {
        +if (!DataValidationUtils.isValidEmail(email)) {
            return false
        }
        -if (!phoneNumber.matches("^\\+?[1-9]\\d{1,14}\$".toRegex())) {
            +if (!DataValidationUtils.isValidPhoneNumber(phoneNumber)) {
                return false
            }
            -if (!password.matches("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z]).{8,}\$".toRegex())) {
                +if (!DataValidationUtils.isStrongPassword(password)) {
                    return false
                }

                return true
            }