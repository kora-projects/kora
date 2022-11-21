### GraalVM Native Image

Так как сам фреймворк генерирует свои вспомогательные классы во время компиляции проблем для сборки нативного образа быть не должно.
Пример сборки нативного образа для gradle:

```groovy
plugins {
    id 'org.graalvm.buildtools.native' version '0.9.16'
}

graalvmNative {
    binaries {
        main {
            javaLauncher = javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(17)
                vendor = JvmVendorSpec.matching("GraalVM Community")
            }
            imageName = 'application'
            mainClass = 'ru.tinkoff.kora.SomeNativeApp'
            debug = true
            verbose = true
            buildArgs.add('--report-unsupported-elements-at-runtime')
        }
    }
    metadataRepository {
        enabled = true
    }
}
```

Некоторые библиотеки требуют дополнительной конфигурации, часть конфигураций сделана в фреймворке.
Проверенные модули, котоыре должны работать без дополнительной конфигурации:

- http-server-undertow
- http-client-async
- kafka
- grpc
- database-cassandra
- database-jdbc (с postgresql)
- opentelemetry
- micrometer
