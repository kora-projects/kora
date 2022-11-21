# Контейнер приложения

Работа контейнера в kora разделена на две части, на то что выполняется в рантайме и на то что выполняется во время компиляции.

## Runtime часть контейнера

По сути вся логика времени исполнения вызывается через метод `KoraApplication.run`, он делает следующие вещи:

* Инициализация всех компонентов в контейнере
* Отслеживание изменений в контейнере
* Атомарное обновление графа при изменениях
* Graceful shutdown при получении сигнала SIGTERM

## Compile time часть контейнера

На этап компиляции вынесен поиск компонентов для построения всего контейнера. Это происходит при помощи обработки аннотации `KoraApp`. Её необходимо проставлять на интерфейс, внутри которого лежат
фабричные методы для создания компонентов.

```java
import ru.tinkoff.kora.common.KoraApp;

@KoraApp
public interface Application {

    default SomeService someService() {
        return new SomeService();
    }

    default OtherService otherService(SomeService someService) {
        return new OtherService(someService);
    }
}
```

Например, этот контейнер описывает две фабрики, и фабрика `otherService` требует компонент, создаваемый фабрикой `someService`. Это самый базовый способ как в контейнере могут появляться компоненты.

### Модули

Сами компоненты ищутся в модулях. Под модулем понимается интерфейс, в котором находятся фабричные методы. Kora не делает автоматический поиск модулей, как это делают некоторые другие DI решения. Все
необходимые модули должны быть подключены явно в интерфейс, помеченный аннотацией `KoraApp`, через наследование.

```java
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.http.server.common.json.JsonHttpServerCommonModule;
import ru.tinkoff.kora.http.server.undertow.UndertowHttpServerModule;
import ru.tinkoff.kora.logging.logback.LogbackModule;

@KoraApp
public interface Application extends
    LogbackModule,
    JsonHttpServerModule,
    UndertowHttpServerModule {
}
```

### Аннотированные модули и компоненты

Для упрощения добавления модулей и компонентов в контейнер есть две аннотации

* `Module`
* `Component`

#### Module

Аннотация `Module` помечает интерфейс, который нужно примешать в наш контейнер на этапе компиляции, все компоненты внутри него становятся доступными для контейнера.

```java
import ru.tinkoff.kora.common.Module;

@Module
interface SomeModule {
    default SomeService someService() {
        return new SomeService();
    }
}
```

#### Component

Аннотация `Component` помечает класс, как доступный в контейнере. При этом к классу предъявляются следующие требования:

* Класс не должен быть абстрактным
* У класса должен быть только один публичный конструктор

```java
import ru.tinkoff.kora.common.Component;

@Component
class SomeService {
    private OtherService otherService;

    public SomeService(OtherService otherService) {
        this.otherService = otherService;
    }
}
```

#### KoraSubmodule

Аннотация `KoraModule` помечает интерфейс, для котого нужно собрать модуль для текущего модуля компиляции, в который будут помещены все компоненты помеченные аннотациями `Module` и `Component`. 
Эта аннотация полезна, если вы разбиваете свой проект на модули с точки зрения своего инструмента maven/gradle/etc., каждый из которых ответает за какую то часть функциональности, а само приложение с `KoraApp` собирается в отдельном модуле от логики. Для интерфейса будет сгенерирован интерфейс наследник, в котором будут отнаследованы все интерфейсы помеченные `@Module` и созданы default методы для классов помеченных как `@Component`

Например у вас есть модуль для работы с пользователями, который содержит контроллеры и другие компоненты и в нём есть свой модуль

```java
@KoraSubmodule
interface GithubModule {}
```

И есть модуль со сборкой приложения

```java
@KoraApp
interface Application extends GithubModule {}
```

При этом в итоговое приложение будет примешен модуль сгенерированный на основе GithubModule

### Generic фабрики

Если в контейнере не удалось найти фабрику для конкретного типа, то kora compile time контейнер может попробовать поискать методы с generic параметрами, и при помощи этого метода создать экземпляр
нужного класса

```java
import ru.tinkoff.kora.common.Module;

@Module
interface ValidatorsModule {
    default <T> GenericValidator<T> genericValidator(SomeValidationEntity<T> entity) {
        return new GenericValidator<>(entity);
    }
}
```

