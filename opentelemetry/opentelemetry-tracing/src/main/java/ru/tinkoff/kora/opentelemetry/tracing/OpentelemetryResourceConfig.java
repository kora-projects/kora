package ru.tinkoff.kora.opentelemetry.tracing;

import java.util.Map;

public record OpentelemetryResourceConfig(Map<String, String> attributes) {}
