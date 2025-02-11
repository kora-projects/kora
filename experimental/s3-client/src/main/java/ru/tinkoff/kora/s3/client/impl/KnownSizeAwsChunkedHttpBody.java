package ru.tinkoff.kora.s3.client.impl;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.concurrent.Flow;

class KnownSizeAwsChunkedHttpBody implements HttpBodyOutput {
    // <hex len>;chunk-signature=b6c6ea8a5354eaf15b3cb7646744f4275b71ea724fed81ceb9323e279d449df9\r\n<data>\r\n
    private static final int AWS_CHUNK_SUFFIX_SIZE = 85;
    // 0;chunk-signature=b6c6ea8a5354eaf15b3cb7646744f4275b71ea724fed81ceb9323e279d449df9\r\n\r\n
    private static final int AWS_CHUNK_ZERO_HEADER_SIZE = AWS_CHUNK_SUFFIX_SIZE + 1;
    // x-amz-checksum-sha256:YeJXRE0UIVNLcFUlWAzyOcOh/42uhL5Q1Az8JdFNNus=\r\n
    // x-amz-trailer-signature:63bddb248ad2590c92712055f51b8e78ab024eead08276b24f010b0efd74843f
    private static final int SHA256_TRAILER_SIZE = 159;

    private final S3RequestSigner signer;
    private final String contentType;
    private final InputStream is;
    private final MessageDigest sha256 = DigestUtils.sha256();
    private final long contentLength;
    private final byte[] buf;
    final ZonedDateTime date;
    @Nullable
    private String shaBase64;
    String previousSignature;

    static long calculateFullBodyLength(long uploadChunkSize, String uploadChunkSizeHex, long size, @Nullable String sha256Base64) {
        var lastDataChunkSize = size % uploadChunkSize;
        var fullChunks = (long) Math.floor((double) size / uploadChunkSize);
        var fullChunkSize = uploadChunkSizeHex.length() + AWS_CHUNK_SUFFIX_SIZE + uploadChunkSize;
        var lastChunkSize = lastDataChunkSize == 0 ? 0 : lastDataChunkSize + Long.toHexString(lastDataChunkSize).length() + AWS_CHUNK_SUFFIX_SIZE;
        var trailer = sha256Base64 == null ? SHA256_TRAILER_SIZE : 0;

        return fullChunks * fullChunkSize
            + lastChunkSize
            + trailer
            + AWS_CHUNK_ZERO_HEADER_SIZE;
    }

    KnownSizeAwsChunkedHttpBody(S3RequestSigner signer, int uploadChunkSize, String uploadChunkSizeHex, String contentType, InputStream is, long streamLength, @Nullable String sha256Base64) {
        this.signer = signer;
        this.date = ZonedDateTime.now(ZoneOffset.UTC);
        this.contentType = contentType;
        this.is = is;
        this.buf = new byte[uploadChunkSize];
        this.contentLength = calculateFullBodyLength(uploadChunkSize, uploadChunkSizeHex, streamLength, sha256Base64);
        this.shaBase64 = sha256Base64;
    }

    @Override
    public long contentLength() {
        return contentLength;
    }

    @Override
    @Nullable
    public String contentType() {
        return this.contentType;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        throw new IllegalStateException();
    }

    @Override
    public void close() throws IOException {
        is.close();
    }

    @Override
    public void write(OutputStream os) throws IOException {
        while (true) {
            var read = is.readNBytes(buf, 0, buf.length);
            if (read <= 0) {
                previousSignature = signer.processFinalChunk(date, previousSignature, os);
                break;
            }
            if (this.shaBase64 == null) {
                sha256.update(buf, 0, read);
            }
            previousSignature = signer.processChunk(date, previousSignature, buf, read, os);
            os.flush();
            if (read < buf.length) {
                previousSignature = signer.processFinalChunk(date, previousSignature, os);
                break;
            }
        }
        if (shaBase64 == null) {
            this.shaBase64 = Base64.getEncoder().encodeToString(sha256.digest());
            signer.processTrailer(date, previousSignature, shaBase64, os);
        } else {
            os.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        }
    }

    public String sha256() {
        if (this.shaBase64 != null) {
            return this.shaBase64;
        }
        throw new IllegalStateException("Can't get sha256 hash before body has been written");
    }
}
