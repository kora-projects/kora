# Базы данных

## Repository

Главным инструментом для работы с базами данных с помощью kora является аннотация `@Repository` - 
она позволяет создавать с помощью генерации кода имплементации репозиториев. Рассмотрим пример:

```java
@Repository
public interface EntityRepository extends JdbcRepository {
    record Entity(Long id, @Column("value1") String field1, String value2) {}

    @Query("SELECT id, value1, value2 FROM test_table WHERE id = :id")
    Entity getOneById(Long id);

}
```
* `@Repository` - указывает на то, что интерфейс является репозиторием
* `@Query` - показывает, что нужно сгенерировать имплементацию для метода, выполняющую SQL запрос указанный внутри.
* `@Column` - указывает на имя колонки, соответствующее полю в `Entity`

## Entity

Сущности, используемые в качестве возвращаемого значения, должны содержать один публичный
конструктор. Это может быть как конструктор по умолчанию, так и конструктор с параметрами. 
Если Kora найдет конструктор с параметрами, то на его основе будет создаваться объект сущности. 
В случае же с пустым конструктором поля будут заполняться через сеттеры.

В случае, когда требуется создать несколько конструкторов можно использовать
аннотацию `@EntityConstructor`

## Batch
Kora поддерживает batch-запросы с помощью аннотации `@Batch`. Её использование выглядит следующим образом:
```java
@Repository
interface RepoWithBatch extends JdbcRepository {
    @Query("INSERT INTO test(value1, value2) VALUES (:entity.value1, :entity.value2)")
    void insertBatch(@Batch List<Entity> entity);
}
```

## Реализации:
### JDBC

При подключении через `jdbc` следует добавить `JdbcDatabaseModule`. Внутри `JdbcDatabaseModule` создаются экземпляры классов `JdbcDataBaseConfig` и `JdbcDataBase`.

Параметры, описанные в классе `JdbcDataBaseConfig`:

* **username** - имя пользователя
* **password** - пароль
* **jdbcUrl** - url базы данных
* **poolName** - название пула соединений
* **connectionTimeout** - таймаут на ожидание подключения в милисекундах. Дефолтное значение 30000 милисекунд
* **validationTimeout** - таймаут на ожидание пулом подтверждения, что соединение активно, в милисекундах. Дефолтное значение 5000 милисекунд
* **idleTimeout** - максимальное время, в течение короторого подключение может оставаться в пуле и быть незанятым, в милисекундах. Дефолтное значение 600000 милисекунд
* **leakDetectionThreshold** - время, в течение которого подключение может быть вне пула, прежде чем будет залогированно сообщение о возможной утечке соединения, в милисекундах. Дефолтное значение 0
* **maxLifetime** - максимальное время жизни соединения в пуле в милисекундах. Дефолтное значение 1800000 милисекунд
* **maxPoolSize** - размер пула соединений. Дефолтное значение 10
* **minIdle** - минимальное количество свободных соединений, поддерживаемых в пуле. Дефолтное значение 0
* **dsProperties** - стандартные dataSourceProperties

Пример конфигурации:
```hocon
db {
  jdbcUrl = "jdbc:postgresql://localhost:5432/db"
  username = "username"
  password = "password"
  maxPoolSize = 1
  poolName = "postgresql"
}
```

Для выполнения блокирующих запросов в БД Kora предоставляет интерфейс `JdbcConnectionFactory`, api которого используется при генерации имплементаций репозиториев.
Для того чтобы выполнять запросы транзакционно, можно сделать что-то подобное:

```java

@Repository
public interface SomeRepository extends JdbcRepository {
    @Query("select num from test where id=:id")
    Integer selectNum(Long id);

    @Query("select value from test_2 where id=:id")
    String selectValue(Long id);
}

public final class SomeService {
    private final SomeRepository repo;
    private final JdbcConnectionFactory connectionFactory;

    public SomeService(WithExecutorAccessorRepository repo, JdbcConnectionFactory connectionFactory) {
        this.repo = repo;
        this.connectionFactory = connectionFactory;
    }

    SomeEntity getAndCombine(Long id) {
        return connectionFactory.inTx(connection -> {
           var num = repo.selectNum(id);
           var value = repo.selectValue(id);
           return new SomeEntity(num, value);
        });
    }
}
```

Для обычных случаев, когда используются аннотации `@Repository` и `@Query`, `preparedStatementSetter` и `resultExtractor` будут сгенерированы на этапе генерации кода.

