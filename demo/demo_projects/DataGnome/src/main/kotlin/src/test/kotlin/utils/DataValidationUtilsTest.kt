package utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DataValidationUtilsTest {

    @Test
    fun testIsValidEmail() {
        assertTrue(DataValidationUtils.isValidEmail("test@example.com"))
        assertTrue(DataValidationUtils.isValidEmail("user.name+tag+sorting@example.com"))
        assertFalse(DataValidationUtils.isValidEmail("plainaddress"))
        assertFalse(DataValidationUtils.isValidEmail("@missingusername.com"))
        assertFalse(DataValidationUtils.isValidEmail("username@.com"))
    }

    @Test
    fun testIsValidPhoneNumber() {
        assertTrue(DataValidationUtils.isValidPhoneNumber("+1234567890"))
        assertTrue(DataValidationUtils.isValidPhoneNumber("1234567890"))
        assertFalse(DataValidationUtils.isValidPhoneNumber("123"))
        assertFalse(DataValidationUtils.isValidPhoneNumber("phone123"))
        assertFalse(DataValidationUtils.isValidPhoneNumber("+1(234)567-890"))
    }

    @Test
    fun testIsValidURL() {
        assertTrue(DataValidationUtils.isValidDate("2023-10-15"))
        assertFalse(DataValidationUtils.isValidDate("15-10-2023"))
        assertFalse(DataValidationUtils.isValidDate("2023/10/15"))
        assertFalse(DataValidationUtils.isValidDate("2023-02-30")) // Invalid date
        assertFalse(DataValidationUtils.isValidDate("2023-13-01")) // Invalid month
    }
}