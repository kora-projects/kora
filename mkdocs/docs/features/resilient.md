# Resilient

Предоставляет модули позволяющие использовать такие компоненты как: *CircuitBreaker, Fallback, Timeout, Retryable*.

Оглавление:
- [CircuitBreaker](#circuitbreaker)
- [Retryable](#retryable)
- [Timeout](#timeout)
- [Fallback](#fallback)
- [Поддерживаемые AOP типы](#supported-types)

## CircuitBreaker

CircuitBreaker – это прокси, который контролирует поток к запросам к конкретному методу, 
может находиться в нескольких состояниях в зависимости от поведения (OPEN, CLOSED, HALF_OPEN).

#### Dependency

```groovy
implementation 'ru.tinkoff.kora:resilient-circuitbreaker'
```

#### Module

```java
@KoraApp
public interface AppWithConfig extends CircuitBreakerModule {
    
}
```

#### Example

```java
@Component
public class Target {

    @CircuitBreaker("custom")
    public String getValue() {
        throw new IllegalStateException("Ops");
    }
}
```

#### Конфигурация

Существует *default* конфигурация, которая применяется к CircuitBreaker при создании 
и затем применяются именованные настройки конкретного CircuitBreaker для переопределения дефолтов.

Можно изменить дефолтные настройки для всех CircuitBreaker одновременно изменив *default* конфигурацию.

Конфигурация *default* CircuitBreaker'а:
```hocon
resilient {
  circuitbreaker { 
    default {
      failureRateThreshold = 50
      waitDurationInOpenState = 25s
      permittedCallsInHalfOpenState = 10
      slidingWindowSize = 100L
      minimumRequiredCalls = 50L
    }
  }
}
```

Пример переопределения именованных настроек для определенного CircuitBreaker'а:
```hocon
resilient {
  circuitbreaker { 
    custom {
      waitDurationInOpenState = 1s
    }
  }
}
```

##### Exception Predicate

Для регистрации какие ошибки следует записывать как ошибки со стороны CircuitBreaker, можно переопределить дефолтный *CircuitBreakerFailurePredicate*, 
требуется имплементировать и зарегистрировать свой Bean в контексте и указать в конфигурации CircuitBreaker его имя возвращаемое в методе *name()*.

```java
@Component
public final class MyFailurePredicate implements CircuitBreakerFailurePredicate {

    @Override
    public String name() {
        return "MyPredicate";
    }
    
    @Override
    public boolean test(Throwable throwable) {
        return true;
    }
}
```

Конфигурация:
```hocon
resilient {
  circuitbreaker { 
    default {
      failurePredicateName = "MyPredicate"
    }
  }
}
```

## Retryable

Retryable - предоставляет возможность настраивать политику Retry проаннотированного метода.

#### Dependency

```groovy
implementation 'ru.tinkoff.kora:resilient-retry'
```

#### Module

```java
@KoraApp
public interface AppWithConfig extends RetryableModule {
    
}
```

#### Example

```java
@Component
public class RetryableTarget {

    @Retryable("custom1")
    public void execute(String arg) {
        throw new IllegalStateException("Ops");
    }
}
```

#### Конфигурация

Существует *default* конфигурация, которая применяется к Retryable при создании
и затем применяются именованные настройки конкретного Retryable для переопределения дефолтов.

Можно изменить дефолтные настройки для всех Retryable одновременно изменив *default* конфигурацию.

Конфигурация *default* Retryable'а:
```hocon
resilient {
  retry {
    default {
      delay = "100ms"
      delayStep = "100ms"
      attempts = 2
      failurePredicateName = "MyPredicate"
    }
  }
}
```

##### Exception Predicate

Пример имплементации:
```java
@Component
public final class MyFailurePredicate implements RetrierFailurePredicate {

    @Override
    public String name() {
        return "MyPredicate";
    }

    @Override
    public boolean test(Throwable throwable) {
        return true;
    }
}
```

Конфигурация:
```hocon
resilient {
  retry {
    default {
      failurePredicateName = "MyPredicate"
    }
  }
}
```

## Timeout

Timeout - предоставляет возможность задания параметров Timeout'а для проаннотированного метода.

#### Dependency

```groovy
implementation 'ru.tinkoff.kora:resilient-timeout'
```

#### Module

```java
@KoraApp
public interface AppWithConfig extends TimeoutModule {
    
}
```

#### Example

```java
@Component
public class Target {

    @Timeout("custom")
    public String getValue() {
        try {
            Thread.sleep(3000);
            return "OK";
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
```

#### Конфигурация

Существует *default* конфигурация, которая применяется к Timeout при создании
и затем применяются именованные настройки конкретного Timeout для переопределения дефолтов.

Можно изменить дефолтные настройки для всех Timeout одновременно изменив *default* конфигурацию.

Конфигурация *default* Timeout'а:
```hocon
resilient {
  timeout { 
    default {
      duration = 1s
    }
  }
}
```

## Fallback

Fallback - предоставляет возможность указания метода который будет вызван в случае
если исключение брошенное про аннотированным методом будет удовлетворено *FallbackFailurePredicate*.

#### Dependency

```groovy
implementation 'ru.tinkoff.kora:resilient-fallback'
```

#### Module

```java
@KoraApp
public interface AppWithConfig extends FallbackModule {
    
}
```

#### Example

Пример для *Fallback* без аргументов:
```java
@Component
public class Target {

    @Fallback(value = "custom", method = "getFallback()")
    public String getValue() {
        return "value";
    }

    protected String getFallback() {
        return "fallback";
    }
}
```

Пример для *Fallback* с аргументами:
```java
@Component
public class Target {

    @Fallback(value = "custom", method = "getFallback(arg3, arg1)")     // Передает аргументы проаннотированного метода в указанном порядке в Fallback метод
    public String getValue(String arg1, Integer arg2, Long arg3) {
        return "value";
    }

    protected String getFallback(Long argLong, String argString) {
        return "fallback";
    }
}
```

#### Конфигурация

Существует *default* конфигурация, которая применяется к Fallback при создании
и затем применяются именованные настройки конкретного Fallback для переопределения дефолтов.

Можно изменить дефолтные настройки для всех Fallback одновременно изменив *default* конфигурацию.

Пример имплементации:
```java
@Component
public final class MyFailurePredicate implements FallbackFailurePredicate {

    @Override
    public String name() {
        return "MyPredicate";
    }

    @Override
    public boolean test(Throwable throwable) {
        return true;
    }
}
```

Конфигурация:
```hocon
resilient {
  fallback { 
    default {
      failurePredicateName = "MyPredicate"
    }
  }
}
```

## Supported Types

Поддерживаемые типы возвращаемых методов для AOP:
- *Java*:
  - Sync
  - Project Reactor
- *Kotlin*:
  - Sync
  - Suspend
  - Flow
