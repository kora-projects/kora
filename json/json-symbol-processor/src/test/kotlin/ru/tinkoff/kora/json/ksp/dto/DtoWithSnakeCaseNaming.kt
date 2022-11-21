package ru.tinkoff.kora.json.ksp.dto

import ru.tinkoff.kora.common.NamingStrategy
import ru.tinkoff.kora.common.naming.SnakeCaseNameConverter
import ru.tinkoff.kora.json.common.annotation.Json
import java.util.*

@Json
@NamingStrategy(SnakeCaseNameConverter::class)
data class DtoWithSnakeCaseNaming(val stringField: String, val integerField: Int)
