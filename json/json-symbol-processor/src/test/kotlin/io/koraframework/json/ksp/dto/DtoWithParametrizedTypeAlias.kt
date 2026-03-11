package io.koraframework.json.ksp.dto

import io.koraframework.json.common.JsonNullable
import io.koraframework.json.common.annotation.Json

@Json
data class DtoWithParametrizedTypeAlias(
    val arrayList: ArrayList<String>,
    val arrayListNullable: JsonNullable<ArrayList<String>>
)
