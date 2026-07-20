package io.koraframework.grpc.server.telemetry.impl;

import com.google.protobuf.MessageOrBuilder;
import org.jspecify.annotations.Nullable;

public class DefaultGrpcServerBodyConverter {

    @Nullable
    public String convertRequestMessage(Object message) {
        return convertMessage(message);
    }

    @Nullable
    public String convertResponseMessage(Object message) {
        return convertMessage(message);
    }

    @Nullable
    protected String convertMessage(Object message) {
        if (message instanceof MessageOrBuilder messageOrBuilder) {
            return messageOrBuilder.toString();
        }
        if (message instanceof CharSequence charSequence) {
            return charSequence.toString();
        }
        return null;
    }
}
