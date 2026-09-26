import com.google.protobuf.gradle.id

plugins {
    alias(libs.plugins.kora.kotlin)
    alias(libs.plugins.kora.inTestGenerated)
    alias(libs.plugins.protobuf)
}

dependencies {
    api(projects.core.symbolProcessorCommon)

    implementation(projects.core.koraAppSymbolProcessor)

    testImplementation(libs.grpc.kotlin.stub)
    testImplementation(libs.protobuf.kotlin)
    testImplementation(libs.grpc.protobuf)
    testImplementation(projects.grpc.grpcClient)
    testImplementation(projects.config.configSymbolProcessor)
    testImplementation(testFixtures(projects.core.symbolProcessorCommon))
    testImplementation(libs.javax.annotation.api)
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    plugins {
        id("grpc") {
            artifact = libs.grpc.java.gen.get().toString()
        }
        id("grpckt") {
            artifact = "${libs.grpc.kotlin.gen.get()}:jdk8@jar"
        }
    }
    generateProtoTasks {
        ofSourceSet("test").forEach { task ->
            task.plugins {
                id("grpc")
                id("grpckt")
            }
            task.builtins {
                id("kotlin")
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

configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
    sourceSets {
        getByName("test") {
            kotlin.srcDir(layout.buildDirectory.dir("generated/source/proto/test/grpckt"))
        }
    }
}
