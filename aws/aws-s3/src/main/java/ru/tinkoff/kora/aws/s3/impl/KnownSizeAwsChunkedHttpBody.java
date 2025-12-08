package ru.tinkoff.kora.aws.s3.impl;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.aws.s3.S3Client;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;

class KnownSizeAwsChunkedHttpBody implements HttpBodyOutput {
    // <hex len>;chunk-signature=b6c6ea8a5354eaf15b3cb7646744f4275b71ea724fed81ceb9323e279d449df9\r\n<data>\r\n
    private static final int AWS_CHUNK_SUFFIX_SIZE = 85;
    // 0;chunk-signature=b6c6ea8a5354eaf15b3cb7646744f4275b71ea724fed81ceb9323e279d449df9\r\n\r\n
    private static final int AWS_CHUNK_ZERO_HEADER_SIZE = AWS_CHUNK_SUFFIX_SIZE + 1;
    // x-amz-checksum-sha256:YeJXRE0UIVNLcFUlWAzyOcOh/42uhL5Q1Az8JdFNNus=\r\n
    // x-amz-trailer-signature:63bddb248ad2590c92712055f51b8e78ab024eead08276b24f010b0efd74843f
    private static final int SHA256_TRAILER_SIZE = 159;

    private final AwsRequestSigner signer;
    private final S3Client.ContentWriter contentWriter;
    private final String contentType;
    private final long contentLength;
    final ZonedDateTime date;
    private final int uploadChunkSize;
    private final String rqSignature;
    @Nullable
    private String shaBase64;
    private final String region;

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

