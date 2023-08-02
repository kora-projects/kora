package ru.tinkoff.kora.logging.aspect;

import com.squareup.javapoet.ClassName;

public class LogAspectClassNames {
    public static final ClassName log = ClassName.get("ru.tinkoff.kora.logging.common.annotation", "Log");
    public static final ClassName logIn = log.nestedClass("in");
    public static final ClassName logOut = log.nestedClass("out");
    public static final ClassName logOff = log.nestedClass("off");
    public static final ClassName logResult = log.nestedClass("result");
    public static final ClassName structuredArgument = ClassName.get("ru.tinkoff.kora.logging.common.arg", "StructuredArgument");
}
