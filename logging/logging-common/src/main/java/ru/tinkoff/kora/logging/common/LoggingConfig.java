package ru.tinkoff.kora.logging.common;

import java.util.Map;

/**
 * @param levels Logging levels for ROOT, packages and specific classes.
 */
public record LoggingConfig(Map<String, String> levels) {}
