package io.koraframework.http.client.common.request.form;

import io.koraframework.http.common.body.HttpBodyOutput;
import io.koraframework.http.common.form.FormMultipart;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public final class MultipartWriterUtils {

    private static final byte[] RN_BUF = "\r\n".getBytes(StandardCharsets.US_ASCII);

    private MultipartWriterUtils() {}

    public static HttpBodyOutput write(List<? extends FormMultipart.FormPart> parts) {
        return write("blob:" + UUID.randomUUID(), parts);
    }

    private static String contentDisposition(String name, @Nullable String fileName) {
        var sb = new StringBuilder("content-disposition: form-data; name=\"").append(escape(name)).append('"');
        if (fileName != null) {
            sb.append("; filename=\"").append(escape(fileName)).append('"');
        }
        return sb.append("\r\n").toString();
    }

    // same escaping as browsers use for multipart/form-data (WHATWG HTML spec)
    private static String escape(String value) {
        return value.replace("\"", "%22").replace("\r", "%0D").replace("\n", "%0A");
    }

    public static HttpBodyOutput write(String boundary, List<? extends FormMultipart.FormPart> parts) {
        return new MultipartHttpBodyOutput(boundary, parts);
    }

    private static final class MultipartHttpBodyOutput implements HttpBodyOutput {
        private final byte[] boundaryRN;
        private final byte[] boundaryDD;
        private final String boundary;
        private final List<? extends FormMultipart.FormPart> parts;

        private MultipartHttpBodyOutput(String boundary, List<? extends FormMultipart.FormPart> parts) {
            this.boundary = boundary;
            this.parts = parts;
            this.boundaryRN = ("--" + boundary + "\r\n").getBytes(StandardCharsets.US_ASCII);
            this.boundaryDD = ("--" + boundary + "--").getBytes(StandardCharsets.US_ASCII);

        }

        @Override
        public long contentLength() {
            return -1;
        }

        @Override
        public String contentType() {
            return "multipart/form-data;boundary=\"" + boundary + "\"";
        }

        @Override
        public void write(OutputStream os) throws IOException {
            for (var part : parts) {
                switch (part) {
                    case FormMultipart.FormPart.MultipartData data -> {
                        var contentDisposition = contentDisposition(part.name(), null);
                        var contentType = "text/plain;charset=utf-8";

                        os.write(boundaryRN);
                        os.write(contentDisposition.getBytes(StandardCharsets.UTF_8));
                        os.write(("content-type: " + contentType + "\r\n").getBytes(StandardCharsets.US_ASCII));
                        os.write(RN_BUF);
                        os.write(data.content().getBytes(StandardCharsets.UTF_8));
                        os.write(RN_BUF);
                    }
                    case FormMultipart.FormPart.MultipartFile(var name, var fileName, var fileContentType, var content) -> {
                        var contentDisposition = contentDisposition(part.name(), fileName);
                        var contentType = fileContentType != null
                            ? fileContentType
                            : "application/octet-stream";

                        os.write(boundaryRN);
                        os.write(contentDisposition.getBytes(StandardCharsets.UTF_8));
                        os.write(("content-type: " + contentType + "\r\n").getBytes(StandardCharsets.US_ASCII));
                        os.write(RN_BUF);
                        os.write(content);
                        os.write(RN_BUF);
                    }
                    case FormMultipart.FormPart.MultipartFileStream stream -> {
                        try (var content = stream.content()) {
                            var contentDisposition = contentDisposition(part.name(), stream.fileName());
                            var contentType = content.contentType() != null
                                ? content.contentType()
                                : "application/octet-stream";

                            os.write(boundaryRN);
                            os.write(contentDisposition.getBytes(StandardCharsets.UTF_8));
                            os.write(("content-type: " + contentType + "\r\n").getBytes(StandardCharsets.US_ASCII));
                            os.write(RN_BUF);
                            content.write(os);
                            os.write(RN_BUF);
                        }
                    }
                }
            }
            os.write(boundaryDD);
        }

        @Override
        public void close() throws IOException {
            var exception = (IOException) null;
            for (var part : this.parts) {
                if (part instanceof FormMultipart.FormPart.MultipartFileStream stream) {
                    try {
                        stream.content().close();
                    } catch (IOException e) {
                        if (exception == null) {
                            exception = e;
                        } else {
                            exception.addSuppressed(e);
                        }
                    }
                }
            }
            if (exception != null) {
                throw exception;
            }
        }
    }
}
