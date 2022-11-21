# gRPC server

Kora предоставляет модуль для поднятия gRPC сервера на основе базового функционала `io.grpc`

Для подключения gRPC сервера, необходимо добавить модуль `GrpcModule` из следующей зависимости

```groovy
implementation "ru.tinkoff.kora:grpc"
```
## gRPC Service
Сгенерированные gRPC сервисы требуется пометить аннотацией `@Component`

```java
@Component
public class ExampleService extends ExampleGrpc.ExampleImplBase {}
```

## Default Interceptors

При запуске сервера по-умолчанию используются следующие перехватчики:

- `ContextServerInterceptor`
- `CoroutineContextInjectInterceptor`
- `MetricCollectorServerInterceptor`
- `LoggingServerInterceptor`

Для переопределения дефолтного списка перехватчиков можно переопределить  метод
`serverBuilder` из класса `GrpcModule`

### Custom interceptor

Для добавления своего перехватчика достаточно создать наследника `ServerInterceptor` 
с аннотацией `@Component`

```java
import ru.tinkoff.kora.common.Component;

@Component
public class GrpcExceptionHandlerServerInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata,
                                                                 ServerCallHandler<ReqT, RespT> serverCallHandler) {

        ServerCall.Listener<ReqT> listener = serverCallHandler.startCall(serverCall, metadata);
        return new ExceptionHandlingServerCallListener<>(listener, serverCall, metadata);
    }
}
```

## Configuration

В настоящий момент для конфигурации gRPC server существует класс `GrpcServerConfig`, 
который принимает только номер порта (значение по умолчанию `8090`)

Пример использования в `application.conf`:
```hocon
grpcServer {
    port = 9090
}
```
