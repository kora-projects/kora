package ru.tinkoff.kora.http.client.common.form;

import reactor.core.Fuseable;
import reactor.core.publisher.Flux;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestBuilder;
import ru.tinkoff.kora.http.common.form.FormMultipart;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class MultipartWriter {
    private static final ByteBuffer RN_BUF = StandardCharsets.US_ASCII.encode("\r\n");

    public static HttpClientRequestBuilder write(HttpClientRequestBuilder b, List<? extends FormMultipart.FormPart> parts) {
        return write(b, "blob:" + UUID.randomUUID(), parts);
    }

    public static HttpClientRequestBuilder write(HttpClientRequestBuilder b, String boundary, List<? extends FormMultipart.FormPart> parts) {
        var boundaryBuff = StandardCharsets.US_ASCII.encode("--" + boundary);

        var body = Flux.fromIterable(parts).concatMap(part -> {
            final String contentDisposition;
            final String contentType;
            final Flux<ByteBuffer> content;
            if (part instanceof FormMultipart.FormPart.MultipartData data) {
                contentDisposition = "content-disposition: form-data; name=\"" + part.name() + "\"\r\n";
                contentType = "text/plain; charset=utf-8";
                var dataBuf = StandardCharsets.UTF_8.encode(data.content());
                content = Flux.just(dataBuf);
            } else if (part instanceof FormMultipart.FormPart.MultipartFile file) {
                if (file.fileName() != null) {
                    contentDisposition = "content-disposition: form-data;"
                                         + " name=\"" + part.name() + "\""
                                         + "; filename=\"" + file.fileName() + "\""
                                         + "\r\n";
                } else {
                    contentDisposition = "content-disposition: form-data; name=\"" + part.name() + "\"\r\n";
                }
                contentType = file.contentType() != null
                    ? file.contentType()
                    : "application/octet-stream";
                content = Flux.just(ByteBuffer.wrap(file.content()));
            } else if (part instanceof FormMultipart.FormPart.MultipartFileStream stream) {
                if (stream.fileName() != null) {
                    contentDisposition = "content-disposition: form-data;"
                                         + " name=\"" + part.name() + "\""
                                         + "; filename=\"" + stream.fileName() + "\""
                                         + "\r\n";
                } else {
                    contentDisposition = "content-disposition: form-data; name=\"" + part.name() + "\"\r\n";
                }
                contentType = stream.contentType() != null
                    ? stream.contentType()
                    : "application/octet-stream";
                content = stream.content();
            } else {
                // never gonna happen
                throw new IllegalStateException("Invalid sealed interface impl: " + part.getClass());
            }
            var contentDispositionBuff = StandardCharsets.US_ASCII.encode(contentDisposition);
            var contentTypeBuff = StandardCharsets.US_ASCII.encode("content-type: " + contentType + "\r\n");
            if (content instanceof Fuseable.ScalarCallable<?> scalarCallable) {
                try {
                    var data = (ByteBuffer) scalarCallable.call();

                    return Flux.just(boundaryBuff.slice(), RN_BUF.slice(), contentDispositionBuff, contentTypeBuff, RN_BUF.slice(), data, RN_BUF.slice());
                } catch (Exception e) {
                    return Flux.error(e);
                }
            }
            return Flux.just(boundaryBuff.slice(), RN_BUF.slice(), contentDispositionBuff, contentTypeBuff, RN_BUF.slice()).concatWith(content).concatWithValues(RN_BUF.slice());
        }).concatWith(Flux.just(boundaryBuff.slice(), StandardCharsets.US_ASCII.encode("--")));

        return b.header("content-type", "multipart/form-data;boundary=\"" + boundary + "\"")
            .body(body);
    }
}
