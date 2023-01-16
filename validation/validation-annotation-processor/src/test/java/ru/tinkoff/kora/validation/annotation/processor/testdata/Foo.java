package ru.tinkoff.kora.validation.annotation.processor.testdata;

import ru.tinkoff.kora.validation.common.annotation.NotEmpty;
import ru.tinkoff.kora.validation.common.annotation.Pattern;
import ru.tinkoff.kora.validation.common.annotation.Range;
import ru.tinkoff.kora.validation.common.annotation.Validated;

import javax.annotation.Nullable;
import java.time.OffsetDateTime;

@Validated
public record Foo(@NotEmpty @Pattern("\\d+") String number,
                  @Range(from = 1, to = 10, boundary = Range.Boundary.INCLUSIVE_EXCLUSIVE) long code,
                  @Nullable OffsetDateTime timestamp,
                  @Validated @Nullable Bar bar) {}
