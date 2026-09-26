plugins {
    alias(libs.plugins.kora.kotlin)
}

dependencies {
    api(projects.core.koraAppSymbolProcessor)
    api(projects.aop.aopSymbolProcessor)
    api(projects.config.configSymbolProcessor)
    api(projects.json.jsonSymbolProcessor)
    api(projects.http.httpServerSymbolProcessor)
    api(projects.kafka.kafkaSymbolProcessor)
    api(projects.http.httpClientSymbolProcessor)
    api(projects.http.soapClientSymbolProcessor)
    api(projects.database.databaseSymbolProcessor)
    api(projects.scheduling.schedulingSymbolProcessor)
    api(projects.resilient.resilientSymbolProcessor)
    api(projects.cache.cacheSymbolProcessor)
    api(projects.validation.validationSymbolProcessor)
    api(projects.mapping.mapstructKspExtension)
    api(projects.mapping.konvertKspExtension)
    api(projects.logging.loggingSymbolProcessor)
    api(projects.grpc.grpcClientSymbolProcessor)
    api(projects.experimental.s3ClientSymbolProcessor)
    api(projects.experimental.camundaZeebeWorkerSymbolProcessor)
}
