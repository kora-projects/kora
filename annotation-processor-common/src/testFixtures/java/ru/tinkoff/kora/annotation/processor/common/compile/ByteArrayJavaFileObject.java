package ru.tinkoff.kora.annotation.processor.common.compile;


import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class ByteArrayJavaFileObject implements JavaFileObject {
    private final Kind kind;
    private final String className;
    private final byte[] content;

    public ByteArrayJavaFileObject(Kind kind, String className, byte[] content) {
        this.kind = kind;
        this.className = className;
        this.content = content;
    }

    @Override
    public Kind getKind() {
        return this.kind;
    }

    @Override
    public boolean isNameCompatible(String simpleName, Kind kind) {
        if (!this.kind.equals(kind)) {
            return false;
        }
        if (this.className.equals(simpleName)) {
            return true;
        }
        if (this.className.endsWith(simpleName)) {
            return true;
        }
        return false;
    }

    @Override
    public NestingKind getNestingKind() {
        throw new IllegalStateException();
    }

    @Override
    public Modifier getAccessLevel() {
        throw new IllegalStateException();
    }

    @Override
    public URI toUri() {
        return URI.create("/" + this.className.replace('.', '/') + ".java");
    }

    @Override
    public String getName() {
        return this.className;
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return new ByteArrayInputStream(this.content);
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        throw new IllegalStateException();
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
        return new InputStreamReader(new ByteArrayInputStream(this.content));
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        return new String(this.content, StandardCharsets.UTF_8);
    }

    @Override
    public Writer openWriter() throws IOException {
        throw new IllegalStateException();
    }

    @Override
    public long getLastModified() {
        throw new IllegalStateException();
    }

    @Override
    public boolean delete() {
        return true;
    }
}
