package io.koraframework.konvert.ksp.extension

import io.mcarle.konvert.api.Konverter

data class Car(val make: String, val numberOfSeats: Int)

data class CarDto(val make: String, val seatCount: Int)

@Konverter
interface CarMapper {
    fun carToCarDto(car: Car): CarDto
}
