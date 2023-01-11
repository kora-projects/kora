# Http client

Kora предоставляет инструментарий для создания и исполнения http-запросов.

## Реализации клиента

### AsyncHttpClient

```groovy
implemenation 'ru.tinkoff.kora:http-client-async'
```

Для работы через AsyncHttpClient необходимо добавить модуль `AsyncHttpClientModule` к своему `@KoraApp`.

### Нативный JDK client

```groovy
implemenation 'ru.tinkoff.kora:http-client-jdk'
```

Для работы через нативный JDK клиент необходимо добавить модуль `JdkHttpClientModule` к своему `@KoraApp`

## Клиент

Базовый клиент представляет собой интерфейс `HttpClient`

```java
public interface HttpClient {
    /**
     * Result Mono can throw wrapped {@link HttpClientException}
     */
    Mono<HttpClientResponse> execute(HttpClientRequest request);

    default HttpClient with(HttpClientInterceptor interceptor) {
        return request -> interceptor.processRequest(this::execute, request);
    }
}
```

* `execute` — метод исполнения запроса
* `with` — метод, позволяющий добавлять различные интерцепторы

На данный момент предоставляется одна реализация клиента на базе `org.asynchttpclient.AsyncHttpClient`: `ru.tinkoff.kora.http.client.async.AsyncHttpClient`
## Request builder

Для построения запросов вручную можно использовать `HttpClientRequestBuilder`, конструктор которого выглядит следующим образом:

```java
public HttpClientRequestBuilder(String method, String uriTemplate) {
    this.method = method;
    this.uriTemplate = uriTemplate;
}
```

###<a name="client_gen"></a> Генерация клиента

Аннотация `HttpClient` помечает интерфейс как http client. `HttpRoute`, в случае клиента, отмечает маршрут, на который нужно отправить запрос.

```java
@HttpClient
public interface Hello {
    @HttpRoute(method = HttpMethod.GET, path = "/hello/{name}")
    HttpClientResponse getGreetings(@Path("name") String name, @Query("includeOutdated") String includeOutdated);

    @HttpRoute(method = HttpMethod.POST, path = "/hello/")
    Greeting addGreeting(Greeting greeting);
}
```

В этом примере показана как работа с чистым `HttpClientResponse`, так и с маппингом тела запроса и ответа. Рассмотрим подробнее:
* `@Query` — аннотация, помечающая параметр метода как query-параметр
* `@Path` — аннотация, помечающая параметр метода как часть пути
* `@Header` — аннотация, позволяющая добавить к запросу заголовок
* `@Mapping` — аннотация, позволяющая указать кастомный маппер для преобразования в тело запроса

По умолчанию маппер будет применяться только для 2хх статусов, для всех остальных будет выбрасываться исключение `HttpClientResponseException`, выглядящее следующим образом:

```java
public class HttpClientResponseException extends HttpClientException {
    private final int code;
    private final String httpMessage;
    private final HttpHeaders headers;
    private final byte[] bytes;
}
```
Это поведение, не применяется если работать с `HttpClientResponse`. Кроме того, можно задать собственные мапперы для любых статусов с помощью аннотации `@ResponseCodeMapper`. 

Пример:

```java
@HttpRoute(method = HttpMethod.POST, path = "/repos/{owner}/{repo}/issues")
@ResponseCodeMapper(code = 418, mapper = CustomVoidMapper.class)
@ResponseCodeMapper(code = 201, type = Void.class)
void createIssue(Issue issue, @Path("owner") String owner, @Path("repo") String repo);
```

Код маппера:

```java
class CustomVoidMapper implements HttpClientResponseMapper<Void, Mono<Void>> {
    @Override
    public Mono<Void> apply(HttpClientResponse response) {
        return Mono.empty();
    }
}
```

Можно заметить, что в методе `addGreeting` не указаны аннотации для параметра. В таком случае кодогенератор будет считать, что в контейнере можно получить соответствующий экземпляр `HttpClientRequestMapper`.

Посмотрим на сгенерированный код клиента:

```java
@Generated("ru.tinkoff.kora.http.client.annotation.processor.ClientClassGenerator")
public class HelloClient implements Hello {
    private final HttpClientRequestMapper<Greeting> addGreetingRequestMapper;

    private final HttpClientResponseMapper<Greeting, Mono<Greeting>> addGreetingResponseMapper;

    private final HttpClient addGreetingClient;

    private final int addGreetingRequestTimeout;

    private final String addGreetingUrl;

    public HelloClient(HttpClient httpClient,
                       HelloConfig config,
                       HttpClientRequestMapper<Greeting> addGreetingRequestMapper,
                       HttpClientResponseMapper<Greeting, Mono<Greeting>> addGreetingResponseMapper) {
        this.addGreetingRequestMapper = addGreetingRequestMapper;
        this.addGreetingResponseMapper = addGreetingResponseMapper;
        var addGreeting = config.apply(httpClient, Hello.class, "addGreeting", config.addGreetingConfig(), "/hello/");
        this.addGreetingUrl = addGreeting.url();
        this.addGreetingClient = addGreeting.client();
        this.addGreetingRequestTimeout = addGreeting.requestTimeout();
    }
    
    @Override
    public Greeting addGreeting(Greeting greeting) throws HttpClientException { 
        //здесь реализация
    }
}
```

