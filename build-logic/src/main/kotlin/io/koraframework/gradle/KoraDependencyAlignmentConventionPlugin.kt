package io.koraframework.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

class KoraDependencyAlignmentConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val catalogs = project.extensions.getByType<VersionCatalogsExtension>()
        val libs = catalogs.named("libs")

        fun getVer(key: String): String =
            libs.findVersion(key)
                .orElseThrow { IllegalStateException("Version '$key' not found in libs.versions.toml") }
                .requiredVersion

        val nettyVer = getVer("netty")
        val grpcVer = getVer("grpc-java")
        val protoVer = getVer("protobuf")
        val jetbrainsVer = getVer("jetbrains-annotations")
        val kotlinVer = getVer("kotlin-stdlib")
        val poetVer = getVer("kotlinpoet")
        val junitVer = getVer("junit")
        val slf4jVer = getVer("slf4j")
        val logbackVer = getVer("logback")
        val guavaVer = getVer("guava")
        val buddyVer = getVer("byte-buddy")
        val jacksonAnnotationsVer = getVer("jackson2-annotations")
        val jacksonCorelineVer = getVer("jackson2-coreline")
        val cliVer = getVer("commons-cli")
        val codecVer = getVer("commons-codec")
        val collectionsVer = getVer("commons-collections")
        val fileuploadVer = getVer("commons-fileupload")
        val ioVer = getVer("commons-io")
        val loggingVer = getVer("commons-logging")
        val lang3Ver = getVer("commons-lang3")
        val textVer = getVer("commons-text")
        val prometheusVer = getVer("prometheus-metrics")
        val swaggerVer = getVer("swagger-coreline")
        val swaggerParserVer = getVer("swagger-parser")

        project.configurations.configureEach {
            resolutionStrategy {
                force(
                    "io.netty:netty-common:$nettyVer",
                    "io.netty:netty-buffer:$nettyVer",
                    "io.netty:netty-codec:$nettyVer",
                    "io.netty:netty-codec-dns:$nettyVer",
                    "io.netty:netty-codec-http:$nettyVer",
                    "io.netty:netty-codec-http2:$nettyVer",
                    "io.netty:netty-codec-socks:$nettyVer",
                    "io.netty:netty-codec-common:$nettyVer",
                    "io.netty:netty-handler:$nettyVer",
                    "io.netty:netty-handler-proxy:$nettyVer",
                    "io.netty:netty-resolver:$nettyVer",
                    "io.netty:netty-resolver-dns:$nettyVer",
                    "io.netty:netty-resolver-dns-classes-macos:$nettyVer",
                    "io.netty:netty-resolver-dns-native-macos:$nettyVer",
                    "io.netty:netty-transport:$nettyVer",
                    "io.netty:netty-transport-clasess-epoll:$nettyVer",
                    "io.netty:netty-transport-clasess-kqueue:$nettyVer",
                    "io.netty:netty-transport-unix-common:$nettyVer",
                    "io.netty:netty-transport-native-epoll:$nettyVer",
                    "io.netty:netty-transport-native-kqueue:$nettyVer",
                    "io.netty:netty-transport-native-unix-common:$nettyVer",

                    "io.grpc:grpc-api:$grpcVer",
                    "io.grpc:grpc-context:$grpcVer",
                    "io.grpc:grpc-core:$grpcVer",
                    "io.grpc:grpc-netty:$grpcVer",
                    "io.grpc:grpc-okhttp:$grpcVer",
                    "io.grpc:grpc-protobuf:$grpcVer",
                    "io.grpc:grpc-protobuf-lite:$grpcVer",
                    "io.grpc:grpc-services:$grpcVer",
                    "io.grpc:grpc-stub:$grpcVer",
                    "io.grpc:grpc-util:$grpcVer",

                    "com.google.protobuf:protobuf-kotlin:$protoVer",
                    "com.google.protobuf:protobuf-java:$protoVer",
                    "com.google.protobuf:protobuf-java-util:$protoVer",

                    "org.jetbrains:annotations:$jetbrainsVer",

                    "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVer",
                    "org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVer",
                    "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVer",

                    "com.squareup:kotlinpoet:$poetVer",
                    "com.squareup:kotlinpoet-ksp:$poetVer",

                    "org.junit.jupiter:junit-jupiter:$junitVer",
                    "org.junit.jupiter:junit-platform-launcher:$junitVer",

                    "org.slf4j:slf4j-api:$slf4jVer",

                    "ch.qos.logback:logback-classic:$logbackVer",
                    "ch.qos.logback:logback-core:$logbackVer",

                    "com.google.guava:guava:$guavaVer",

                    "net.bytebuddy:byte-buddy:$buddyVer",
                    "net.bytebuddy:byte-buddy-agent:$buddyVer",

                    "com.fasterxml.jackson.core:jackson-annotations:$jacksonAnnotationsVer",
                    "com.fasterxml.jackson.core:jackson-core:$jacksonCorelineVer",
                    "com.fasterxml.jackson.core:jackson-databind:$jacksonCorelineVer",
                    "com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonCorelineVer",
                    "com.fasterxml.jackson.datatype:jackson-datatype-guava:$jacksonCorelineVer",
                    "com.fasterxml.jackson.datatype:jackson-datatype-jdk8:$jacksonCorelineVer",
                    "com.fasterxml.jackson.datatype:jackson-datatype-joda:$jacksonCorelineVer",
                    "com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonCorelineVer",
                    "com.fasterxml.jackson.jakarta.rs:jackson-jakarta-rs-base:$jacksonCorelineVer",
                    "com.fasterxml.jackson.jakarta.rs:jackson-jakarta-rs-json-provider:$jacksonCorelineVer",
                    "com.fasterxml.jackson.module:jackson-module-jakarta-xmlbind-annotations:$jacksonCorelineVer",

                    "commons-cli:commons-cli:$cliVer",
                    "commons-codec:commons-codec:$codecVer",
                    "commons-collections:commons-collections:$collectionsVer",
                    "commons-fileupload:commons-fileupload:$fileuploadVer",
                    "commons-io:commons-io:$ioVer",
                    "commons-logging:commons-logging:$loggingVer",

                    "org.apache.commons:commons-lang3:$lang3Ver",
                    "org.apache.commons:commons-text:$textVer",

                    "io.prometheus:prometheus-metrics-config:$prometheusVer",
                    "io.prometheus:prometheus-metrics-core:$prometheusVer",
                    "io.prometheus:prometheus-metrics-exposition-formats:$prometheusVer",
                    "io.prometheus:prometheus-metrics-exposition-textformats:$prometheusVer",
                    "io.prometheus:prometheus-metrics-model:$prometheusVer",
                    "io.prometheus:prometheus-metrics-tracer-common:$prometheusVer",

                    "io.swagger.core.v3:swagger-annotations:$swaggerVer",
                    "io.swagger.core.v3:swagger-core:$swaggerVer",
                    "io.swagger.core.v3:swagger-models:$swaggerVer",

                    "io.swagger.parser.v3:swagger-parser:$swaggerParserVer",
                    "io.swagger.parser.v3:swagger-parser-core:$swaggerParserVer",
                    "io.swagger.parser.v3:swagger-parser-safe-url-resolver:$swaggerParserVer",
                    "io.swagger.parser.v3:swagger-parser-v2-converter:$swaggerParserVer",
                    "io.swagger.parser.v3:swagger-parser-v3:$swaggerParserVer"
                )
            }
        }
    }
}
