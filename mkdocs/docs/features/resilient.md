### Resilient

#### CircuitBreaker

CircuitBreaker – это прокси, который контролирует поток к запросам к конкретному методу, 
может находиться в нескольких состояниях в зависимости от поведения (OPEN, CLOSED, HALF_OPEN).

#### Dependency

```groovy
implementation 'ru.tinkoff.kora:resilient-circuitbreaker'
```

##### Конфигурация

Существует *default* конфигурация, которая применяется к CircuitBreaker при создании 
и затем применяются именованные настройки конкретного CircuitBreaker для переопределения дефолтов.

Можно изменить дефолтные настройки для всех CircuitBreaker одновременно изменив *default* конфигурацию.

Конфигурация *default* CircuitBreaker'а:
```hocon
resilient {
  circuitBreaker { 
    fast {
      default {
        failureRateThreshold = 50
        waitDurationInOpenState = 25s
        permittedCallsInHalfOpenState = 10
        slidingWindowSize = 100L
        minimumRequiredCalls = 50L
        failurePredicateName = "ru.tinkoff.kora.resilient.circuitbreaker.impl.FastCircuitBreakerFailurePredicate"
      }
    }
  }
}
```

Пример переопределения именованных настроек для определенного CircuitBreaker'а:
```hocon
resilient {
  circuitBreaker { 
    fast {
      custom {
        waitDurationInOpenState = 1s
      }
    }
  }
}
```

###### Пример

```java
@Component
public class Example {

    @CircuitBreaker(value = "custom", fallbackMethod = "getFallback")
    public String getValue() {
        return "OK";
    }

    protected String getFallback() {
        return "FALLBACK";
    }
}
```

##### Exception Predicate

Для регистрации какие ошибки следует записывать как ошибки со стороны CircuitBreaker, можно переопределить дефолтный *CircuitBreakerFailurePredicate*, 
требуется имплементировать и зарегистрировать свой Bean в контексте и указать в конфигурации CircuitBreaker его имя.

```java
package ru.tinkoff.kora;

public final class SimpleCircuitBreakerFailurePredicate implements CircuitBreakerFailurePredicate {

    @Override
    public String name() {
        return "MyName";
    }
    
    @Override
    public boolean test(Throwable throwable) {
        return true;
    }
}
```

Конфигурация *default* CircuitBreaker'а:
```hocon
resilient {
  circuitBreaker { 
    fast {
      default {
        failurePredicateName = "MyName"
      }
    }
  }
}
```

#### AOP

Поддерживаемые типы возвращаемых методов.

*Java*:
- Sync
- Project Reactor

*Kotlin*:
- Sync
- Suspend
- Flow
