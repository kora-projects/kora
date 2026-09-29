package io.koraframework.mapstruct.ksp.extension

import io.koraframework.common.annotation.Tag
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Tag(TaggedCarMapper::class)
@Mapper
interface TaggedCarMapper {

    @Mapping(source = "numberOfSeats", target = "seatCount")
    fun carToCarDto(car: Car): CarDto
}
