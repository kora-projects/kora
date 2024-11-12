package ru.tinkoff.kora.validation.annotation.processor;

import com.squareup.javapoet.ClassName;

public final class ValidTypes {

    private ValidTypes() {}

    public static final ClassName jsonNullable = ClassName.get("ru.tinkoff.kora.json.common", "JsonNullable");
    public static final ClassName violation = ClassName.get("ru.tinkoff.kora.validation.common", "Violation");
    public static final ClassName violationException = ClassName.get("ru.tinkoff.kora.validation.common", "ViolationException");

    public static final ClassName VALID_TYPE = ClassName.get("ru.tinkoff.kora.validation.common.annotation", "Valid");
    public static final ClassName VALIDATED_BY_TYPE = ClassName.get("ru.tinkoff.kora.validation.common.annotation", "ValidatedBy");
    public static final ClassName CONTEXT_TYPE = ClassName.get("ru.tinkoff.kora.validation.common", "ValidationContext");
    public static final ClassName VALIDATOR_TYPE = ClassName.get("ru.tinkoff.kora.validation.common", "Validator");
    public static final ClassName VIOLATION_TYPE = ClassName.get("ru.tinkoff.kora.validation.common", "Violation");
    public static final ClassName EXCEPTION_TYPE = ClassName.get("ru.tinkoff.kora.validation.common", "ViolationException");
}
