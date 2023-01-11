# Http server

Kora предоставляет тонкий слой абстракции над библиотеками http-сервера. 
Это сделано исключительно для того, чтобы можно было заменять реализации на более эффективные по мере их появления.

## Реализации сервера

### Undertow

Для подключения http-сервера `Undertow`, необходимо добавить модуль `UndertowHttpServerModule` в интерфейс из следующей зависимости:

```groovy
implementation "ru.tinkoff.kora:http-server-undertow"
```

## Обработчики запросов

Обработчик запроса представляет собой интерфейс `HttpServerRequestHandler`:

```java
package ru.tinkoff.kora.http.server.common;

import reactor.core.publisher.Mono;

public interface HttpServerRequestHandler {
    String method();

    String routeTemplate();

    Mono<HttpServerResponse> handle(HttpServerRequest request);
}
```

* method - http-метод
* routeTemplate - шаблон запроса в формате `/some/{route}/template`
* handle - асинхронный обработчик запроса

Это низкоуровневый API, который предоставляет доступ напрямую к сырому запросу. И напрямую его использовать скорее всего никогда не придётся.

## Контроллеры

Для более удобного написания обработчиков, есть возможность декларативно описывать контроллеры через аннотации.

```java
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.SimpleHttpServerResponse;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;

import java.nio.charset.StandardCharsets;

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

Аннотация `HttpController` помечает класс как контроллер. `HttpRoute` отмечает методы, которые должны обрабатывать запросы.
В данном примере мы будем обрабатывать `GET /hello/world` и в ответ мы можем вернуть сырой ответ типа `HttpServerResponse`.
Но контроллеры могут возвращать и любой другой тип ответа, подробнее это будет рассмотрено в подразделе [Преобразование ответа](#_3)

Для такого контроллера на этапе компиляции будет сгенерирован модуль `HelloWorldControllerModule`, в котором будет предоставлен обработчик, вызывающий методы этого контроллера.
Этот модуль можно добавить в ваш контейнер стандартным способом через наследование.

### Контроллеры и Component

Для упрощения добавления контроллеров и сгенерированных модулей в контейнер приложения контроллер можно пометить аннотацией `Component`.
При этом на сгенерированный модуль будет добавлена аннотация `Module` и все обработчики запросов станут доступны в контейнере без явного добавления.  
Подробнее про эти аннотации читайте на [странице про контейнер](/features/container/#module).

### Преобразование ответа

Давайте рассмотрим следующий пример. Тут мы возвращаем record `HelloWorldResponse` из метода `helloWorld`.

```java
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;

@HttpController
@Component
public final class HelloWorldController {
    public record HelloWorldResponse(String greeting) {}

    @HttpRoute(method = HttpMethod.GET, path = "/hello/world")
    public HelloWorldResponse helloWorld() {
        return new HelloWorldResponse("Hello world");
    }
}
```

Теперь взглянем на сгенерированный код, чтобы лучше понять, как это всё работает.

```java
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Module;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.common.handler.BlockingRequestExecutor;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandlerImpl;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;
import ru.tinkoff.kora.http.server.common.handler.HttpServerResponseMapper;

