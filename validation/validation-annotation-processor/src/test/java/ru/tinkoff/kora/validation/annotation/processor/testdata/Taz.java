package ru.tinkoff.kora.validation.annotation.processor.testdata;

import ru.tinkoff.kora.validation.common.annotation.Pattern;
import ru.tinkoff.kora.validation.common.annotation.Validated;

@Validated
public record Taz(@Pattern("\\d+") String number) {}
