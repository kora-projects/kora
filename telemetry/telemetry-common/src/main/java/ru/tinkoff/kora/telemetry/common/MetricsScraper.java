package ru.tinkoff.kora.telemetry.common;

import java.io.IOException;
import java.io.Writer;

public interface MetricsScraper {
    void scrape(Writer writer) throws IOException;
}
