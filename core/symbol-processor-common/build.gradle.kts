plugins {
    alias(libs.plugins.kora.kotlin)
    `java-test-fixtures`
}

dependencies {
    api(libs.slf4j.api)
    api(libs.kotlinpoet)
    api(libs.kotlinpoet.ksp)
    api(libs.ksp.api)
    api(libs.kotlin.reflect)

    testFixturesApi(projects.core.common)
    testFixturesApi(projects.aop.aopSymbolProcessor)
    testFixturesApi(libs.kotlin.reflect)

    testFixturesImplementation(libs.classgraph)
    testFixturesImplementation(libs.kotlin.compiler)
    testFixturesImplementation(libs.ksp.symbol.processing.aa.embeddable)
    testFixturesImplementation(libs.ksp.symbol.processing.common.deps)
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(libs.ksp)
    testFixturesImplementation(libs.ksp.api)
}
