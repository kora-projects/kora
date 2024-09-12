package ru.tinkoff.kora.camunda.engine.configurator;

import java.io.InputStream;

interface Resource {

    String name();

    String path();

    InputStream asInputStream();
}
