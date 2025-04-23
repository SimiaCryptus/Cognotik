package com.example

+import utils.StringManipulationUtils

class SomeClass {

    fun processString(input: String): String {
        -
        val trimmed = input.trim()
        -
        val uppercased = trimmed.toUpperCase()
        +
        val trimmed = StringManipulationUtils.trim(input)
        +
        val uppercased = StringManipulationUtils.toUpperCase(trimmed)
        return uppercased
    }
}