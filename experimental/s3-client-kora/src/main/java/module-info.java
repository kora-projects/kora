import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.sthree.client.kora {
    requires transitive kora.common;
    requires transitive kora.config.common;
    requires transitive kora.http.client.common;
    requires transitive kora.telemetry.common;
    requires kora.logging.common;
    requires java.xml;

    exports io.koraframework.s3.client.kora;
    exports io.koraframework.s3.client.kora.annotation;
    exports io.koraframework.s3.client.kora.exception;
    exports io.koraframework.s3.client.kora.impl;
    exports io.koraframework.s3.client.kora.impl.xml;
    exports io.koraframework.s3.client.kora.model;
    exports io.koraframework.s3.client.kora.model.request;
    exports io.koraframework.s3.client.kora.model.response;
    exports io.koraframework.s3.client.kora.telemetry;
    exports io.koraframework.s3.client.kora.telemetry.impl;
}
