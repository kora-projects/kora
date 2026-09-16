plugins {
    alias(libs.plugins.kora.java)
}

dependencies {
    annotationProcessor(projects.config.configAnnotationProcessor)

    compileOnly(projects.database.databaseJdbc)

    api(projects.core.common)
    api(libs.camunda7.engine) {
        exclude(group = "org.springframework", module = "spring-beans")
        exclude(group = "org.apache.tomcat", module = "catalina")
    }

    implementation(projects.config.configCommon)
    implementation(libs.fasterxml.uuidgenerator)

    testImplementation(libs.jdbc.postgresql)
    testImplementation(projects.database.databaseJdbc)
    testImplementation(projects.internal.testLogging)
    testImplementation(projects.internal.testPostgres)
}
