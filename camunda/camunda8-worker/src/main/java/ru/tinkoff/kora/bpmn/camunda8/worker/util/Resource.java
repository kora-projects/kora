package ru.tinkoff.kora.bpmn.camunda8.worker.util;

import java.io.InputStream;

public interface Resource {

    String name();

    String path();

    InputStream asInputStream();
}
