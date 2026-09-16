import com.google.protobuf.gradle.id

plugins {
    alias(libs.plugins.kora.java)
    alias(libs.plugins.protobuf)
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    compileOnly(libs.grpc.services)
    compileOnly(libs.grpc.kotlin.stub)
    compileOnly(libs.protobuf.java)

    api(projects.logging.loggingCommon)
    api(projects.telemetry.telemetryCommon)
    api(libs.grpc.stub)
    api(libs.grpc.okhttp)

    testImplementation(libs.grpc.java.gen)
    testImplementation(libs.grpc.protobuf)
    testImplementation(libs.protobuf.java)
    testImplementation(libs.javax.annotation.api)
}

configurations.compileClasspath {
    exclude(group = "com.google.code.findbugs", module = "jsr305")
}
configurations.runtimeClasspath {
    exclude(group = "com.google.code.findbugs", module = "jsr305")
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.orNull?.toString()
    }
    plugins {
        id("grpc") {
            artifact = libs.grpc.java.gen.orNull?.toString()
        }
    }
    generateProtoTasks {
        ofSourceSet("test").forEach { task ->
            task.plugins {
                id("grpc")
            }
        }
    }
}

sourceSets {
    test {
        java {
            val buildDir = layout.buildDirectory
            srcDir(buildDir.dir("generated/source/proto/test/grpc"))
            srcDir(buildDir.dir("generated/source/proto/test/java"))
        }
    }
}
