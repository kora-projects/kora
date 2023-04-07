package ru.tinkoff.kora.validation.annotation.processor.testdata;

import ru.tinkoff.kora.validation.common.annotation.NotEmpty;
import ru.tinkoff.kora.validation.common.annotation.Pattern;
import ru.tinkoff.kora.validation.common.annotation.Range;
import ru.tinkoff.kora.validation.common.annotation.Valid;

import javax.annotation.Nullable;
import java.time.OffsetDateTime;

@Valid
public record ValidFoo(@NotEmpty @Pattern("\\d+") String number,
                       @Range(from = 1, to = 10, boundary = Range.Boundary.INCLUSIVE_EXCLUSIVE) long code,
                       @Nullable OffsetDateTime timestamp,
                       @Nullable @Valid ValidBar bar) {

    public static final String IGNORED = "ops";
}
