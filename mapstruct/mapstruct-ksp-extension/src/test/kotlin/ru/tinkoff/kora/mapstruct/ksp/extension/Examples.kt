package ru.tinkoff.kora.mapstruct.ksp.extension

import org.mapstruct.Mapper
import org.mapstruct.Mapping

enum class CarType { TYPE1, TYPE2 }

data class Car(val make: String, val numberOfSeats: Int, val type: CarType)

data class CarDto(val make: String, val seatCount: Int, val type: String)

@Mapper
interface CarMapper {

    @Mapping(source = "numberOfSeats", target = "seatCount")
    fun carToCarDto(car: Car): CarDto
}

