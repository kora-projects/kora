package ru.tinkoff.grpc.client;

import io.grpc.ChannelCredentials;
import io.grpc.ManagedChannelBuilder;
import io.grpc.okhttp.OkHttpChannelBuilder;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.common.util.Configurer;

public final class GrpcOkHttpClientChannelFactory implements GrpcClientChannelFactory {

    @Nullable
    private final Configurer<ManagedChannelBuilder<?>> configurer;

    public GrpcOkHttpClientChannelFactory(@Nullable Configurer<ManagedChannelBuilder<?>> configurer) {
        this.configurer = configurer;
    }

    @Override
    public ManagedChannelBuilder<?> forAddress(String host, int port) {
        if (this.configurer != null) {
            return this.configurer.configure(OkHttpChannelBuilder.forAddress(host, port));
        }
        return OkHttpChannelBuilder.forAddress(host, port);
    }

    @Override
    public ManagedChannelBuilder<?> forAddress(String host, int port, ChannelCredentials creds) {
        if (this.configurer != null) {
            return this.configurer.configure(OkHttpChannelBuilder.forAddress(host, port, creds));
        }
        return OkHttpChannelBuilder.forAddress(host, port, creds);
    }

    @Override
    public ManagedChannelBuilder<?> forTarget(String target) {
        if (this.configurer != null) {
            return this.configurer.configure(OkHttpChannelBuilder.forTarget(target));
        }
        return OkHttpChannelBuilder.forTarget(target);
    }

    @Override
    public ManagedChannelBuilder<?> forTarget(String target, ChannelCredentials creds) {
        if (this.configurer != null) {
            return this.configurer.configure(OkHttpChannelBuilder.forTarget(target, creds));
        }
        return OkHttpChannelBuilder.forTarget(target, creds);
    }
}
