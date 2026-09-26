package io.koraframework.gradle.test

object CiTestType {
    const val CODEGEN_JAVA = "codegen-java-postgres-cassandra-redis-kafka"
    const val OPENAPI_SHARD_1 = "openapi-1"
    const val OPENAPI_SHARD_2 = "openapi-2"
    const val CODEGEN_KOTLIN_1 = "codegen-kotlin-1"
    const val CODEGEN_KOTLIN_2 = "codegen-kotlin-2"
    const val CODEGEN_KOTLIN_3 = "codegen-kotlin-3"
    const val CODEGEN_KOTLIN_4 = "codegen-kotlin-4"
    const val OTHER = "other"

    val TYPES = listOf(
        CODEGEN_JAVA,
        OPENAPI_SHARD_1,
        OPENAPI_SHARD_2,
        CODEGEN_KOTLIN_1,
        CODEGEN_KOTLIN_2,
        CODEGEN_KOTLIN_3,
        CODEGEN_KOTLIN_4,
        OTHER,
    )
}
