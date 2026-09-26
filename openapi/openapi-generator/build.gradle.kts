plugins {
    alias(libs.plugins.kora.kotlin)
    alias(libs.plugins.kora.inTestGenerated)
}

dependencies {
    api(libs.slf4j.api)
    api(libs.openapi.generator) {
        exclude(group = "org.slf4j", module = "slf4j-api")
        exclude(group = "com.github.ben-manes.caffeine", module = "caffeine")
        exclude(group = "commons-codec", module = "commons-codec")
        exclude(group = "commons-io", module = "commons-io")
    }

    implementation(libs.javapoet)
    implementation(libs.kotlinpoet)
    implementation(libs.caffeine)
    implementation(libs.commons.codec)
    implementation(libs.commons.io)
    implementation(libs.translit.icu4j)

    testImplementation(libs.swagger.annotations)
    testImplementation(libs.kotlin.compiler)
    testImplementation(libs.kotlin.stdlib.lib)
    testImplementation(projects.json.jsonCommon)
    testImplementation(projects.json.jacksonModule)
    testImplementation(projects.json.jsonAnnotationProcessor)
    testImplementation(projects.json.jsonSymbolProcessor)
    testImplementation(projects.http.httpClientCommon)
    testImplementation(projects.http.httpClientAnnotationProcessor)
    testImplementation(projects.http.httpClientSymbolProcessor)
    testImplementation(projects.http.httpServerCommon)
    testImplementation(projects.http.httpServerAnnotationProcessor)
    testImplementation(projects.http.httpServerSymbolProcessor)
    testImplementation(projects.validation.validationModule)
    testImplementation(projects.validation.validationAnnotationProcessor)
    testImplementation(projects.validation.validationSymbolProcessor)
    testImplementation(projects.aop.aopAnnotationProcessor)
    testImplementation(projects.aop.aopSymbolProcessor)
    testImplementation(testFixtures(projects.core.annotationProcessorCommon))
    testImplementation(testFixtures(projects.core.symbolProcessorCommon))
}

val openapiPaths = listOf(
    "petstoreV2",
    "petstoreV2_implicit_headers",
    "petstoreV2_implicit_headers_regex",
    "petstoreV2_server_request",
    "petstoreV3",
    "petstoreV3_implicit_headers",
    "petstoreV3_implicit_headers_regex",
    "petstoreV3_server_request",
    "petstoreV3_additional_props",
    "petstoreV3_additional_props_enable_json_nullable",
    "petstoreV3_discriminator",
    "petstoreV3_discriminator_enable_json_nullable",
    "petstoreV3_default_delegate",
    "petstoreV3_enum",
    "petstoreV3_filter",
    "petstoreV3_form",
    "petstoreV3_nullable",
    "petstoreV3_nullable_enable_json_nullable",
    "petstoreV3_request_parameters",
    "petstoreV3_responses",
    "petstoreV3_response_ranges",
    "petstoreV3_response_ranges_no_default",
    "petstoreV3_requests",
    "petstoreV3_same_response_model",
    "petstoreV3_bare_object",
    "petstoreV3_security_all",
    "petstoreV3_security_all_auth_arg",
    "petstoreV3_security_multi",
    "petstoreV3_security_multi_auth",
    "petstoreV3_security_multi_auth_arg",
    "petstoreV3_security_api_key",
    "petstoreV3_security_api_key_auth_arg",
    "petstoreV3_security_basic",
    "petstoreV3_security_basic_auth_arg",
    "petstoreV3_security_bearer",
    "petstoreV3_security_bearer_auth_arg",
    "petstoreV3_security_oauth",
    "petstoreV3_security_oauth_auth_arg",
    "petstoreV3_security_cookie",
    "petstoreV3_security_cookie_auth_arg",
    "petstoreV3_single_response",
    "petstoreV3_types",
    "petstoreV3_validation",
    "petstoreV3_validation_enable_json_nullable",
    "petstoreV3_client_successful_response",
    "petstoreV3_client_successful_response_successful"
)

sourceSets {
    maybeCreate("testGenerated").apply {
        java.srcDirs(
            openapiPaths.flatMap { path ->
                listOf(
                    layout.buildDirectory.dir("out/$path/java-client"),
                    layout.buildDirectory.dir("out/$path/java-server")
                )
            }
        )
    }
}

pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    val kotlinExtension = extensions.getByType<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension>()
    kotlinExtension.sourceSets.maybeCreate("testGenerated").apply {
        kotlin.srcDirs(
            openapiPaths.flatMap { path ->
                listOf(
                    layout.buildDirectory.dir("out/$path/kotlin-client"),
                    layout.buildDirectory.dir("out/$path/kotlin-server")
                )
            }
        )
    }
}

tasks.named<Test>("test") {
    val globalArgs = jvmArgs
    globalArgs.remove("-XX:TieredStopAtLevel=1")

    minHeapSize = "1g"
    maxHeapSize = "4g"

    setJvmArgs(globalArgs + listOf(
        "-Dkotlin.compiler.execution.strategy=in-process",
    ))

    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

    systemProperties(
        "junit.jupiter.execution.parallel.enabled" to "true",
        "junit.jupiter.execution.parallel.mode.default" to "concurrent",
        "junit.jupiter.execution.parallel.config.strategy" to "dynamic",
        "junit.jupiter.execution.parallel.config.dynamic.factor" to "1",
    )
}
