# Метрики

Kora использует micrometer для записи метрик приложения.

### Dependency

```groovy
implementation "ru.tinkoff.kora:micrometer-module"
```

### Module

```java
@KoraApp
public interface ApplicationModules extends MetricsModule { }
```

После этого в Metrics.globalRegistry будет зарегистрирован `PrometheusMeterRegistry`, который будет использоваться во всех компонентах, собирающих метрики.

## Соглашения об именовании и составе метрик

Мы следуем и вам рекомендуем использовать нотацию, описанную в [спецификации OpenTelemetry](https://opentelemetry.io/docs/specs/otel/metrics/).

## Модификация конфигурации

Для внесения изменений в конфигурацию `PrometheusMeterRegistry` нужно добавить в контейнер `PrometheusMeterRegistryInitializer`.

**Важно**, `PrometheusMeterRegistryInitializer` применяется только один раз при инициализации приложения.

Например, мы хотим добавить общий тег для всех метрик:

```java
@Module
interface MetricsConfigModule {
    default PrometheusMeterRegistryInitializer commonTagsInit() {
        return registry -> {
            registry.config().commonTags("tag", "value");
            return registry;
        };
    }
}
```

Так же стандартные метрики имеют некоторые конфигурации, такие как service layer objectives для Distribution summary метрик.
Имена полей конфигурации можно посмотреть в  `ru.tinkoff.kora.micrometer.module.MetricsConfig`.
