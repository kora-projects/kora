# Cache

Модуль предоставляет AOP реализацию Cache'ей.

# Getting Started

Допустим имеется класс:
```java
@Component
public class CacheableTargetSync {

    public Long getValue(String arg1, BigDecimal arg2) {
        return ThreadLocalRandom.current().nextLong();
    }

    public Long putValue(String arg1, BigDecimal arg2) {
        return ThreadLocalRandom.current().nextLong();
    }

    public void evictValue(String arg1, BigDecimal arg2) {
        // do nothing
    }

    public void evictAll() {
        // do nothing
    }
}
```

## Get

Для кэширования и получения значения из кэша для метода *getValue()* следует проаннотировать его аннотацией *@Cacheable*.

Метод проаннотированный *@Cacheable* будет пытаться взять значение по ключу из кэша который указан в *value*, в случае если значение для такого ключа не существует,
будет вызван сам метод и его значение будет закэшированно для последующих операций и возвращено.
Имя кэша из *value* соответствует его имени в конфигурации файла (hocon)

Ключ для кэша составляет из аргументов метода, порядок аргументов имеет значение, в данном случае он будет составляться из значений *arg1* и *arg2*.

В *value* указывается имя кеша где будет храниться значения.
Место в каком кэше будет храниться значение, определяется его именем и его имплементацией.
Реализация указывается в *tags* либо используется дефолтная если подключен дефолтный модуль (пример модуля *DefaultCaffeineCacheModule*/*DefaultRedisCacheModule*).

```java
@Component
public class CacheableTargetSync {

    @Cacheable(name = "my_cache")
    public Long getValue(String arg1, BigDecimal arg2) {
        return ThreadLocalRandom.current().nextLong();
    }

    public Long putValue(String arg1, BigDecimal arg2) {
        return ThreadLocalRandom.current().nextLong();
    }

    public void evictValue(String arg1, BigDecimal arg2) {
        // do nothing
    }

    public void evictAll() {
        // do nothing
    }
}
```

## Put

Для добавления значений в кэш через метод *putValue()* следует проаннотировать его аннотацией *@CachePut*.

Метод проаннотированный *@CachePut* будет вызван и его значение положено во все кэш определенный в *value*, значение это значение будет возвращено дальше.
Имя кэша из *value* соответствует его имени в файле конфигурации (hocon)

Ключ для кэша составляет из аргументов метода, порядок аргументов имеет значение, в данном случае он будет составляться из значений *arg1* и *arg2*.

В *value* указывается имя кеша где будет храниться значения.
Место в каком кэше будет храниться значение, определяется его именем и его имплементацией.
Реализация указывается в *tags* либо используется дефолтная если подключен дефолтный модуль (пример модуля *DefaultCaffeineCacheModule*/*DefaultRedisCacheModule*).

```java
@Component
public class CacheableTargetSync {

    @Cacheable(name = "my_cache")
    public Long getValue(String arg1, BigDecimal arg2) {
        return ThreadLocalRandom.current().nextLong();
    }

    @CachePut(name = "my_cache")
    public Long putValue(String arg1, BigDecimal arg2) {
        return ThreadLocalRandom.current().nextLong();
    }

    public void evictValue(String arg1, BigDecimal arg2) {
        // do nothing
    }

    public void evictAll() {
        // do nothing
    }
}
```

## Invalidate

Для удаления значения по ключу из кэша через метод *evictValue()* следует проаннотировать его аннотацией *@CacheInvalidate*.

Метод проаннотированный *@CacheInvalidate* будет вызван и затем по ключу для кэша определенного в *value* будут удалены значения по ключу.
Имя кэша из *value* соответствует его имени в файле конфигурации (hocon)

Ключ для кэша составляет из аргументов метода, порядок аргументов имеет значение, в данном случае он будет составляться из значений *arg1* и *arg2*.

