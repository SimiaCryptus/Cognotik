package com.example

+import utils.StringManipulationUtils

class AnotherClass {

    fun formatMessage(name: String): String {
        -return "Hello, ${name.capitalize()}!"
        +return StringManipulationUtils.format("Hello, %s!", StringManipulationUtils.capitalizeFirst(name))
    }
}