Теперь, если какому-то компоненту понадобится GenericValidator как зависимость, то эта фабрика будет использована для его создания

### Расширения и генерация зависимостей

В случае если ни одна из фабрик не смогла предоставить компонент, kora может попробовать сгенерировать эту зависимость на лету.
Для этого предусмотрен механизм расширений. Каждое расширение умеет сказать, может ли оно создать компонент нужного типа. 
Если расширение может это сделать, то оно делает нужную кодогенерирацию и говорит, что можно таким-то образом получить этот компонент.
Например, есть расширения, которые умеют генерировать оптимальные json сериализаторы и десериализаторы, jdbc репозитории и другие компоненты.

Поиск доступных расширений происходит благодаря механизму `ServiceLocator` из всех зависимостей предоставленных в annotation processor scope. 

### Автогенерация компонента из финального класса

Если же ни один из способов выше не смог предоставить компонент, то kora может посмотреть не является ли этот компонент финальным классом с единственным публичным конструктором.
Если он подходит, то этот компонент будет создан просто при помощи конструктора и добавлен в контейнер.

### Получение всех экземпляров типа

В контейнере может быть много экземпляров одного и того же типа, и если их все нужно собрать в одном месте, то следует использовать специальный тип `All`.

```java
package ru.tinkoff.kora.hello.world;

import ru.tinkoff.kora.application.graph.All;

public interface SomeModule {
  default HandlerA handlerA() {
    return new HandlerA();
  }

  default HandlerB handlerB() {
    return new HandlerB();
  }
  
  default SomeProcessor someProcessor(All<Handler> handlers) {
    return new SomeProcessor(handlers);
  }
}
```

Например, у нас есть некоторая сущность `Handler` и его имплементируют N разных типов в контейнере. `SomeProcessor` при этом потребляет все возможные реализации этого типа.
Сам тип `All` имеет следующий контракт:

```java
public interface All<T> extends List<T> {}
```

То есть по сути это `List` и его можно отдавать в конструкторы, которые ожидают лист. А так, это просто маркерный тип.

### <a name="tags"></a>Тегирование компонентов

Иногда есть потребность предоставить разные экземпляры одного и того же типа в разные компоненты. Для этого их можно разграничить по тегам.  
Для этого есть аннотация `Tag`, которая принимает на вход класс тега.
Используется именно класс, а не строковый литерал, потому что это проще для навигации по коду и проще для рефакторинга.

Например, вот так можно раскидать разные экземпляры класса по разным компонентам

```java
package ru.tinkoff.kora.hello.world;

import ru.tinkoff.kora.common.Tag;

public interface SomeModule {
  @Tag(ServiceB.class)
  default ServiceA serviceAForB() {
    return new ServiceA();
  }

  @Tag(ServiceC.class)
  default ServiceA serviceAForC() {
    return new ServiceA();
  }

  default ServiceB serviceB(@Tag(ServiceB.class) ServiceA serviceA) {
    return new ServiceB(serviceA);
  }


  default ServiceC serviceC(@Tag(ServiceC.class) ServiceA serviceA) {
    return new ServiceC(serviceA);
  }
}
```

Теги над методом говорят какой нужно установить тег для компонента, а теги на параметрах говорят какой тег нужно найти в контейнере.
Также теги работают на конструкторе в связке с `Component` или финальными классами. 

## Жизненный цикл контейнера

Контейнер умеет инициализировать все компоненты в правильном порядке, при этом он это делает максимально параллельно, для того, чтобы достичь максимально быстрого времени запуска.
Когда контейнер уже больше не нужен, то запускается механизм освобождения компонентов в обратном порядке.

Также в середине жизненного цикла может произойти обновление какого либо компонента в контейнере, и тогда он обновляет все компоненты зависящие от изменённого. Это происходит атомарно, условно вначале
процесса открывается транзакция, которая закрывается только при условии успешной инициализации всех компонентов и откатывается если произошла хотя бы одна ошибка.

## Прямые и непрямые зависимости

Рассмотрим следующий пример