Если для запроса нужна какая-то более сложная логика, и `@Query` будет недостаточно, можно поступить следующим образом:
```java
@Repository
interface ComplexRepository extends JdbcRepository {
    default SomeCoplexEntity getSome(Long id) {
        return this.getJdbcConnectionFactory().inTx(connection -> {
            //some complex logic
        });
    }
}
```
### Vert.x

При подключении через `Vert.x` следует добавить `VertxDatabaseModule`. Внутри `VertxDatabaseModule` создаются экземпляры классов `VertxDatabaseConfig` и `VertxDatabase`.

Параметры, описанные в классе `VertxDatabaseConfig`:

* **username** - имя пользователя
* **password** - пароль
* **host** - host для подключения
* **database** - дефолтный database для подключения
* **poolName** - название пула соединений
* **hostRequirement** - допустимые значения: PRIMARY, SECONDARY. Данный параметр настраивается при наличии нескольких хостов
* **connectionTimeout** - таймаут на подключение в милисекундах. Дефолтное значение 30000 милисекунд
* **idleTimeout** - максимальное время, в течение короторого подключение может оставаться в пуле и быть незанятым, в милисекундах. Дефолтное значение 600000 милисекунд
* **acquireTimeout** - таймаут на ожидание подключения в пуле в милисекундах. Дефолтное значение 30000 милисекунд
* **maxPoolSize** - максимальный размер пула соединений
* **minPoolSize** - минимальный размер пула соединений
* **aliveBypassWindow** - определяет время между последним успешным и новым походом в базу данных, после которого при последующей выдаче соединения из пула сперва выполнится запрос "SELECT 1"

Пример конфигурации:
```hocon
db {
  username = "username"
  password = "password"
  host = "localhost:5432"
  database = "db"
  poolName = "vertx"
}
```

`VertxConnectionFactory` и `VertxRepository` используются следующим способом:

```java
@Repository
public interface WithExecutorAccessorRepository extends VertxRepository {
    default Mono<Integer> selectTwo() {
        return this.getVertxConnectionFactory().inTx(connection -> {
            return getVertxConnectionFactory().query(connection, new QueryContext("SELECT 2", "SELECT 2"), Tuple::tuple, rs -> {
                for (var row : rs) {
                    return row.getInteger(1);
                }
                return null;
            });
        });
    }
}
```

## Mapping

Если по какой-то причине нужно переопределить маппер для запросов или ответа от БД, это можно сделать следующим образом:

```java
@Repository
interface RepoWithMapping {
    record MappedEntity(String value1, @Mapping(JdbcParameterColumnMapper.class) String value2){}
    
    @Query("SELECT test")
    @Mapping(MappedEntityRowMapper.class)
    MappedEntity returnEntityRowMapper();

    @Query("INSERT INTO test(value1, value2) VALUES (?, ?)")
    void mappedBatch(@Mapping(ParameterMapper.class) @Batch List<MappedEntity> batch);
}
```

`MappedEntityRowMapper` должен реализовывать `JdbcRowMapper<MappedEntity>`, а `ParameterMapper` - `JdbcParameterStatementMapper<MappedEntity>`

Примеры:

```java
class MappedEntityRowMapper implements JdbcRowMapper<MappedEntity> {
        @Override
        public MappedEntity apply(ResultSet rs) throws SQLException {
            return new MappedEntity(rs.getString(1));
        }
    }
```

```java
class ParameterMapper implements JdbcParameterColumnMapper<MappedEntity> {

        @Override
        public void set(PreparedStatement stmt, int index, String value) throws SQLException {
            stmt.setString(index, value);
        }
    }

```

В примерах выше были рассмотрены мапперы для jdbc. Для Vert.x соответственно доступны следующие интерфейсы мапперов: `VertxParameterFieldMapper`, `VertxParameterMapper`, `VertxResultColumnMapper`, `VertxRowMapper`, `VertxRowSetMapper`

## Naming strategy

По умолчанию имена полей DTO переводятся в snake_case при извлечении результата. Если нужно сохранить их как есть, можно использовать аннотацию `@NamingStrategy`:
```java
@Repository
public interface EntityRepository {
    @NamingStrategy(NoopNameConverter.class)
    record Entity(Long id, String firstValue, String secondValue) {}

    @Query("SELECT id, firstValue, secondValue FROM test_table WHERE id = :id")
    Entity getOneById(Long id);
}
```
