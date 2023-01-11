# Конфигурации

Для конфигурации kora framework использует библиотеку [typesafe config](https://github.com/lightbend/config), которая умеет читать конфигурации в формате [HOCON](https://github.com/lightbend/config/blob/master/HOCON.md).  

## Подключение конфигураций

К приложению необходимо добавить модуль `ConfigModule` через наследование. 
Он добавит в контекст приложения вычитанный `typesafe config` и компонент, который будет следить за обновлениями внешнего файла конфигурации, если он задан через `-Dconfig.file=some-file.conf`.

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
