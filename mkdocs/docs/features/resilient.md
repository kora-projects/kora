# Resilient

Предоставляет модули позволяющие использовать такие компоненты как: *CircuitBreaker, Fallback, Timeout, Retryable*.

Оглавление:

- [CircuitBreaker](#circuitbreaker)
- [Retryable](#retryable)
- [Timeout](#timeout)
- [Fallback](#fallback)
- [Combination](#Combination)
- [Поддерживаемые AOP типы](#supported-types)

## CircuitBreaker

CircuitBreaker – это прокси, который контролирует поток к запросам к конкретному методу,
может находиться в нескольких состояниях в зависимости от поведения (OPEN, CLOSED, HALF_OPEN).

Цель применения CircuitBreaker — дать системе время на исправление ошибки, которая вызвала сбой, прежде чем разрешить приложению попытаться выполнить операцию еще раз. 
Шаблон CircuitBreaker обеспечивает стабильность, пока система восстанавливается после сбоя и снижает влияние на производительность.

- **CLOSED**: Запрос приложения перенаправляется на операцию. Прокси ведет подсчет числа недавних сбоев в рамках установленного кол-ва операций ([`slidingWindowSize`](#конфигурация)) поступающих через прокси, и если вызов операции не завершился успешно, прокси увеличивает это число. 
  Если число запросов превысило установленный минимальный потолок необходимый для подсчетов ([`minimumRequiredCalls`](#конфигурация)) и число недавних сбоев превышает заданный порог ([`failureRateThreshold`](#конфигурация)) в течение заданного периода времени, прокси переводится в состояние **OPEN**. 
- **OPEN**: Во время нахождения в таком статусе запрос от приложения немедленно завершает с ошибкой и исключение возвращается в приложение.
  На этом этапе прокси запускает таймер времени ожидания ([`waitDurationInOpenState`](#конфигурация)), и по истечении времени этого таймера прокси переводится в состояние **HALF-OPEN**.
- **HALF-OPEN**: Ограниченному числу запросов ([`permittedCallsInHalfOpenState`](#конфигурация)) от приложения разрешено проходить через операцию и вызывать ее. Если эти запросы выполняются успешно, предполагается, что ошибка, которая ранее вызывала
  сбой, устранена, а автоматический выключатель переходит в состояние **CLOSED** (счетчик сбоев сбрасывается). Если какой-либо запрос завершается со сбоем, автоматическое выключение предполагает, что
  неисправность все еще присутствует, поэтому он возвращается в состояние **OPEN** и перезапускает таймер времени ожидания ([`waitDurationInOpenState`](#конфигурация)), чтобы дать системе дополнительное время на восстановление после сбоя.

Состояние Half-Open помогает предотвратить быстрый рост запросов к сервису. Т.к. после начала работы сервиса, некоторое время он может быть способен обрабатывать ограниченное число запросов до полного
восстановления.

Изначально имеет состояние CLOSED.

#### Dependency

**Java**:
```groovy
annotationProcessor "ru.tinkoff.kora:annotation-processors"
implementation "ru.tinkoff.kora:resilient-circuitbreaker"
```

**Kotlin**:
```groovy
ksp "ru.tinkoff.kora:annotation-processors"
implementation "ru.tinkoff.kora:resilient-circuitbreaker"
```

#### Module

```java
@KoraApp
public interface ApplicationModules extends CircuitBreakerModule { }
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

Описание конфигурации:

- `slidingWindowSize` - Предельное кол-во запросов в рамках которых рассчитывается *failureRateThreshold* для определения состояния CircuitBreaker
- `minimumRequiredCalls` - Минимальное кол-во запросов необходимое для начала расчета состояния CircuitBreaker
- `failureRateThreshold` - Процент неуспешных запросов который необходим для перехода в состояния **OPEN** (имеет значения от *1 до 100*)
- `waitDurationInOpenState` - Время ожидания в статусе **OPEN**, после которого осуществляется переход в статус **HALF-OPEN**
- `permittedCallsInHalfOpenState` - Необходимое кол-во запросов в статусе **HALF-OPEN** которые должны завершится успехом для перехода в **CLOSED**
- `failurePredicateName` - Имя предиката который будет регистрировать ошибки подходящие под требования CircuitBreaker *(Optional)*

Существует *default* конфигурация, которая применяется к CircuitBreaker при создании
и затем применяются именованные настройки конкретного CircuitBreaker для переопределения дефолтов.

Можно изменить дефолтные настройки для всех CircuitBreaker одновременно изменив *default* конфигурацию.

Пример конфигурации *default* CircuitBreaker'а:
```hocon
resilient {
    circuitbreaker {
        default {
            slidingWindowSize = 100L
            minimumRequiredCalls = 50L
            failureRateThreshold = 50
            waitDurationInOpenState = 25s
            permittedCallsInHalfOpenState = 10
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

Позволяет указать когда требуется повторить попытку выполнения метода, настроить параметры повторения, в случае если методом была брошена ошибка (Exception) соответствующая заданным требованиям повторения (*RetrierFailurePredicate*).

#### Dependency

**Java**:
```groovy
annotationProcessor "ru.tinkoff.kora:annotation-processors"
implementation "ru.tinkoff.kora:resilient-retry"
```

**Kotlin**:
```groovy
ksp "ru.tinkoff.kora:annotation-processors"
implementation "ru.tinkoff.kora:resilient-retry"
```

#### Module

```java
@KoraApp
public interface ApplicationModules extends RetryableModule { }
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

Описание конфигурации:

- `delay` - Начальное время задержки для операции при Retry
- `delayStep` - Шаг задержки который аккумулируется в следствии последующих попыток Retry
- `attempts` - Кол-во попыток Retry для операции
- `failurePredicateName` - Имя предиката который будет регистрировать ошибки подходящие под требования Retryable *(Optional)*

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

Позволяет задать предельное время выполнения операции / метода.

#### Dependency

**Java**:
```groovy
annotationProcessor "ru.tinkoff.kora:annotation-processors"
implementation "ru.tinkoff.kora:resilient-timeout"
```

**Kotlin**:
```groovy
ksp "ru.tinkoff.kora:annotation-processors"
implementation "ru.tinkoff.kora:resilient-timeout"
```

#### Module

```java
@KoraApp
public interface ApplicationModules extends TimeoutModule { }
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

Описание конфигурации:

- duration - Предельное время работы операции после которого будет брошен TimeoutException.

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
если исключение брошенное проаннотированным методом будет удовлетворено (*FallbackFailurePredicate*).

#### Dependency

**Java**:
```groovy
annotationProcessor "ru.tinkoff.kora:annotation-processors"
implementation "ru.tinkoff.kora:resilient-fallback"
```

**Kotlin**:
```groovy
ksp "ru.tinkoff.kora:annotation-processors"
implementation "ru.tinkoff.kora:resilient-fallback"
```

#### Module

```java
@KoraApp
public interface ApplicationModules extends FallbackModule { }
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

Описание конфигурации:

- `failurePredicateName` - Имя предиката который будет регистрировать ошибки подходящие под требования Fallback *(Optional)*

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

## Combination

Можно совмещать одновременно над одним методом все вышеперечисленные аннотации.

Порядок применения аннотаций зависит от порядка объявления аннотаций.
Вы можете поменять порядок по своему желанию и комбинировать его с другими аннотациями, которые точно также применяются в порядке объявления.

Пример класса:
```java
@Component
public class ResilientExample {

    @Fallback(value = "default", method = "getFallback(arg1)")   // 4
    @CircuitBreaker("default")                                   // 3
    @Retryable("default")                                        // 2
    @Timeout("default")                                          // 1
    public String getValueSync(String arg1) {
        return "result-" + arg1;
    }

    protected String getFallback(String arg1) {                  // 4
        return "fallback-" + arg1;
    }
}   
```

В примере выше:

1) Применяется *@Timeout* который говорит что метод не должен выполняться дольше времени указанного в конфигурации
2) Применяется *Retryable* который будет пытаться повторить выполнение метода указанное в конфигурации кол-во раз в случае если метод бросил исключение по цепочке (включая *@Timeout*)
3) Применяется *@CircuitBreaker* который будет работать согласно конфигурации и [состоянию](#circuitbreaker) в зависимости успешного результата метода или если метод бросил исключение по цепочке (включая *@Timeout* & *@Retryable*)
4) Применяется *@Fallback* который будет вызвать *getFallback* метод с аргументом *arg1* в случае если метод бросил исключение по цепочке (включая *@Timeout* & *@Retryable* & *@CircuitBreaker*)

Пример конфигурации:
```hocon
resilient {
    circuitbreaker {
        default {
            slidingWindowSize = 1
            minimumRequiredCalls = 1
            failureRateThreshold = 100
            permittedCallsInHalfOpenState = 1
            waitDurationInOpenState = 1s
        }
    }
    timeout {
        default {
            duration = 300ms
        }
    }
    retry {
        default {
            delay = 100ms
            attempts = 2
        }
    }
}
```

## Supported Types

Поддерживаемые типы возвращаемых методов для AOP:

Java:

- Обычный метод
- Project Reactor (Mono & Flux)

Kotlin:

- Обычный метод
- Suspend
- Flow
