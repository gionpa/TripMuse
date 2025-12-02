package com.tripmuse

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TripMuseApplication

fun main(args: Array<String>) {
    runApplication<TripMuseApplication>(*args)
}
