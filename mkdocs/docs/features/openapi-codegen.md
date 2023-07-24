### Openapi codegen

Для облегчения использования [клиента](/kora/features/http-client) при наличии контракта openapi 

Чтобы использовать его в gradle вам необходимо добавить зависимость с генератором в buildscript и сконфигурировать плагин как обычно:
```groovy
buildscript {
    dependencies {
        classpath "ru.tinkoff.kora:openapi-generator"
    }
}

plugins {
    id "org.openapi.generator" version "6.0.0"
}

openApiGenerate {
    generatorName = "kora"
    inputSpec = "$projectDir/src/main/resources/petstoreV2.yaml".toString()
    outputDir = "$buildDir/generated".toString()
    apiPackage = "org.openapi.example.api"
    invokerPackage = "org.openapi.example.invoker"
    modelPackage = "org.openapi.example.model"
    configOptions = [
        mode: "java_client" // так же поддерживаются java_server, reactive_client, reactive_server, kotlin_client, kotlin_server
    ]
}
```

Для maven необходимо добавить зависимость с генератором для плагина и так же сконфигурировать:
```xml
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <version>6.0.0</version>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <inputSpec>${project.basedir}/src/main/resources/petstoreV2.yaml</inputSpec>
                <output>${project.basedir}/target/generated-sources/openapi/petstoreV2</output>
                <generatorName>kora</generatorName>
                <configOptions>
                    <mode>java_client</mode>
                    <sourceFolder>.</sourceFolder>
                </configOptions>
            </configuration>
        </execution>
    </executions>
    <dependencies>
        <dependency>
            <groupId>ru.tinkoff.kora</groupId>
            <artifactId>openapi-generator</artifactId>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>
    </dependencies>
</plugin>
```

Для клиентов будет сгенерирован интерфейс с аннотацией `@HttpClient`, который далее можно просто использовать в приложении.

Для серверов будет сгенерирован контроллер и интерфейс делегата. Необходимо только реализовать интерфейс и положить реализацию в контейнер любым способом.


#### Теги
На сгенерированные клиенты есть возможность поставить параметры аннотации `@HttpClient` `httpClientTag` и `telemetryTag`.
Для этого необходимо установить параметр configOptions.tags:

Maven:
```xml
<configOptions>
  <mode>java_client</mode>
  <tags>{
    "*": { // применится для всех тегов, кроме явно указанных (в данном случае instrument)
      "httpClientTag": "some.tag.Common.class",
      "telemetryTag": "some.tag.Common.class"
    }
    "instrument": { // применится для instrument
      "httpClientTag": "some.tag.Instrument.class",
      "telemetryTag": "some.tag.Instrument.class"
    }
  }
  </tags>
</configOptions>
```
Gradle:
```groovy
    configOptions = [
    mode: "java_client",
    tags: """
          {
            "*": { // применится для всех тегов, кроме явно указанных (в данном случае instrument)
              "httpClientTag": "some.tag.Common.class",
              "telemetryTag": "some.tag.Common.class"
            },
            "instrument": { // применится для instrument
              "httpClientTag": "some.tag.Instrument.class",
              "telemetryTag": "some.tag.Instrument.class"
            }
          }
          """
]
```

Значение - json объект, ключем которого выступает тег апи из контракта, а значением объект с полями `httpClientTag` и `telemetryTag`.

#### Валидация на стороне сервера

Для генерации моделей и контроллеров с аннотациями из модуля валидации необходимо установить опцию `enableServerValidation`:

Maven:

```xml

<configOptions>
    <mode>java_server</mode>
    <enableServerValidation>true</enableServerValidation>
</configOptions>
```

Gradle:

```groovy
    configOptions = [
    mode                  : "java_server",
    enableServerValidation: true
]
```
