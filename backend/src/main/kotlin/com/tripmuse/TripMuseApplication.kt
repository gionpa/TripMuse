package com.tripmuse

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching

@SpringBootApplication
@EnableCaching
class TripMuseApplication

fun main(args: Array<String>) {
    runApplication<TripMuseApplication>(*args)
}
