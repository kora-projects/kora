package io.koraframework.logging.annotation.processor.aop;

import com.palantir.javapoet.ClassName;

public class LogAspectClassNames {
    public static final ClassName log = ClassName.get("io.koraframework.logging.common.annotation", "Log");
    public static final ClassName logIn = log.nestedClass("in");
    public static final ClassName logOut = log.nestedClass("out");
    public static final ClassName logOff = log.nestedClass("off");
    public static final ClassName logResult = log.nestedClass("result");
    public static final ClassName mask = ClassName.get("io.koraframework.logging.common.annotation", "Mask");
    public static final ClassName json = ClassName.get("io.koraframework.json.common.annotation", "Json");
    public static final ClassName jsonWriter = ClassName.get("io.koraframework.json.common.annotation", "JsonWriter");
    public static final ClassName jsonField = ClassName.get("io.koraframework.json.common.annotation", "JsonField");
    public static final ClassName jsonSkip = ClassName.get("io.koraframework.json.common.annotation", "JsonSkip");
    public static final ClassName structuredArgument = ClassName.get("io.koraframework.logging.common.arg", "StructuredArgument");
    public static final ClassName structuredArgumentMapper = ClassName.get("io.koraframework.logging.common.arg", "StructuredArgumentMapper");
    public static final ClassName maskingMetadata = ClassName.get("io.koraframework.logging.common.masking", "MaskingMetadata");
    public static final ClassName maskingClassMeta = ClassName.get("io.koraframework.logging.common.masking", "MaskingClassMeta");
    public static final ClassName maskingFieldMeta = ClassName.get("io.koraframework.logging.common.masking", "MaskingFieldMeta");
    public static final ClassName maskRule = ClassName.get("io.koraframework.logging.common.masking", "MaskRule");

    public static final ClassName loggerFactory = ClassName.get("org.slf4j", "ILoggerFactory");
    public static final ClassName logger = ClassName.get("org.slf4j", "Logger");
}
