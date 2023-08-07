# Cache

Модуль предоставляет AOP реализацию Cache'ей.

## Implementations

- [Caffeine](#caffeine)
- [Redis](#redis)

## Examples

### Cache Implementation

Для начала требуется зарегистрировать типизированный `Cache` сигнатуру.

Интерфейс `Cache` должен наследоваться только от предоставляемых Kora'ой реализаций: `CaffeineCache` / `RedisCache`.

Для такого `Cache` будет сгенерирована и добавлена в граф реализация, ее можно будет использовать как `Bean` для внедрения зависимостей.

#### Single key

В случае если ключ кэша представляет собой 1 аргумент, то требуется зарегистрировать `Cache` с сигнатурой соответствующей типам ключа и значения.

```java
public interface DummyCache extends CaffeineCache<String, String> { }
```

#### Composite key

В случае если ключ кэша представляет собой N аргументов, то требуется зарегистрировать `Cache` с использованием `CacheKey` интерфейса который отвечает за композитный ключ, где типизированной сигнатурой указываются N аргументов ключа.

Пример для `Cache` где композитный ключ состоит из 2 элементов:

```java
public interface DummyCache extends CaffeineCache<CacheKey.Key2<String, BigDecimal>, String> { }
```

Для ключей из 3ых составляющих будет использоваться `CacheKey.Key3` и так далее.

#### Config

Для регистрации `Cache` и указания конфига требуется проаннотировать аннотацией `@Cache` где аргумент `value` означает полный путь к конфига.

```java
@Cache("my.full.cache.config.path")
public interface DummyCache extends CaffeineCache<CacheKey.Key2<String, BigDecimal>, String> { }
```

### AOP

Допустим имеется класс:
```java
@Component
public class CacheExample {

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

Для него регистрируем соответствующий `Cache`:
```java
@Cache("dummy")
public interface DummyCache extends CaffeineCache<CacheKey.Key2<String, BigDecimal>, String> { }
```

#### Get

Для кэширования и получения значения из кэша для метода *getValue()* следует проаннотировать его аннотацией *@Cacheable*.

Метод проаннотированный *@Cacheable* будет пытаться взять значение по ключу из кэша который указан в *value*, в случае если значение для такого ключа не существует,
будет вызван сам метод и его значение будет закэшированно для последующих операций и возвращено.
Имя кэша из *value* соответствует его имени в конфигурации файла (hocon)

Ключ для кэша составляет из аргументов метода, порядок аргументов имеет значение, в данном случае он будет составляться из значений *arg1* и *arg2*.

```java
@Component
public class CacheExample {

    @Cacheable()
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

#### Put

Для добавления значений в кэш через метод *putValue()* следует проаннотировать его аннотацией *@CachePut*.

Метод проаннотированный *@CachePut* будет вызван и его значение положено во все кэш определенный в *value*.

Ключ для кэша составляет из аргументов метода, порядок аргументов имеет значение, в данном случае он будет составляться из значений *arg1* и *arg2*.

```java
@Component
public class CacheExample {

    @Cacheable(DummyCache.class)
    public Long getValue(String arg1, BigDecimal arg2) {
        return ThreadLocalRandom.current().nextLong();
    }

    @CachePut(DummyCache.class)
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

#### Invalidate

Для удаления значения по ключу из кэша через метод *evictValue()* следует проаннотировать его аннотацией *@CacheInvalidate*.

Метод проаннотированный *@CacheInvalidate* будет вызван и затем по ключу для кэша определенного в *value* будут удалены значения по ключу.

Ключ для кэша составляет из аргументов метода, порядок аргументов имеет значение, в данном случае он будет составляться из значений *arg1* и *arg2*.

```java
@Component
public class CacheExample {

    @Cacheable(DummyCache.class)
    public Long getValue(String arg1, BigDecimal arg2) {
        return ThreadLocalRandom.current().nextLong();
    }

    @CachePut(DummyCache.class)
    public Long putValue(String arg1, BigDecimal arg2) {
        return ThreadLocalRandom.current().nextLong();
    }

    @CacheInvalidate(DummyCache.class)
    public void evictValue(String arg1, BigDecimal arg2) {
        // do nothing
    }

    public void evictAll() {
        // do nothing
    }
}
```

##### InvalidateAll

Для удаления всех значений из кэша через метод *evictAll()* следует проаннотировать его аннотацией *@CacheInvalidate* и указать параметр *invalidateAll = true*.

Метод проаннотированный *@CacheInvalidate* будет вызван и затем будут удалены все из кэша определенного в *value*.

```java
@Component
public class CacheExample {

    @Cacheable(DummyCache.class)
    public Long getValue(String arg1, BigDecimal arg2) {
        return ThreadLocalRandom.current().nextLong();
    }

    @CachePut(DummyCache.class)
    public Long putValue(String arg1, BigDecimal arg2) {
        return ThreadLocalRandom.current().nextLong();
    }

    @CacheInvalidate(DummyCache.class)
    public void evictValue(String arg1, BigDecimal arg2) {
        // do nothing
    }

    @CacheInvalidate(DummyCache.class, invalidateAll = true)
    public void evictAll() {
        // do nothing
    }
}
```

### Reactor Mono

Все примеры были бы аналогичны и поддерживаются для Reactor Mono, пример такого класса:

```java
@Component
public class CacheableTargetMono {

    @Cacheable(DummyCache.class)
    public Mono<Long> getValue(String arg1, BigDecimal arg2) {
        return Mono.just(ThreadLocalRandom.current().nextLong());
    }

    @CachePut(DummyCache.class)
    public Mono<Long> putValue(String arg1, BigDecimal arg2) {
        return Mono.just(ThreadLocalRandom.current().nextLong());
    }

    @CacheInvalidate(DummyCache.class)
    public Mono<Void> evictValue(String arg1, BigDecimal arg2) {
        return Mono.empty();
    }

    @CacheInvalidate(DummyCache.class, invalidateAll = true)
    public Mono<Void> evictAll() {
        return Mono.empty();
    }
}
```

### Несколько кешей

В случае если у вас есть несколько кешей требуется использовать для базового и самого распространенного кеша модуль *Default* конфигурации, а 
кэш другой имплементации размечать в аннотации в поле *tags*, подставляя туда нужную имплементацию *CacheManager*.

Конфигурация будет выглядеть следующим образом:
```java
@KoraApp
public interface ApplicationModules extends RedisCacheModule, CaffeineCacheModule { }
```

Реализации:
```java
@Cache("my.caffeine.cache.config")
public interface CacheCaffeine extends CaffeineCache<String, String> { }

@Cache("my.redis.cache.config")
public interface CacheRedis extends RedisCache<String, String> { }
```

А сам класс с кешами так:
```java
@Component
public class CacheableExample {

    @Cacheable(CacheCaffeine.class)
    @Cacheable(CacheRedis.class)
    public Mono<Long> getValueCaffeine(String arg1) {
        return Mono.just(ThreadLocalRandom.current().nextLong());
    }
}
```

Порядок вызова кэшей соответствует порядку в котором объявлены аннотации.

### Порядок аргументов для ключа

В случае если метод принимает не нужные аргументы, которые не хочется использовать как часть ключа для кэша либо же порядок аргументов не соответствует порядку аргументов для ключа кэша, 
следует использовать атрибут аннотации *parameters* в котором определить какие именно аргументы использовать и в каком порядке.

В примере ниже ключ для кэша для обоих методов будет составлен идентично.

В случае если в каком либо месте у вас будет не соответствие ключа по сигнатуре аргументов метода, вы получите ошибку на этапе компиляции.

```java
@Component
public class CacheExample {

    @Cacheable(DummyCache.class)
    public Long getValue(String arg1, BigDecimal arg2) {
        return ThreadLocalRandom.current().nextLong();
    }
    
    @Cacheable(value = DummyCache.class, parameters = {"arg1", "arg2"})
    public Long getValue(BigDecimal arg2, String arg3, String arg1) {
        return ThreadLocalRandom.current().nextLong();
    }
}
```

## Конфигурации

В текущий момент реализованы две имплементации для кешей:

- [Caffeine](https://github.com/ben-manes/caffeine)
- [Redis](https://github.com/lettuce-io/lettuce-core)

### Caffeine

Описание конфигурации:

- `expireAfterWrite` - Время по истечении которого значение для ключа будет удалено, отчитывается после добавления значения. *(Optional)*
- `expireAfterAccess` - Время по истечении которого значение для ключа будет удалено, отчитывается после операции чтения. *(Optional)*
- `initialSize` - Начальный размер кэша (помогает избежать ресазинга в случае активного набухания кэша) *(Optional)*
- `maximumSize` - Максимальный размер кэша (При достижении границы **или чуть ранее** будет исключать из кэша наименее актуальные значения) *(Optional)*

Предоставляет модуль *CaffeineCacheModule* для использования Caffeine.

Пример конфигурации для *my.cache.config* кэша:
```hocon
my {
  cache {
    config { 
      expireAfterWrite = 10s
      expireAfterAccess = 10s
      initialSize = 10
      maximumSize = 10
    }
  }
}
```

#### Dependencies

**Java**:
```groovy
annotationProcessor "ru.tinkoff.kora:annotation-processors"
implementation "ru.tinkoff.kora:cache-caffeine"
```

**Kotlin**:
```groovy
ksp "ru.tinkoff.kora:annotation-processors"
implementation "ru.tinkoff.kora:cache-caffeine"
```

Module:
```java
@KoraApp
public interface ApplicationModules extends CaffeineCacheModule { }
```

### Redis

Описание конфигурации подключения к Redis (Lettuce клиент):

- uri - URI редиса
- user - Юзер *(Optional)*
- password - Пароль юзера *(Optional)*
- database - Номер базы *(Optional)*
- protocol - Протокол *(Optional)*
- socketTimeout - Время таймаута коннекта *(Optional)*
- commandTimeout - Время таймаута команды *(Optional)*

Описание конфигурации Redis Cache:

- expireAfterWrite - При записи устанавливает время [expiration](https://redis.io/commands/psetex/)
- expireAfterAccess - При чтении устанавливает время [expiration](https://redis.io/commands/getex/)

Предоставляет модули *RedisCacheModule* для использования Redis.

Пример конфигурации для *my.cache.config* кэша.

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

my {
  cache {
   config {
      expireAfterWrite = 10s
      expireAfterAccess = 10s
    }
  }
}
```

#### Dependencies

**Java**:
```groovy
annotationProcessor "ru.tinkoff.kora:annotation-processors"
implementation "ru.tinkoff.kora:cache-redis"
```

**Kotlin**:
```groovy
ksp "ru.tinkoff.kora:annotation-processors"
implementation "ru.tinkoff.kora:cache-redis"
```

Module:
```java
@KoraApp
public interface ApplicationModules extends RedisCacheModule { }
```

### Loadable Cache

Библиотека предоставляет компонент для построения сущности, которая объединяет операции GET и PUT, без использования AOP аннотаций - `LoadableCache`

#### Кеширование блокирующих операций

Иногда у нас есть долгая операция, которую бы мы хотели кешировать и запускать на отдельном потоке исполнения при использовании асинхронного апи.   
Для создания такого `CacheLoader` можно воспользоваться фабричным методом `CacheLoader.blocking`, он создаёт такой cache, который при вызове `CacheLoader.loadAsync` вызовет метод `load`, из примера ниже, на переданном `ExecutorService`.
При этом вызов `CacheLoader.load` вызовет 'load' на том же потоке 

```java
@Module
interface ApplicationModules {
  default LoadableCache<String, String> someEntityLoadCache(DummyCache cache, SomeService someService, ExecutorService executor) {
    return LoadableCache.create(cache, CacheLoader.blocking(someService::loadEntity, executor));
  }
}

@Component
class ServiceExample {
  private final LoadableCache<String, String> loadableCache;
  
  public OtherService(LoadableCache<SomeEntity> loadableCache) {
      this.loadableCache = loadableCache;
  }
}
```

#### Кеширование неблокирующих операций

По аналогии с методом `CacheLoader.blocking`, существуют фабричные методы `CacheLoader.nonBlocking` и `CacheLoader.async`, которые просто умеют кешировать результат переданной функции без `Executor`.

## Supported Types

Поддерживаемые типы возвращаемых методов для AOP:

Java:

- Обычный метод
- Project Reactor (Mono)

Kotlin:

- Обычный метод
- Suspend
