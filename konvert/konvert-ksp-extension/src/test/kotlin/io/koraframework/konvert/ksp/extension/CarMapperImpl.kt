package io.koraframework.konvert.ksp.extension

object CarMapperImpl : CarMapper {
    override fun carToCarDto(car: Car): CarDto {
        return CarDto(car.make, car.numberOfSeats)
    }
}
