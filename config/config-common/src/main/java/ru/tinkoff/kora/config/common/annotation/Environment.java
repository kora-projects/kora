package ru.tinkoff.kora.config.common.annotation;

import ru.tinkoff.kora.common.Tag;

/**
 * <b>Русский</b>: Специальный тег {@link Tag} который используется для внедрения конфигурации окружения переменных.
 * <hr>
 * <b>English</b>: A special {@link Tag} tag that is used to enforce variable environment configuration.
 */
@Tag(Environment.class)
public @interface Environment {}
