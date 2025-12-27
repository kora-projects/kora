package ru.tinkoff.kora.http.client.ok;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.http.client.common.HttpClientConnectionException;
import ru.tinkoff.kora.http.client.common.HttpClientEncoderException;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;

import java.io.IOException;
import java.util.Objects;

@NullMarked
public final class OkHttpRequestBody extends RequestBody {
    private final HttpBodyOutput bodyOutput;

    public OkHttpRequestBody(HttpBodyOutput bodyOutput) {
        this.bodyOutput = Objects.requireNonNull(bodyOutput);
    }

    @Nullable
    @Override
    public MediaType contentType() {
        var contentType = bodyOutput.contentType();
        if (contentType == null) {
            return null;
        }
        return MediaType.get(contentType);
    }

    @Override
    public long contentLength() {
        return bodyOutput.contentLength();
    }

    @Override
    public void writeTo(BufferedSink bufferedSink) {
        try {
            bodyOutput.write(bufferedSink.outputStream());
        } catch (IOException e) {
            throw new HttpClientConnectionException(e);
        } catch (Exception e) {
            throw new HttpClientEncoderException(e);
        }
    }
}