    public KnownSizeAwsChunkedHttpBody(AwsRequestSigner signer, String region, int uploadChunkSize, String contentType, String rqSignature, S3Client.ContentWriter contentWriter, @Nullable String sha256Base64) {
        this.region = region;
        this.signer = signer;
        this.contentWriter = contentWriter;
        this.date = ZonedDateTime.now(ZoneOffset.UTC);
        this.contentType = contentType;
        this.uploadChunkSize = uploadChunkSize;
        this.contentLength = calculateFullBodyLength(uploadChunkSize, Integer.toHexString(uploadChunkSize), contentWriter.length(), sha256Base64);
        this.shaBase64 = sha256Base64;
        this.rqSignature = rqSignature;
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
    public void close() throws IOException {
        this.contentWriter.close();
    }

    @Override
    public void write(OutputStream os) throws IOException {
        try (var wrappedOs = new BlaBlaOutputStream(os, new byte[uploadChunkSize], this.shaBase64 == null ? DigestUtils.sha256() : null)) {
            this.contentWriter.write(wrappedOs);
        }
    }

    private class BlaBlaOutputStream extends OutputStream {
        private final OutputStream os;
        private final byte[] buf;
        private int pos = 0;
        private String previousSignature = rqSignature;
        private boolean completed = false;
        @Nullable
        private final MessageDigest sha256;

        private BlaBlaOutputStream(OutputStream os, byte[] buf, @Nullable MessageDigest sha256) {
            this.os = os;
            this.buf = buf;
            this.sha256 = sha256;
        }

        @Override
        public void write(int b) throws IOException {
            this.write(new byte[]{(byte) b});
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            try {
                while (len > 0) {
                    var remaining = this.buf.length - this.pos;
                    var toWrite = Math.min(len, remaining);
                    System.arraycopy(b, off, this.buf, this.pos, toWrite);
                    off += toWrite;
                    len -= toWrite;
                    this.pos += toWrite;
                    if (this.pos == this.buf.length) {
                        if (this.sha256 != null) {
                            this.sha256.update(this.buf, 0, this.buf.length);
                        }
                        this.previousSignature = KnownSizeAwsChunkedHttpBody.this.processChunk(this.previousSignature, this.buf, this.pos, this.os);
                        this.pos = 0;
                    }
                }
            } catch (Throwable e) {
                this.completed = true;
                throw e;
            }

        }

        @Override
        public void close() throws IOException {
            if (!this.completed) {
                this.completed = true;
                if (this.pos > 0) {
                    if (KnownSizeAwsChunkedHttpBody.this.shaBase64 == null) {
                        this.sha256.update(this.buf, 0, this.pos);
                    }
                    this.previousSignature = KnownSizeAwsChunkedHttpBody.this.processChunk(this.previousSignature, this.buf, this.pos, this.os);
                    this.pos = 0;
                }
                this.previousSignature = KnownSizeAwsChunkedHttpBody.this.processFinalChunk(this.previousSignature, this.os);

                if (this.sha256 != null) {
                    KnownSizeAwsChunkedHttpBody.this.shaBase64 = Base64.getEncoder().encodeToString(this.sha256.digest());
                    KnownSizeAwsChunkedHttpBody.this.processTrailer(this.previousSignature, KnownSizeAwsChunkedHttpBody.this.shaBase64, os);
                } else {
                    this.os.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                }
            }
        }
    }


    private String processChunk(String previousSignature, byte[] buf, int read, OutputStream os) throws IOException {
        var amzDate = this.date.format(AwsRequestSigner.AMZ_DATE_FORMAT);
        var signerDate = this.date.format(AwsRequestSigner.SIGNER_DATE_FORMAT);

        var scope = signerDate + "/" + this.region + "/s3/aws4_request";
        var chunkContentSha = DigestUtils.sha256(buf, 0, read).hex();

        var stringToSign = "AWS4-HMAC-SHA256-PAYLOAD" + "\n"
            + amzDate + "\n"
            + scope + "\n"
            + previousSignature + "\n"
            + AwsRequestSigner.EMPTY_PAYLOAD_SHA256_HEX + "\n"
            + chunkContentSha;

        var signature = this.signer.awsSign(this.region, signerDate, stringToSign);

        os.write(Integer.toHexString(read).getBytes(StandardCharsets.US_ASCII));
        os.write(";chunk-signature=".getBytes(StandardCharsets.US_ASCII));
        os.write(signature.getBytes(StandardCharsets.US_ASCII));
        os.write(AwsRequestSigner.CLRF_BYTES);
        os.write(buf, 0, read);
        os.write(AwsRequestSigner.CLRF_BYTES);

        return signature;
    }

    private String processFinalChunk(String previousSignature, OutputStream os) throws IOException {
        var amzDate = this.date.format(AwsRequestSigner.AMZ_DATE_FORMAT);
        var signerDate = this.date.format(AwsRequestSigner.SIGNER_DATE_FORMAT);
        var scope = signerDate + "/" + this.region + "/s3/aws4_request";

        var stringToSign = "AWS4-HMAC-SHA256-PAYLOAD" + "\n"
            + amzDate + "\n"
            + scope + "\n"
            + previousSignature + "\n"
            + AwsRequestSigner.EMPTY_PAYLOAD_SHA256_HEX + "\n"
            + AwsRequestSigner.EMPTY_PAYLOAD_SHA256_HEX;

        var signature = this.signer.awsSign(this.region, signerDate, stringToSign);

        os.write("0".getBytes(StandardCharsets.US_ASCII));
        os.write(";chunk-signature=".getBytes(StandardCharsets.US_ASCII));
        os.write(signature.getBytes(StandardCharsets.US_ASCII));
        os.write(AwsRequestSigner.CLRF_BYTES);

        return signature;
    }

    private String processTrailer(String previousSignature, String sha256, OutputStream os) throws IOException {
        var amzDate = this.date.format(AwsRequestSigner.AMZ_DATE_FORMAT);
        var signerDate = this.date.format(AwsRequestSigner.SIGNER_DATE_FORMAT);

        var scope = signerDate + "/" + this.region + "/s3/aws4_request";
        var trailerBytes = ("x-amz-checksum-sha256:" + sha256 + "\n").getBytes(StandardCharsets.US_ASCII);
        var chunkContentSha = DigestUtils.sha256(trailerBytes, 0, trailerBytes.length).hex();

        var stringToSign = "AWS4-HMAC-SHA256-TRAILER" + "\n"
            + amzDate + "\n"
            + scope + "\n"
            + previousSignature + "\n"
            + chunkContentSha;

        var signature = this.signer.awsSign(this.region, signerDate, stringToSign);
        os.write(trailerBytes);
        os.write(AwsRequestSigner.CLRF_BYTES);
        os.write("x-amz-trailer-signature:".getBytes(StandardCharsets.US_ASCII));
        os.write(signature.getBytes(StandardCharsets.US_ASCII));
        os.write(AwsRequestSigner.CLRF_BYTES);
        os.write(AwsRequestSigner.CLRF_BYTES);

        return signature;
    }

    public String sha256() {
        if (this.shaBase64 != null) {
            return this.shaBase64;
        }
        throw new IllegalStateException("Can't get sha256 hash before body has been written");
    }
}
