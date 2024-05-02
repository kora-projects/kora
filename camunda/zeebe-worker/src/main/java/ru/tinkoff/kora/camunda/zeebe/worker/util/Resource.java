package ru.tinkoff.kora.camunda.zeebe.worker.util;

import java.io.InputStream;

public interface Resource {

    String name();

    String path();

    InputStream asInputStream();
}
