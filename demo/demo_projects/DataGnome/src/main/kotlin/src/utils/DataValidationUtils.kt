package utils

object DataValidationUtils {
    /**
     * Validates if the provided email is in a correct format.
     * @param phoneNumber The phone number string to validate.
     * @return True if the phone number is valid, false otherwise.
     */
    fun isValidPhoneNumber(phoneNumber: String): Boolean {
        val phoneRegex = Regex("^\\+?[1-9]\\d{1,14}\$")
        return phoneRegex.matches(phoneNumber)
    }

    /**
     * Validates if the provided email is in a correct format.
     * @param email The email string to validate.
     * @return True if the email is valid, false otherwise.
     */
    fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".toRegex()
        return emailRegex.matches(email)
    }
    /**
     * Validates if the provided phone number is in a correct format.
     * @param email The email string to validate.
     * @return True if the email is valid, false otherwise.
     */

    /**
     * Validates if the provided email is in a correct format.
     * @param email The email string to validate.
     * @return True if the email is valid, false otherwise.
     */
    fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".toRegex()
        return emailRegex.matches(email)
    }
    /**
     * Validates if the provided phone number is in a correct format.
     * @param phoneNumber The phone number string to validate.
     * @return True if the phone number is valid, false otherwise.
     */

    /**
     * Validates if the provided phone number is in a correct format.
     * @param phoneNumber The phone number string to validate.
     * @return True if the phone number is valid, false otherwise.
     */
    fun isValidPhoneNumber(phoneNumber: String): Boolean {
        val phoneRegex = Regex("^\\+?[1-9]\\d{1,14}\$")
        return phoneRegex.matches(phoneNumber)
    }

    /**
     * Validates if the provided password is strong.
     * A strong password must be at least 8 characters long and contain at least one digit, one uppercase letter, one lowercase letter, and one special character.
     * @param password The password string to validate.
     * @return True if the password is strong, false otherwise.
     */
    fun isStrongPassword(password: String): Boolean {
        val passwordRegex = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#\$%^&+=]).{8,}\$".toRegex()
        return passwordRegex.matches(password)
    }

    /**
     * Validates if the provided URL is in a correct format.
     * @param url The URL string to validate.
     * @return True if the URL is valid, false otherwise.
     */
    fun isValidURL(url: String): Boolean {
        val urlRegex = "^(https?|ftp)://[^\s/$.?#].[^\s]*$".toRegex()
        return urlRegex.matches(url)
    }

    /**
     * Validates if the provided date is in a correct format.
     * @param date The date string to validate.
     * @return True if the date is valid, false otherwise.
     */
    fun isValidDate(date: String): Boolean {
        val dateFormat = "yyyy-MM-dd"
        val sdf = java.text.SimpleDateFormat(dateFormat)
        sdf.isLenient = false
        return try {
            sdf.parse(date)
            true
        } catch (e: java.text.ParseException) {
            false
        }
    }

    /**
     * Validates if the provided number is within the specified range.
     * @param number The number to validate.
     * @param min The minimum value of the range.
     * @param max The maximum value of the range.
     * @return True if the number is within the range, false otherwise.
     */
    fun isWithinRange(number: Int, min: Int, max: Int): Boolean {
        return number in min..max
    }

    /**
     * Validates if the provided string is a valid hexadecimal color code.
     * @param colorCode The color code string to validate.
     * @return True if the color code is valid, false otherwise.
     */
    fun isValidHexColor(colorCode: String): Boolean {
        val hexColorRegex = "^#(?:[0-9a-fA-F]{3}){1,2}\$".toRegex()
        return hexColorRegex.matches(colorCode)
    }

    /**
     * Validates if the provided string is a valid password.
     * A strong password must be at least 8 characters long and contain at least one digit, one uppercase letter, and one lowercase letter.
     * @param password The password string to validate.
     * @return True if the password is strong, false otherwise.
     */
    fun isStrongPassword(password: String): Boolean {
        val passwordRegex = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#\$%^&+=]).{8,}\$".toRegex()
        return passwordRegex.matches(password)
    }

    fun isStrongPassword(password: String): Boolean {
        val passwordRegex = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z]).{8,}\$".toRegex()
        return passwordRegex.matches(password)
    }
}