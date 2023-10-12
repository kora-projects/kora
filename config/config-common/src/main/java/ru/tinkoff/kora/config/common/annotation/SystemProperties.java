package ru.tinkoff.kora.config.common.annotation;

import ru.tinkoff.kora.common.Tag;

/**
 * <b>Русский</b>: Специальный тег {@link Tag} который используется для внедрения конфигурации системных свойств.
 * <hr>
 * <b>English</b>: A special {@link Tag} that is used to enforce the configuration of system properties.
 */
@Tag(SystemProperties.class)
public @interface SystemProperties {}
