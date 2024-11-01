package ru.tinkoff.kora.json.ksp.dto

import ru.tinkoff.kora.json.common.JsonNullable
import ru.tinkoff.kora.json.common.annotation.Json

@Json
data class DtoWithParametrizedTypeAlias(
    val arrayList: ArrayList<String>,
    val arrayListNullable: JsonNullable<ArrayList<String>>
)
