plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    api(projects.core.koraAppAnnotationProcessor)
    api(projects.aop.aopAnnotationProcessor)
    api(projects.config.configAnnotationProcessor)
    api(projects.json.jsonAnnotationProcessor)
    api(projects.http.httpServerAnnotationProcessor)
    api(projects.http.httpClientAnnotationProcessor)
    api(projects.http.soapClientAnnotationProcessor)
    api(projects.database.databaseAnnotationProcessor)
    api(projects.kafka.kafkaAnnotationProcessor)
    api(projects.scheduling.schedulingAnnotationProcessor)
    api(projects.resilient.resilientAnnotationProcessor)
    api(projects.cache.cacheAnnotationProcessor)
    api(projects.validation.validationAnnotationProcessor)
    api(projects.mapping.mapstructJavaExtension)
    api(projects.logging.loggingAnnotationProcessor)
    api(projects.grpc.grpcClientAnnotationProcessor)
    api(projects.experimental.s3ClientAnnotationProcessor)
    api(projects.experimental.camundaZeebeWorkerAnnotationProcessor)
}
