package ru.tinkoff.kora.bpmn.camunda7.engine.util;

import java.io.InputStream;

public interface Resource {

    String name();

    String path();

    InputStream asInputStream();
}
