package io.koraframework.validation.annotation.processor.testdata;

import org.jspecify.annotations.Nullable;
import io.koraframework.validation.common.annotation.NotEmpty;
import io.koraframework.validation.common.annotation.Pattern;
import io.koraframework.validation.common.annotation.Range;
import io.koraframework.validation.common.annotation.Valid;

import java.time.OffsetDateTime;

@Valid
public record ValidFoo(@NotEmpty @Pattern("\\d+") String number,
                       @Range(from = 1L, to = Long.MAX_VALUE, boundary = Range.Boundary.INCLUSIVE_EXCLUSIVE) long code,
                       @Nullable OffsetDateTime timestamp,
                       @Nullable @Valid ValidBar bar) {

    public static final String IGNORED = "ops";
}
