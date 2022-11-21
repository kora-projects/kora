package ru.tinkoff.kora.json.ksp.dto

import ru.tinkoff.kora.json.common.annotation.Json

@Json
data class DtoWithEnum(val testEnum: TestEnum) {
    @Json
    enum class TestEnum {
        VAL1, VAL2
    }
}
