package ru.tinkoff.kora.json.ksp.dto

import ru.tinkoff.kora.json.common.annotation.Json
import ru.tinkoff.kora.json.common.annotation.JsonDiscriminatorField

@Json
@JsonDiscriminatorField("type")
sealed interface KotlinSealedInterfaceDto {
    data class FirstDto(val zeroValue: String, val firstValue: String): KotlinSealedInterfaceDto
    data class SecondDto(val firstValue: String, val secondValue: Int): KotlinSealedInterfaceDto
}


