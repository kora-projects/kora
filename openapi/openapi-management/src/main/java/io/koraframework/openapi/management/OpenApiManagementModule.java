package io.koraframework.openapi.management;

import io.koraframework.common.annotation.FactoryModule;

public interface OpenApiManagementModule {

    @FactoryModule
    default OpenApiManagementFactoryModule openApiManagementFactoryModule() {
        return new OpenApiManagementFactoryModule("openapi.management");
    }
}
