package io.koraframework.http.client.apache;

import io.koraframework.common.annotation.FactoryModule;
import io.koraframework.http.client.common.HttpClientModule;

public interface ApacheHttpClientModule extends HttpClientModule {

    @FactoryModule
    default ApacheHttpClientFactoryModule apacheHttpClientFactory() {
        return new ApacheHttpClientFactoryModule("httpClient");
    }
}
