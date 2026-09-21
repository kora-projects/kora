providers.environmentVariable("KORA_VERSION").orNull?.let {
    version = it
}
plugins {
    id("io.koraframework.kora-root-publish")
    id("io.koraframework.kora-root-coverage")
    id("io.koraframework.kora-root-ci-test")
    id("io.koraframework.kora-dependency-management")
    id("io.koraframework.kora-root-testcontainers")
    id("io.koraframework.kora-root-heavy-ksp")
}
