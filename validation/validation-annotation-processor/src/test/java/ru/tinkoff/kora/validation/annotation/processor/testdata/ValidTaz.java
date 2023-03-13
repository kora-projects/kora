package ru.tinkoff.kora.validation.annotation.processor.testdata;

import ru.tinkoff.kora.validation.common.annotation.Pattern;
import ru.tinkoff.kora.validation.common.annotation.Valid;

@Valid
public record ValidTaz(@Pattern("\\d+") String number) {}
