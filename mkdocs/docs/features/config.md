# Конфигурации

Kora поддерживает два формата конфигурационных файлов:

- [HOCON](https://github.com/lightbend/config/blob/master/HOCON.md) с помощью [typesafe config](https://github.com/lightbend/config) через модуль `HoconConfigModule` из `config-hocon`
- YAML через модуль `YamlConfigModule` из `config-yaml`

В обоих случаях по умолчанию читаются файлы:

- `reference.(yml|conf)` для общих значений
- файл, указанный через `config.file` или `config.resource`
- при отсутствии предыдущего пункта прочитается файл по-умолчанию `application.(yml|conf)`

## ConfigSource

Для упрощения создания пользовательских конфигураций следует использовать аннотацию `ConfigSource`.

Рассмотрим пример:

```java

@ConfigSource("services.foo")
record FooServiceConfig(String bar, int baz) {}
```

Этот пример кода добавит в контейнер экземпляр класса `FooServiceConfig`, который при создании будет ожидать конфигурацию следующего вида:

```hocon
services {
    foo {
      bar = "some value"
      baz = 10
    }
}
```

После этого этот класс уже можно использовать как зависимость в других классах.

```java
public final class FooService {
    private final FooServiceConfig config;

    public FooService(FooServiceConfig config) {
        this.config = config;
    }
}
```

## Значения по умолчанию

Если есть необходимость использовать в классе значения по умолчанию, то можно воспользоваться таким форматом:

```java

@ConfigSource("services.foo")
interface FooServiceConfig {
    String bar();

    default int baz() {
        return 42;
    }
}
```
