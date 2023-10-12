package ru.tinkoff.kora.common.naming;

import jakarta.annotation.Nonnull;

/**
 * <b>Русский</b>: Контракт который позволяет применять конвенцию именования строк, такие как CamelCase, snake_case, и т.д.
 * <hr>
 * <b>English</b>: A contract that allows string naming conventions such as CamelCase, snake_case, etc. to be applied.
 * <br>
 * <br>
 */
public interface NameConverter {

    @Nonnull
    String convert(@Nonnull String originalName);
}
