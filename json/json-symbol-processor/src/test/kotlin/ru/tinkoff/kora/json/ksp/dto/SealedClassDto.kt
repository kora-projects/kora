package ru.tinkoff.kora.json.ksp.dto

import ru.tinkoff.kora.json.common.annotation.Json
import ru.tinkoff.kora.json.common.annotation.JsonDiscriminatorField

@Json
@JsonDiscriminatorField("@type")
sealed class SealedClassDto(open val commonValue: String) {

    data class FirstDto(override val commonValue: String, val firstValue: String, val innerDto: InnerDto): SealedClassDto(commonValue){
        @Json
        data class InnerDto(val innerValue: String)
    }
    data class SecondDto(val firstValue: String, val secondValue: Int): SealedClassDto("second")
}


