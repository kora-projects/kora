package ru.tinkoff.kora.http.client.common.request;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.client.common.writer.StringParameterConverter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class UriQueryBuilder {
    private final StringBuilder sb = new StringBuilder();
    private final boolean startFromQMark;
    private final boolean startFromAmp;
    private int counter = 0;

    public UriQueryBuilder(boolean startFromQMark, boolean startFromAmp) {
        this.startFromQMark = startFromQMark;
        this.startFromAmp = startFromAmp;
    }

    public void add(String queryParameterName) {
        var sb = this.sb;
        if (counter == 0) {
            if (startFromQMark) {
                sb.append('?');
            } else if (startFromAmp) {
                sb.append('&');
            }
        } else {
            sb.append('&');
        }
        sb.append(queryParameterName);
        counter++;
    }

    public void add(String queryParameterName, @Nullable String value) {
        if (value == null) {
            return;
        }
        var sb = this.sb;
        if (counter == 0) {
            if (startFromQMark) {
                sb.append('?');
            } else if (startFromAmp) {
                sb.append('&');
            }
        } else {
            sb.append('&');
        }
        sb.append(queryParameterName);
        sb.append('=');
        sb.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        counter++;
    }

    public <T> void add(String queryParameterName, StringParameterConverter<T> converter, @Nullable T value) {
        if (value == null) {
            return;
        }
        var sb = this.sb;
        if (counter == 0) {
            if (startFromQMark) {
                sb.append('?');
            } else if (startFromAmp) {
                sb.append('&');
            }
        } else {
            sb.append('&');
        }
        sb.append(queryParameterName);
        sb.append('=');
        var string = converter.convert(value);
        sb.append(URLEncoder.encode(string, StandardCharsets.UTF_8));
        counter++;
    }

    public String build() {
        return this.sb.toString();
    }
}
