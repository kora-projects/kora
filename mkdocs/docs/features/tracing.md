### Tracing

Kora умеет экспортить спаны OTLP по gRPC. На данный момент спаны экспортятся для HttpServer, HttpClient, методов с @KafkaIncoming и всех баз данных.
Для подключения вам необходима зависимость opentelemetry-tracing-exporter-grpc и модуль `OpentelemetryGrpcExporterModule`.
Ниже представлен пример конфигурации. Обязательным полем является только `endpoint`, аттрибуты из поля `attributes` будут отправлятсья с каждым спаном.

```
tracing {
  otlp {
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

Помимо автоматически создаваемых спанов вы можете пользоваться объектом `Tracer` из контейнера. Создать спан с текущим в parent можно следующим образом:

```java
var ctx = Context.current();
var otctx = OpentelemetryContext.get(ctx);
var newSpan = this.tracer.spanBuilder("some-span")
    .setParent(otctx.getContext())
    .build();
```
