import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.soap.client {
    requires transitive kora.http.client.common;
    requires transitive kora.telemetry.common;
    requires transitive kora.config.common;
    requires transitive kora.common;
    requires transitive jakarta.xml.ws;
    requires transitive jakarta.xml.bind;
    requires transitive org.glassfish.jaxb.runtime;
    requires transitive org.apache.commons.codec;

    exports io.koraframework.soap.client.common;
    exports io.koraframework.soap.client.common.envelope;
    exports io.koraframework.soap.client.common.exception;
    exports io.koraframework.soap.client.common.jakarta;
    exports io.koraframework.soap.client.common.telemetry;
    exports io.koraframework.soap.client.common.telemetry.impl;
    exports io.koraframework.soap.client.common.util;
}
