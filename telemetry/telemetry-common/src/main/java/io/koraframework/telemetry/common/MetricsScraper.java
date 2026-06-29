package io.koraframework.telemetry.common;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

public interface MetricsScraper {

    void scrape(OutputStream os) throws IOException;
}
