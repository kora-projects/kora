## Tracing

Kora умеет экспортить спаны OTLP по gRPC. На данный момент спаны экспортятся для HttpServer, HttpClient, методов с @KafkaIncoming и всех баз данных.

### Dependency

```groovy
implementation "ru.tinkoff.kora:opentelemetry-tracing-exporter-grpc"
```

### Module

```java
@KoraApp
public interface ApplicationModules extends OpentelemetryGrpcExporterModule { }
```

### Configuration

Обязательным полем является только `endpoint`, аттрибуты из поля `attributes` будут отправляться с каждым спаном.

Ниже представлен пример конфигурации:
```hocon
tracing {
  exporter {
    endpoint = "http://localhost:4317"
    exportTimeout = "2 seconds"
    scheduleDelay = "200 ms"
    maxExportBatchSize = 10000
    attributes {
      "service.name" = "test-service"
    }
  }
}
```

### Manual Tracing

Помимо автоматически создаваемых спанов вы можете пользоваться объектом `Tracer` из контейнера. Создать спан с текущим в parent можно следующим образом:

```java
var ctx = Context.current();
var otctx = OpentelemetryContext.get(ctx);
var newSpan = this.tracer.spanBuilder("some-span")
    .setParent(otctx.getContext())
    .build();
```
