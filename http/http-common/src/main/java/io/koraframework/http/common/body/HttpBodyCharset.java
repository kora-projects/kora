package io.koraframework.http.common.body;

import org.jspecify.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class HttpBodyCharset {

    private HttpBodyCharset() { }

    /**
     * @return charset from the {@code charset} parameter of the Content-Type value,
     * or UTF-8 when the parameter is absent, illegal or unsupported
     */
    public static Charset fromContentType(@Nullable String contentType) {
        if (contentType == null) {
            return StandardCharsets.UTF_8;
        }
        var params = contentType.split(";");
        for (int i = 1; i < params.length; i++) {
            var param = params[i].strip();
            if (param.regionMatches(true, 0, "charset=", 0, 8)) {
                var name = param.substring(8).strip();
                if (name.length() > 1 && name.startsWith("\"") && name.endsWith("\"")) {
                    name = name.substring(1, name.length() - 1);
                }
                try {
                    return Charset.forName(name);
                } catch (IllegalArgumentException e) {
                    return StandardCharsets.UTF_8;
                }
            }
        }
        return StandardCharsets.UTF_8;
    }
}
