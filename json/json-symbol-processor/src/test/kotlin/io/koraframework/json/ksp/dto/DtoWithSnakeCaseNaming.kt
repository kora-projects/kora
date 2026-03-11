package io.koraframework.json.ksp.dto

import io.koraframework.common.NamingStrategy
import io.koraframework.common.naming.SnakeCaseNameConverter
import io.koraframework.json.common.annotation.Json

@Json
@NamingStrategy(SnakeCaseNameConverter::class)
data class DtoWithSnakeCaseNaming(val stringField: String, val integerField: Int)
