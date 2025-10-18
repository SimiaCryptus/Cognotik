package com.example.news

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class NewsApplication

fun main(args: Array<String>) {
    runApplication<NewsApplication>(*args)
}