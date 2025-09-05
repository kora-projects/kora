package ru.tinkoff.kora.logging.aspect;

import com.palantir.javapoet.ClassName;

public class LogAspectClassNames {
    public static final ClassName log = ClassName.get("ru.tinkoff.kora.logging.common.annotation", "Log");
    public static final ClassName logIn = log.nestedClass("in");
    public static final ClassName logOut = log.nestedClass("out");
    public static final ClassName logOff = log.nestedClass("off");
    public static final ClassName logResult = log.nestedClass("result");
    public static final ClassName structuredArgument = ClassName.get("ru.tinkoff.kora.logging.common.arg", "StructuredArgument");
    public static final ClassName structuredArgumentMapper = ClassName.get("ru.tinkoff.kora.logging.common.arg", "StructuredArgumentMapper");

    public static final ClassName loggerFactory = ClassName.get("org.slf4j", "ILoggerFactory");
    public static final ClassName logger = ClassName.get("org.slf4j", "Logger");
}
