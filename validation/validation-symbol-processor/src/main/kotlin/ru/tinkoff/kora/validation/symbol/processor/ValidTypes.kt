package ru.tinkoff.kora.validation.symbol.processor

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName

object ValidTypes {

    val jsonNullable = ClassName("ru.tinkoff.kora.json.common", "JsonNullable")
    val MEMBER_LIST_OF = MemberName("kotlin.collections", "mutableListOf")

    val VALID_TYPE = ClassName("ru.tinkoff.kora.validation.common.annotation", "Valid")
    val VALIDATE_TYPE = ClassName("ru.tinkoff.kora.validation.common.annotation", "Validate")
    val VALIDATED_BY_TYPE = ClassName("ru.tinkoff.kora.validation.common.annotation", "ValidatedBy")
    val CONTEXT_TYPE = ClassName("ru.tinkoff.kora.validation.common", "ValidationContext")
    val VALIDATOR_TYPE = ClassName("ru.tinkoff.kora.validation.common", "Validator")
    val VIOLATION_TYPE = ClassName("ru.tinkoff.kora.validation.common", "Violation")
    val EXCEPTION_TYPE = ClassName("ru.tinkoff.kora.validation.common", "ViolationException")
}
