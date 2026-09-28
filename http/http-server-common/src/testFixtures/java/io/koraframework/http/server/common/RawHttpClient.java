package io.koraframework.http.server.common;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal HTTP/1.1 client over one socket: keep-alive, pipelining, reading headers and body separately
 * and abrupt disconnects, none of which OkHttp exposes.
 */
public final class RawHttpClient implements AutoCloseable {

    public record Response(int code, Map<String, String> headers, byte[] body) {
        public String header(String name) {
            return this.headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    public record Head(int code, Map<String, String> headers) {
        public String header(String name) {
            return this.headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public RawHttpClient(int port) throws IOException {
        this(port, 0);
    }

    public RawHttpClient(int port, int receiveBufferSize) throws IOException {
        this.socket = new Socket();
        if (receiveBufferSize > 0) {
            this.socket.setReceiveBufferSize(receiveBufferSize);
        }
        this.socket.setSoTimeout(10_000);
        this.socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
        this.in = new BufferedInputStream(this.socket.getInputStream());
        this.out = this.socket.getOutputStream();
    }

    public static String request(String method, String path) {
        return method + " " + path + " HTTP/1.1\r\nHost: localhost\r\n\r\n";
    }

    public void send(String... requests) throws IOException {
        this.out.write(String.join("", requests).getBytes(StandardCharsets.ISO_8859_1));
        this.out.flush();
    }

    public Response exchange(String method, String path) throws IOException {
        this.send(request(method, path));
        return this.readResponse(method.equals("HEAD"));
    }

    public Response readResponse(boolean headRequest) throws IOException {
        var head = this.readHead();
        var body = headRequest || head.code() == 204 || head.code() == 304
            ? new byte[0]
            : this.readBody(head);
        return new Response(head.code(), head.headers(), body);
    }

    public Head readHead() throws IOException {
        var statusLine = this.readLine();
        var code = Integer.parseInt(statusLine.split(" ", 3)[1]);
        var headers = new HashMap<String, String>();
        for (var line = this.readLine(); !line.isEmpty(); line = this.readLine()) {
            var colon = line.indexOf(':');
            headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
        }
        return new Head(code, headers);
    }

    public byte[] readBody(Head head) throws IOException {
        var contentLength = head.header("content-length");
        if (contentLength != null) {
            return this.in.readNBytes(Integer.parseInt(contentLength));
        }
        if ("chunked".equalsIgnoreCase(head.header("transfer-encoding"))) {
            return this.readChunked();
        }
        return this.in.readAllBytes();
    }

    public byte[] readBytes(int count) throws IOException {
        var bytes = this.in.readNBytes(count);
        if (bytes.length != count) {
            throw new EOFException("Expected " + count + " bytes, got " + bytes.length);
        }
        return bytes;
    }

    /**
     * Closes with RST instead of FIN, so the server sees the peer disappear mid-response.
     */
    public void abort() throws IOException {
        this.socket.setSoLinger(true, 0);
        this.socket.close();
    }

    @Override
    public void close() throws IOException {
        this.socket.close();
    }

    private byte[] readChunked() throws IOException {
        var body = new ByteArrayOutputStream();
        while (true) {
            var sizeLine = this.readLine();
            var extension = sizeLine.indexOf(';');
            var size = Integer.parseInt((extension < 0 ? sizeLine : sizeLine.substring(0, extension)).trim(), 16);
            if (size == 0) {
                // trailers until the empty line
                while (!this.readLine().isEmpty()) {
                }
                return body.toByteArray();
            }
            body.write(this.readBytes(size));
            if (!this.readLine().isEmpty()) {
                throw new IOException("Malformed chunk terminator");
            }
        }
    }

    private String readLine() throws IOException {
        var line = new ByteArrayOutputStream(64);
        while (true) {
            var b = this.in.read();
            if (b < 0) {
                throw new EOFException("Connection closed while reading a line");
            }
            if (b == '\r') {
                if (this.in.read() != '\n') {
                    throw new IOException("Malformed line ending");
                }
                return line.toString(StandardCharsets.ISO_8859_1);
            }
            line.write(b);
        }
    }
}
