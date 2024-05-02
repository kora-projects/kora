package ru.tinkoff.kora.s3.client.model;

import java.io.InputStream;

public interface S3Body {

    InputStream asInputStream();

    byte[] asBytes();
}
