package io.koraframework.validation.annotation.processor.testdata;

import io.koraframework.validation.common.annotation.Pattern;
import io.koraframework.validation.common.annotation.Valid;

@Valid
public record ValidTaz(@Pattern("\\d+") String number) {

    public static final String IGNORED = "ops";
}
