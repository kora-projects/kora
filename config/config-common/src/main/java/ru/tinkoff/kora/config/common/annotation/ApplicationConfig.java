package ru.tinkoff.kora.config.common.annotation;

import ru.tinkoff.kora.common.Tag;

/**
 * <b>Русский</b>: Специальный тег {@link Tag} который используется для внедрения конфигурации всего приложения.
 * <hr>
 * <b>English</b>: A special {@link Tag} that is used to embed the configuration of the entire application.
 */
@Tag(ApplicationConfig.class)
public @interface ApplicationConfig {}