Для наглядности было убрано всё, связанное с обработкой `HttpClientResponse`. Здесь следует остановиться на параметрах конструктора сгенерированного клиента: 

* `httpClient` - http client, через который будут выполняться все запросы
* `config` - конфигурация клиента, о ней поговорим чуть ниже
* `addGreetingRequestMapper` - маппер запроса
* `addGreetingResponseMapper` - маппер ответа

При использовании аннотации `@Mapping` вместо сгенерированного маппера будет использоваться указанный, кроме того, не будет проводиться проверка статусов, пример:
```java
@HttpClient
public interface ClientWithMappers {
    @HttpRoute(method = HttpMethod.GET, path = "/repos/{owner}/{repo}/contributors")
    @Mapping(value = ContributorListMapper.class)
    @Tag(ClientWithMappers.class)
    List<Contributor> contributors(@Path("owner") String owner, @Path("repo") String repo);
}
```

Код маппера:

```java
class ContributorListMapper implements HttpClientResponseMapper<List<Contributor>, Mono<List<Contributor>>> {
    private final JsonReader<List<Contributor>> reader;

    public ContributorListMapper(JsonReader<List<Contributor>> reader) {this.reader = reader;}
    @Override
    public Mono<List<Contributor>> apply(HttpClientResponse response) {
        return ReactorUtils.toByteArrayMono(response.body())
            .handle((bytes, sink) -> {
                try {
                    sink.next(reader.read(bytes));
                } catch (IOException e) {
                    sink.error(new HttpClientDecoderException(e));
                }
            });

    }
}
```
### Конфигурация клиента

Кроме клиента, кодогенератор создаёт класс конфигурации для него:

```java
public record HelloConfig(
        String url,
        @Nullable Integer requestTimeout,
        @Nullable Boolean tracingEnabled,
        @Nullable Boolean loggingEnabled,
        @Nullable HttpClientOperationConfig addGreetingConfig) implements ru.tinkoff.kora.http.client.common.declarative.DeclarativeHttpClientConfig {
}
```
`addGreetingConfig` позволяет переопределить конфигурацию для запроса `addGreeting`, а именно `requestTimeout`, `tracingEnabled` и `loggingEnabled`.

По умолчанию для поиска конфигурации будет использован следующий путь `httpClient.{lower case class name}`.
В таком случае файл конфигурации будет выглядеть следующим образом:

```
 httpClient.hello {
    "url" = "http://localhost:8080"
    "requestTimeout" = 10s
    "tracingEnabled" = false
    "loggingEnabled" = true
    'addGreetingConfig" {
        "requestTimeout" = 20s
    }
 }
```


### Интерцепторы

Kora предоставляет интерфейс `HttpClientInterceptor` для создания кастомных интерцепторов:

```java
public interface HttpClientInterceptor {
    Mono<HttpClientResponse> processRequest(Function1<HttpClientRequest, Mono<HttpClientResponse>> chain, HttpClientRequest request);
}
```

Пример существующей имплементации:
```java
public class RootUriInterceptor implements HttpClientInterceptor {
    private final String root;

    public RootUriInterceptor(String root) {
        this.root = root.endsWith("/")
            ? root.substring(0, root.length() - 1)
            : root;
    }

    @Override
    public Mono<HttpClientResponse> processRequest(Function1<HttpClientRequest, Mono<HttpClientResponse>> chain, HttpClientRequest request) {
        var template = request.uriTemplate().startsWith("/")
            ? request.uriTemplate()
            : "/" + request.uriTemplate();

        var r = request.toBuilder()
            .uriTemplate(this.root + template)
            .build();

        return chain.invoke(r);
    }
}
```

Для декларативного клиента можно отмечать классы и методы аннотацией `@InterceptWith` с указанием интерцептора.

### Пример использования в сервисе

```java
public final class HelloService {
    private final Hello client;

    public HelloService(Hello client) {
        this.client = client;
    }

    int getHelloResponseCode(String name) {
        return client.getGreetings(name, "true").code();
    }
}
```

### Поддержка корутин и Reactor

HttpClient Kora поддерживает Kotlin coroutines и Project Reactor из коробки. Примеры:

* Kotlin coroutines:

```kotlin
@HttpClient
interface GithubClientKotlin {
    @HttpRoute(method = HttpMethod.GET, path = "/repos/{owner}/{repo}/contributors")
    suspend fun contributors(@Path("owner") owner: String, @Path("repo") repo: String): List<GithubClient.Contributor>

    @HttpRoute(method = HttpMethod.POST, path = "/repos/{owner}/{repo}/issues")
    suspend fun createIssue(issue: GithubClient.Issue, @Path("owner") owner: String, @Path("repo") repo: String)
}
```

* Mono:

```java
@HttpClient
public interface GithubClientReactive {
    @HttpRoute(method = HttpMethod.GET, path = "/repos/{owner}/{repo}/contributors")
    Mono<List<Contributor>> contributors(@Path("owner") String owner, @Path("repo") String repo);

    @HttpRoute(method = HttpMethod.POST, path = "/repos/{owner}/{repo}/issues")
    Mono<Void> createIssue(Issue issue, @Path("owner") String owner, @Path("repo") String repo);
}
```