В *value* указывается имя кеша где будет храниться значения.
Место в каком кэше будет храниться значение, определяется его именем и его имплементацией.
Реализация указывается в *tags* либо используется дефолтная если подключен дефолтный модуль (пример модуля *DefaultCaffeineCacheModule*/*DefaultRedisCacheModule*).

```java
@Component
public class CacheableTargetSync {

    @Cacheable(name = "my_cache")
    public Long getValue(String arg1, BigDecimal arg2) {
        return ThreadLocalRandom.current().nextLong();
    }

    @CachePut(name = "my_cache")
    public Long putValue(String arg1, BigDecimal arg2) {
        return ThreadLocalRandom.current().nextLong();
    }

    @CacheInvalidate(name = "my_cache")
    public void evictValue(String arg1, BigDecimal arg2) {
        // do nothing
    }

    public void evictAll() {
        // do nothing
    }
}
```

### InvalidateAll

Для удаления всех значений из кэша через метод *evictAll()* следует проаннотировать его аннотацией *@CacheInvalidate* и указать параметр *invalidateAll = true*.

Метод проаннотированный *@CacheInvalidate* будет вызван и затем будут удалены все из кэша определенного в *value*.
Имя кэша из *value* соответствует его имени в файле конфигурации (hocon)

В *value* указывается имя кеша где будет храниться значения.
Место в каком кэше будет храниться значение, определяется его именем и его имплементацией.
Реализация указывается в *tags* либо используется дефолтная если подключен дефолтный модуль (пример модуля *DefaultCaffeineCacheModule*/*DefaultRedisCacheModule*).

```java
@Component
public class CacheableTargetSync {

    @Cacheable(name = "my_cache")
    public Long getValue(String arg1, BigDecimal arg2) {
        return ThreadLocalRandom.current().nextLong();
    }

    @CachePut(name = "my_cache")
    public Long putValue(String arg1, BigDecimal arg2) {
        return ThreadLocalRandom.current().nextLong();
    }

    @CacheInvalidate(name = "my_cache")
    public void evictValue(String arg1, BigDecimal arg2) {
        // do nothing
    }

    @CacheInvalidate(name = "my_cache", invalidateAll = true)
    public void evictAll() {
        // do nothing
    }
}
```

## Reactor Mono

Все примеры были бы аналогичны и поддерживаются для Reactor Mono, пример такого класса:

```java
@Component
public class CacheableTargetMono {

    @Cacheable(name = "my_cache")
    public Mono<Long> getValue(String arg1, BigDecimal arg2) {
        return Mono.just(ThreadLocalRandom.current().nextLong());
    }

    @CachePut(name = "my_cache")
    public Mono<Long> putValue(String arg1, BigDecimal arg2) {
        return Mono.just(ThreadLocalRandom.current().nextLong());
    }

    @CacheInvalidate(name = "my_cache")
    public Mono<Void> evictValue(String arg1, BigDecimal arg2) {
        return Mono.empty();
    }

    @CacheInvalidate(name = "my_cache", invalidateAll = true)
    public Mono<Void> evictAll() {
        return Mono.empty();
    }
}
```

## Несколько кешей

В случае если у вас есть несколько кешей требуется использовать для базового и самого распространенного кеша модуль *Default* конфигурации, а 
кэш другой имплементации размечать в аннотации в поле *tags*, подставляя туда нужную имплементацию *CacheManager*.

Конфигурация будет выглядеть следующим образом:
```java
@KoraApp
public interface AppWithConfig
    extends RedisCacheModule,     // Только размеченные в аннотации
    DefaultCaffeineCacheModule {  // Дефолтый кэш
}
```

А сам класс с кешами так:
```java
@Component
public class CacheableTargetMono {

    @Cacheable(name = "caffeine_cache")
    @Cacheable(name = "redis_cache", tags = RedisCacheManager.class)
    public Mono<Long> getValueCaffeine(String arg1, BigDecimal arg2) {
        return Mono.just(ThreadLocalRandom.current().nextLong());
    }
}
```

Порядок вызова кэшей соответствует порядку в котором объявлены аннотации.

## Порядок аргументов для ключа

В случае если метод принимает не нужные аргументы, которые не хочется использовать как часть ключа для кэша либо же порядок аргументов не соответствует порядку аргументов для ключа кэша, 
следует использовать атрибут аннотации *parameters* в котором определить какие именно аргументы использовать и в каком порядке.

В примере ниже ключ для кэша для обоих методов будет составлен идентично.

В случае если в каком либо месте у вас будет не соответствие ключа по сигнатуре аргументов метода, вы получите ошибку на этапе компиляции.

```java
@Component
public class CacheableTargetSync {

    @Cacheable(name = "my_cache")
    public Long getValue(String arg1, BigDecimal arg2) {
        return ThreadLocalRandom.current().nextLong();
    }
    
    @Cacheable(name = "my_cache", parameters = {"arg1", "arg2"})
    public Long getValue(BigDecimal arg2, String arg3, String arg1) {
        return ThreadLocalRandom.current().nextLong();
    }
}
```

