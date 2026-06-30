package io.koraframework.validation.symbol.processor

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName

object ValidTypes {

    val jsonValue = ClassName("io.koraframework.json.common", "JsonValue")
    val jsonNullable = ClassName("io.koraframework.json.common", "JsonNullable")
    val jsonUndefined = ClassName("io.koraframework.json.common", "JsonUndefined")
    val MEMBER_LIST_OF = MemberName("kotlin.collections", "mutableListOf")

    val VALID_TYPE = ClassName("io.koraframework.validation.common.annotation", "Valid")
    val VALIDATE_TYPE = ClassName("io.koraframework.validation.common.annotation", "Validate")
    val VALIDATED_BY_TYPE = ClassName("io.koraframework.validation.common.annotation", "ValidatedBy")
    val CONTEXT_TYPE = ClassName("io.koraframework.validation.common", "ValidationContext")
    val VALIDATOR_TYPE = ClassName("io.koraframework.validation.common", "Validator")
    val VIOLATION_TYPE = ClassName("io.koraframework.validation.common", "Violation")
    val EXCEPTION_TYPE = ClassName("io.koraframework.validation.common", "ViolationException")
}