```java
import ru.tinkoff.kora.application.graph.ValueOf;

public interface SomeModule {
  default ServiceA serviceA() {
    return new ServiceA();
  }

  default ServiceB serviceB() {
    return new ServiceB();
  }

  default ServiceC serviceC(ServiceA serviceA, ValueOf<ServiceB> serviceB) {
    return new ServiceC(serviceA, serviceB);
  }
}
```

У нас два сервиса, и третий сервис, который зависит от них. Но есть разница в жизненном цикле.
Если мы принимаем тип как зависимость напрямую, то мы говорим контейнеру, что при обновлении компонента `ServiceA`, нужно точно также обновить компонент `ServiceC`. Но когда мы принимаем тип
обёртку `ValueOf`, то мы сообщаем контейнеру, что `ServiceC` никак не связан с жизненным циклом `ServiceB` и обновлять в случае изменения `ServiceB` нам не нужно обновлять `ServiceC`.

### ValueOf

`ValueOf` имеет следующий контракт

```java
package ru.tinkoff.kora.application.graph;

import reactor.core.publisher.Mono;

public interface ValueOf<T> {
    T get();

    Mono<Void> refresh();
}
```

Мы можем через него получать текущее актуальное состояние компонента в контейнере при помощи функции `get`.
Этот механизм используется в таких компонентах, которые нельзя перезагружать по своей сути во время исполнения приложения. 
Например, это касается различных серверов, которые слушают сокеты (http, grpc), для них через `ValueOf` поставляются обработчики запросов, которые могут быть подвержены изменениям.

При помощи функции `refresh` мы можем инициировать обновление компонента. Этот механизм например используется в компоненте отслеживающем изменения файла конфигурации на диске. 
При изменении контента файла, он инициирует обновления компонента конфигурации, и дальше все изменения распространяются по цепочке компонентов, связанных прямой связью. 

##<a name="lifecycle"></a> Компоненты с жизненным циклом

По умолчанию у всех компонентов нет жизненного цикла, они просто создаются через конструктор и собираются GC, когда они больше не нужны. Если нужно сделать какие то действия при инициализации
компонента или после его освобождения, необходимо чтобы компонент реализовал интерфейс `Lifecycle`

```java
package ru.tinkoff.kora.application.graph;

import reactor.core.publisher.Mono;

public interface Lifecycle {
    Mono<Void> init();

    Mono<Void> release();
}
```

В контейнере все компоненты инициализируются асинхронно и параллельно настолько, насколько это возможно, и поэтому методы `init` и `release` должны вернуть объект типа `Mono`, который опишит процесс
инициализации и освобождения этого компонента.

## Интроспекция компонентов

Есть ситуации, когда у нас есть некоторый компонент в контейнере, который нам нужно дополнительно изменить или инициализировать, 
но при этом нужно, чтобы никто не начал работать с этим компонентом до того, как мы сделаем эти действия.  
Для этого случая предусмотрен механизм интроспекции компонентов. Вам нужно положить в контейнер объект реализующий интерфейс `GraphInterceptor`. Например, этот механизм используется для запуска
миграций на базе данных при инициализации `JdbcDatabase`

```java
package ru.tinkoff.kora.database.flyway;

import org.flywaydb.core.Flyway;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.GraphInterceptor;
import ru.tinkoff.kora.common.util.ReactorUtils;
import ru.tinkoff.kora.database.jdbc.JdbcDataBase;

public final class FlywayJdbcDatabaseInterceptor implements GraphInterceptor<JdbcDataBase> {
    @Override
    public Mono<JdbcDataBase> init(JdbcDataBase value) {
        return ReactorUtils
            .ioMono(() -> {
                Flyway.configure()
                    .dataSource(value.value())
                    .load()
                    .migrate();
            })
            .thenReturn(value);
    }

    @Override
    public Mono<JdbcDataBase> release(JdbcDataBase value) {
        return Mono.just(value);
    }
}
```

Интерфейс `GraphInterceptor` практически повторяет контракт `Lifecycle`, за исключением возвращаемого типа. Тут мы ожидаем, что метод может вернуть изменённый или вообще другой экземпляр объекта
данного типа, и уже этот объект пойдёт как зависимость остальным компонентам.   


