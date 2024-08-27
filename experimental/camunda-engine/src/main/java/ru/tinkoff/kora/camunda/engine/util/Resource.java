package ru.tinkoff.kora.camunda.engine.util;

import java.io.InputStream;

public interface Resource {

    String name();

    String path();

    InputStream asInputStream();
}
