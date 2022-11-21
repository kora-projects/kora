# Hello world

Данный пример разбирает как создать простой сервис на kora, с настроенными метриками, логированием и пробами, который умеет отвечать на запрос `GET /hello/world`.

## Требования

Для работы нам потребуется gradlew с настроенной версией gradle `7.* +`  
Например:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-7.5.1-bin.zip
```

## Настройка gradle 

### Kotlin
Начиная с 9ой версии можно использовать ksp для проектов на kotlin
```kotlin
plugins {
    kotlin("jvm") version "1.7.10"
    id("com.google.devtools.ksp") version "1.7.10-1.0.6"
}

group = "ru.tinkoff.kora.hello.world"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of("17"))
    }

    // объяснем идее где лежат сгенеренные классы
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
    sourceSets.test {
        kotlin.srcDir("build/generated/ksp/test/kotlin")
    }

}


val koraVersion = "0.10.0"

dependencies {
    val kora = platform("ru.tinkoff.kora:kora-parent:$koraVersion")
    implementation(kora)
    ksp(kora)

    ksp("ru.tinkoff.kora:symbol-processors")


    implementation("ru.tinkoff.kora:http-server-undertow")
    implementation("ru.tinkoff.kora:micrometer-module")
    implementation("ru.tinkoff.kora:json-module")
}
```


### Groovy

```groovy
plugins {
    id 'java'
}

group="ru.tinkoff.kora.hello.world"

repositories {
    mavenLocal()
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of("17"))
    }
}

compileJava {
    targetCompatibility = JavaVersion.toVersion("17")
    sourceCompatibility = JavaVersion.toVersion("17")
}

dependencies {
    implementation platform("ru.tinkoff.kora:kora-parent:$koraVersion")
    implementation "ru.tinkoff.kora:http-server-undertow"
    implementation "ru.tinkoff.kora:json-module"
    implementation "ru.tinkoff.kora:micrometer-module"

    annotationProcessor "ru.tinkoff.kora:annotation-processors:$koraVersion"
}
```



## Минимальная конфигурация приложения

Для запуска приложения нам нужно сформировать контейнер. Для этого создадим интерфейс Application.java с таким кодом:

```java
package ru.tinkoff.kora.hello.world;

import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.config.common.ConfigModule;
import ru.tinkoff.kora.http.server.undertow.UndertowHttpServerModule;
import ru.tinkoff.kora.micrometer.module.MetricsModule;

@KoraApp
public interface Application extends
    ConfigModule,
    MetricsModule,
    UndertowHttpServerModule {
}
```

Если мы запустим компиляцию, то будет сгенерирован класс `ApplicationGraph.java`, в котором описано как собирать все компоненты нашего будущего контейнера.
Что нам предоставляет `UndertowHttpServerModule`:

* Сервер для публичного апи на порту 8080 
* Сервер для системного апи на порту 8085
* liveness и readiness пробы на системном порту: [/system/liveness](http://localhost:8085/system/liveness) и [/system/readiness](http://localhost:8085/system/readiness)    
* route отдающий метрики: [/metrics](http://localhost:8085/metrics)

Далее нам нужно создать точку входа, создадим класс `AppRunner.java` с методом `main`

```java
package ru.tinkoff.kora.hello.world;

import ru.tinkoff.kora.application.graph.KoraApplication;

public class AppRunner {
  public static void main(String[] args) {
    KoraApplication.run(ApplicationGraph::graph);
  }
}
```

`KoraApplication.run` запускает параллельную инициализацию всех компонентов в контейнере и блокирует основной поток до получения сигнала SIGTERM, после этого приложение начинает graceful shutdown.
Теперь, если мы запустим это приложение, то нам будут доступны роуты по ссылкам выше.
При этом в логах видно, что поднялся только порт 8085. Это происходит из-за того, что у нас ещё нет ни одного обработчика запросов. 

## Hello world контроллер
Теперь давайте напишем контроллер, который будет обрабатывать запрос `GET /hello/world` на публичном порту.  

```java
package ru.tinkoff.kora.hello.world;

import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.SimpleHttpServerResponse;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;

import java.nio.charset.StandardCharsets;

@Component
@HttpController
public final class HelloWorldController {
  @HttpRoute(method = HttpMethod.GET, path = "/hello/world")
  public HttpServerResponse helloWorld() {
    return new SimpleHttpServerResponse(
        200,
        "text/plain",
        HttpHeaders.of(),
        StandardCharsets.UTF_8.encode("Hello world")
    );
  }
}
```

Давайте разберёмся детально:

* HttpController - говорит, что класс - это контроллер
* Component - говорит, что мы хотим добавить этот класс в наш контейнер
* HttpRoute - описывает какой роут хотим обрабатывать
* HttpServerResponse - это сырой вариант ответа, в котором можно выставить что угодно и отдать любые байты

## Ответ в формате json
В обычной жизни мы всё-таки чаще отдаём json, для этого добавим модуль   

```java
package ru.tinkoff.kora.hello.world;

import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.config.common.ConfigModule;
import ru.tinkoff.kora.http.server.undertow.UndertowHttpServerModule;
import ru.tinkoff.kora.json.module.JsonModule;
import ru.tinkoff.kora.micrometer.module.MetricsModule;

@KoraApp
public interface Application extends
    ConfigModule,
    MetricsModule,
    JsonModule,
    UndertowHttpServerModule {
  
}
```

Далее изменим контроллер, чтобы он возвращал DTO, которую мы хотим сериализовать

```java
package ru.tinkoff.kora.hello.world;

import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;

@Component
@HttpController
public final class HelloWorldController {
  final static record HelloWorldResponse(String greeting) {}

  @HttpRoute(method = HttpMethod.GET, path = "/hello/world")
  public HelloWorldResponse helloWorld() {
    return new HelloWorldResponse("hello world");
  }
}
```

Теперь для нашего record'а будет сформирован оптимальный JsonWriter и в ответе мы увидим json

```json
{"greeting":"Hello world"}
```
