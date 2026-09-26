project.providers.environmentVariable("KORA_VERSION").orNull?.let {
    project.version = it
}
plugins {
    id("io.koraframework.kora-root-publish")
    id("io.koraframework.kora-root-coverage")
    id("io.koraframework.kora-root-ci-test")
    id("io.koraframework.kora-dependency-management")
}