@Module
public interface HelloWorldControllerModule {
    default HttpServerRequestHandler get_hello_world(HelloWorldController _controller,
                                                     HttpServerRequestMapper<HttpServerRequest> _requestMapper,
                                                     HttpServerResponseMapper<HelloWorldController.HelloWorldResponse> _responseMapper,
                                                     BlockingRequestExecutor _executor) {
        return HttpServerRequestHandlerImpl.get("/hello/world", _request -> Mono.deferContextual(_ctx -> _requestMapper.apply(_request)
            .flatMap(_mappedRequest -> _executor.execute(() -> _controller.helloWorld()))
            .flatMap(_response -> _responseMapper.apply(_response))));
    }
}
```

Запрос преобразуется при помощи переменной `_responseMapper`, которая объявлена в качестве параметра для фабричной функции `get_hello_world`.
То есть кодогенерация обработчика полагается на то, что обработчик ответов будет найден где внутри контейнера нашего приложения.  
`HttpServerResponseMapper` - это по сути просто функция, это по сути просто функция `T -> HttpServerResponse`.

На данный момент есть два модуля которые можно подключить для простого преобразования ответа в json с кодом 200:

* JsonHttpServerModule - предоставляет фабричную функцию для преобразования ответа при помощи наших оптимальных кодогенерированных сериализаторов
* JacksonHttpServerModule - предоставляет фабричную функцию для преобразования ответа при помощи jackson object mapper

По аналогии с этими конверторами, можно реализовать любой API с нужным вам поведением, связанным со статус-кодами и хедерами в ответе.

### Получение параметров запроса

Для простого получения параметров запроса в контроллере предусмотрены ряд аннотаций с говорящими именами.

#### Path

Аннотация `Path` позволяет получить параметр, который определён в шаблоне пути.

```java
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.common.annotation.Path;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;

@HttpController
@Component
public final class HelloWorldController {
    public record HelloWorldResponse(String greeting) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/hello/{name}")
    public HelloWorldResponse helloWorld(@Path String name) {
        return new HelloWorldResponse(String.format("Hello %s", name));
    }
}
```

Тут будет получен параметр, который идёт в последнем фрагменте пути. Если название параметра не совпадает с названием в шаблоне, то в `Path` можно передать необходимое имя.

#### Query

По аналогии с `Path`, для получения параметра из параметров запроса, можно использовать `Query`

```java
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.common.annotation.Query;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;

@HttpController
@Component
public final class HelloWorldController {
    public record HelloWorldResponse(String greeting) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/hello")
    public HelloWorldResponse helloWorld(@Query String name) {
        return new HelloWorldResponse(String.format("Hello %s", name));
    }
}
```

В этом примере мы ожидаем, что у нас будет обязательный query-параметр с именем `name`. Если мы хотим обозначить, что параметр необязательный, то следует пометить его аннотацией `Nullable`.

#### Header

Для получения из хедеров запроса, по аналогии с предыдущими вариантами, следует использовать аннотацию `Header`.

```java
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.annotation.Header;
import ru.tinkoff.kora.http.common.annotation.HttpRoute;
import ru.tinkoff.kora.http.server.common.annotation.HttpController;

@HttpController
@Component
public final class HelloWorldController {
    public record HelloWorldResponse(String greeting) {
    }

    @HttpRoute(method = HttpMethod.GET, path = "/hello")
    public HelloWorldResponse helloWorld(@Header String name) {
        return new HelloWorldResponse(String.format("Hello %s", name));
    }
}
```

#### Сырые обработчики

Если в спике параметров указать без аннотаций  `Path`, `Query` или `Header`. То кодогенератор будет считать, что для этого параметра можно получить экземпляр типа `HttpServerRequestMapper`.
Этот механизм работает аналогично тому, что описано в [разделе про преобразование ответа](#_3).

### Синхронные и асинхронные обработчики

Kora поддерживает три варианта обработчиков в контроллере, которые определяются по возвращаемому типу:

* Возвращаемый тип является реализацией `org.reactivestreams.Publisher`, например `Mono`
* Обработчик suspend функций Kotlin, запускающихся в корутине, преобразует их в `Mono`
* Синхронный обработчик, то есть не подпадающий под два варианта выше. В этом случае мы считаем, что контроллер исполняет какой-то блокирующий код и запускаем его внутри `ExecutorService`, отвечающего за исполнение блокирующих операций


### Интерцепторы

Для перехвата (интерцепции) запросов к серверу имеются следующие возможности:

- `HttpServerInterceptor`, объявленный с тегом `@Tag(HttpServerModule.class)` будет применен ко всем входящим запросам
- `HttpServerInterceptor` из аннотации `@InterceptWith` расположенной на контроллере будет применен к каждому методу в контроллере
- `HttpServerInterceptor` из аннотации `@InterceptWith` расположенной на методе контроллера будет применен к этому методу
