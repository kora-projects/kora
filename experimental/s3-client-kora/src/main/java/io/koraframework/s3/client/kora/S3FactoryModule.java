package io.koraframework.s3.client.kora;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.http.client.common.HttpClient;
import io.koraframework.s3.client.kora.impl.KoraS3Client;
import io.koraframework.s3.client.kora.telemetry.S3ClientTelemetryFactory;

public class S3FactoryModule {

    private final String configPath;

    public S3FactoryModule(String configPath) {
        this.configPath = configPath;
    }

    @Tag(Tag.Factory.class)
    @DefaultComponent
    public S3HttpClientProvider defaultKoraS3httpClientProvider(HttpClient client) {
        return () -> client;
    }

    @Tag(Tag.Factory.class)
    @DefaultComponent
    public S3ClientFactory defaultKoraS3ClientFactory(S3HttpClientProvider clientProvider,
                                                      S3ClientTelemetryFactory telemetryFactory) {
        var client = clientProvider.get();
        return new S3ClientFactory() {
            @Override
            public S3Client create(S3ClientConfig config) {
                return create(configPath, KoraS3Client.class, config);
            }

            @Override
            public S3Client create(String configPath, Class<?> clientImpl, S3ClientConfig config) {
                var telemetry = telemetryFactory.get(configPath, clientImpl, config.telemetry());
                return new KoraS3Client(client, config, telemetry);
            }
        };
    }
}
