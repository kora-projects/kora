plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    compileOnly(libs.netty.transport.epoll)
    compileOnly(libs.netty.transport.kqueue)
    compileOnly(libs.netty.transport.uring)

    api(projects.core.common)
    api(projects.config.configCommon)
    api(libs.bundles.netty)
}
