# Логирование

В kora имеется поддержка структурированных логов.
Передать структурированные данные в запись лога можно двумя способами:

- Через Marker:

```java
var marker = StructuredArgument.marker("key", gen -> {
   gen.writeString("value");
});

log.info(marker, "message");
```


- Через параметры:

```java
var parameter = StructuredArgument.arg("key", gen -> {
 gen.writeString("value");
});
log.info("message", parameter);
```

Методы `marker` и `arg` также принимают в качестве шорткатов Long, Integer, String, Boolean и Map<String, String>.

Структурные данные можно прикреплять ко всем записям в рамках контекста с помощью класса `ru.tinkoff.kora.logging.common.MDC`:

```java
MDC.put("key",gen->gen.writeString("value"));
```

Если вы используете `AsyncAppender` для отправки логов, то для корректной передачи MDC параметров нужно воспользоваться `ru.tinkoff.kora.logging.logback.KoraAsyncAppender`,
который передаст делегату `ru.tinkoff.kora.logging.logback.KoraLoggingEvent`, содержащий, в том числе, структурный MDC.