# Конфигурации

В текущий момент реализованы две имплементации для кешей:
- [Caffeine](https://github.com/ben-manes/caffeine)
- [Redis](https://github.com/lettuce-io/lettuce-core)

## Caffeine

Описание конфигурации:
* `expireAfterWrite` - Время по истечении которого значение для ключа будет удалено, отчитывается после добавления значения. *(Optional)*
* `expireAfterAccess` - Время по истечении которого значение для ключа будет удалено, отчитывается после операции чтения. *(Optional)*
* `initialSize` - Начальный размер кэша (помогает избежать ресазинга в случае активного набухания кэша) *(Optional)*
* `maximumSize` - Максимальный размер кэша (При достижении границы **или чуть ранее** будет исключать из кэша наименее актуальные значения) *(Optional)*

Предоставляет модули *DefaultCaffeineCacheModule* для использования Caffeine как дефолт реализацию кеша для приложения,
также *CaffeineCacheModule* для использования Caffeine кеша только где указаны соответсвующее теги в аннотации.

Пример конфигурации для *my_cache* кэша:
```hocon
cache {
  caffeine {
    my_cache { 
      expireAfterWrite = 10s
      expireAfterAccess = 10s
      initialSize = 10
      maximumSize = 10
    }
  }
}
```

## Redis

Описание конфигурации подключения к Redis (Lettuce клиент):
* uri - URI редиса
* user - Юзер *(Optional)*
* password - Пароль юзера *(Optional)*
* database - Номер базы *(Optional)*
* protocol - Протокол *(Optional)*
* socketTimeout - Время таймаута коннекта *(Optional)*
* commandTimeout - Время таймаута команды *(Optional)*

Описание конфигурации Redis Cache:
* expireAfterWrite - При записи устанавливает время expiration (см. https://redis.io/commands/psetex/)
* expireAfterAccess - При чтении устанавливает время expiration (см. https://redis.io/commands/getex/)

Предоставляет модули *DefaultRedisCacheModule* для использования Redis как дефолт реализацию кеша для приложения,
также *RedisCacheModule* для использования Redis кеша только где указаны соответсвующее теги в аннотации.

Пример конфигурации для *my_cache* кэша.

Требуется обязательно сконфигурировать клиент Lettuce для доступа в Redis.

```hocon
lettuce {
  uri = "redis://locahost:6379"
  user = "admin"
  password = "12345"
  database = 1
  protocol = "REP3"
  socketTimeout = 15s
  commandTimeout = 15s
}
cache {
  redis {
    my_cache {
      expireAfterWrite = 10s
      expireAfterAccess = 10s
    }
  }
}
```

## Loadable cache

Библиотека предоставляет компонент для построения сущности, которая объединяет операции GET и PUT, без использования AOP аннотаций - `LoadableCache`

### Кеширование блокирующих операций

Иногда у нас есть долгая операция, которую бы мы хотели кешировать и запускать на отдельном потоке исполнения при использовании асинхронного апи.   
Для создания такого `LoadableCache` можно воспользоваться фабричным методом `LoadableCache.blocking`, он создаёт такой cache, который при вызове `LoadableCache.getAsync` вызовет метод `load`, из примера ниже, на переданном `ExecutorService`.
При этом вызов `LoadableCache.get` вызовет 'load' на том же потоке 

```java
@Module
interface SomeEntityModule {
  default LoadableCache<SomeEntity> someEntityLoadCache(CacheManager<String, SomeEntity> cacheManager, SomeService someService, ExecutorService executor) {
    return LoadableCache.blocking(cacheManager.getCache("some.service"), executor, someService::loadEntity);
  }
}

@Component
class OtherService {
  private final LoadableCache<SomeEntity> someEntityLoadableCache;
  
  public OtherService(LoadableCache<SomeEntity> someEntityLoadableCache) {
      this.someEntityLoadableCache = someEntityLoadableCache;
  }
}
```

### Кеширование неблокирующих операций

По аналогии с методом `LoadableCache.blocking`, существуют фабричные методы `LoadableCache.nonBlocking` и `LoadableCache.async`, которые просто умеют кешировать результат переданной функции без  

## Supported Types

Поддерживаемые типы возвращаемых методов для AOP:

Java:
* Обычный метод
* Project Reactor (Mono)

Kotlin:
* Обычный метод
* Suspend
