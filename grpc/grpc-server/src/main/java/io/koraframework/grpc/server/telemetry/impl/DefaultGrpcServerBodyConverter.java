package io.koraframework.grpc.server.telemetry.impl;

import com.google.protobuf.MessageOrBuilder;
import io.grpc.Metadata;
import org.jspecify.annotations.Nullable;

public class DefaultGrpcServerBodyConverter {

    @Nullable
    public String convertRequestMessage(String service, String method, Metadata requestHeaders, @Nullable Object requestMessage) {
        return convertMessage(requestMessage);
    }

    @Nullable
    public String convertResponseMessage(Object message) {
        return convertMessage(message);
    }

    @Nullable
    protected String convertMessage(@Nullable Object message) {
        if (message instanceof MessageOrBuilder messageOrBuilder) {
            return messageOrBuilder.toString();
        }
        if (message instanceof CharSequence charSequence) {
            return charSequence.toString();
        }
        return null;
    }
}
