package ru.tinkoff.kora.annotation.processor.common.compile;


import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class InTestGeneratedJavaFileObject implements JavaFileObject {
    private final Kind kind;
    private final String className;
    private final Path file;

    public InTestGeneratedJavaFileObject(Kind kind, String className, Path file) {
        this.kind = kind;
        this.className = className;
        this.file = file;
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
        return Files.newInputStream(this.file);
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        return Files.newOutputStream(this.file, StandardOpenOption.CREATE_NEW);
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
        return new InputStreamReader(this.openInputStream());
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        return Files.readString(this.file);
    }

    @Override
    public Writer openWriter() throws IOException {
        return new OutputStreamWriter(this.openOutputStream(), StandardCharsets.UTF_8);
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
