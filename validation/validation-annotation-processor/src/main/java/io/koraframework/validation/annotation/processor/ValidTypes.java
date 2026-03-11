package io.koraframework.validation.annotation.processor;

import com.palantir.javapoet.ClassName;

public final class ValidTypes {

    private ValidTypes() {}

    public static final ClassName jsonNullable = ClassName.get("io.koraframework.json.common", "JsonNullable");
    public static final ClassName violation = ClassName.get("io.koraframework.validation.common", "Violation");
    public static final ClassName violationException = ClassName.get("io.koraframework.validation.common", "ViolationException");

    public static final ClassName VALID_TYPE = ClassName.get("io.koraframework.validation.common.annotation", "Valid");
    public static final ClassName VALIDATED_BY_TYPE = ClassName.get("io.koraframework.validation.common.annotation", "ValidatedBy");
    public static final ClassName CONTEXT_TYPE = ClassName.get("io.koraframework.validation.common", "ValidationContext");
    public static final ClassName VALIDATOR_TYPE = ClassName.get("io.koraframework.validation.common", "Validator");
    public static final ClassName VIOLATION_TYPE = ClassName.get("io.koraframework.validation.common", "Violation");
    public static final ClassName EXCEPTION_TYPE = ClassName.get("io.koraframework.validation.common", "ViolationException");
}
