# Description

Модуль предоставляет `Extension` для `JUnit5` для тестирования приложений.

# Dependency

```groovy
testImplementation "ru.tinkoff.kora:test-junit5"
```

Удостовериться что включена платформа `JUnit` в Gradle:
```groovy
test {
    useJUnitPlatform()
}
```

## Given

Примеры будут показаны относительно такой конфигурации классов:

Предположим есть класс `@Component`:

```java
@Component
final class Component1 implements Supplier<String> {

    public String get() {
        return "1";
    }
}
```

Также класс `@Component` который является `Root` и используется `@Component` объявленный выше:
```java
@Root
@Component
final class Component12 implements Lifecycle, Supplier<String> {

    private final Component1 component1;

    public Component12(Component1 component1) { this.component1 = component1; }

    public String get() { return component1.get() + "2"; }

    @Override
    public Mono<?> init() { return Mono.empty(); }

    @Override
    public Mono<?> release() { return Mono.empty(); }
}
```

Также `@KoraApp` класс:

```java
@KoraApp
public interface ApplicationModules {

    @Tag(Supplier.class)
    Supplier<String> taggedSupplier() {
        return () -> "tag1";
    }
}
```

## KoraAppTest

Предполагается использовать аннотацию `@KoraAppTest` для аннотирования тестового класса.

Параметры аннотации `@KoraAppTest`:

- `application` - обязательный параметр который указывает на класс аннотированный `@KoraApp`, представляющий собой граф всех зависимостей которые будут доступны в рамках теста.
- `components` - список компонентов которые надо инициализировать в рамках теста, можно указать список компонентов и только они и будут инициализированы в рамках графа, 
в случае отсутствия указанных компонентов, будет инициализирован весь граф.
- `initializeMode` - когда инициализировать контекст графа, каждый раз для каждого тестового метода (стандартное поведение) либо один раз на тестовый класс.

Пример:
```java
@KoraAppTest(
    value = ApplicationModules.class,
    components = {Component1.class, Component12.class})
class ComponentJUnitExtensionTests {
    
}
```

## TestComponent

Для использования компонентов в рамках теста предлагается использовать аннотацию `@TestComponent` 
которая позволяет внедрять зависимости компонентов в аргументы и/или поля тестового класса.

Пример теста, где компоненты внедряются в поля:
```java
@KoraAppTest(
    value = ApplicationModules.class,
    components = {Component1.class, Component12.class})
class ComponentJUnitExtensionTests {

    @TestComponent
    private Component1 component1;
    
    @Test
    void example() {
        assertEquals("1", component1.get());
    }
}
```

Пример теста, где компоненты внедряются в аргументы метода:
```java
@KoraAppTest(
    value = ApplicationModules.class,
    components = {Component1.class, Component12.class})
class ComponentJUnitExtensionTests {

    @Test
    void example(@TestComponent Component1 component1) {
        assertEquals("1", component1.get());
    }
}
```

### Tags

Для внедрения зависимости которая имеет `@Tag`, требуется указать соответствующую аннотацию `@Tag` рядом с внедряемым аргументом:
```java
@KoraAppTest(value = ApplicationModules.class)
class ComponentJUnitExtensionTests {

    @Test
    void example(@Tag(Supplier.class) @TestComponent Supplier<String> supplier) {
        assertEquals("tag1", supplier.get());
    }
}
```

## MockComponent

Для `Mock`'а компонентов в рамках теста предлагается использовать аннотацию `@MockComponent`
которая позволяет сделать `Mock` проаннотированного компонента и внедрять `Mock` зависимость в аргументы и/или поля тестового класса.

Пример теста, где `Mock` внедряется в поля:
```java
@KoraAppTest(
    value = ApplicationModules.class,
    components = {Component1.class, Component12.class})
class ComponentJUnitExtensionTests {

    @MockComponent
    private Component1 component1;

    @BeforeEach
    void mock() {
        Mockito.when(component1.get()).thenReturn("?");
    }

    @Test
    void example() {
        assertEquals("?", component1.get());
    }
}
```

Пример теста, где `Mock` внедряется в аргументы метода:
```java
@KoraAppTest(
    value = ApplicationModules.class,
    components = {Component1.class, Component12.class})
class ComponentJUnitExtensionTests {

    @Test
    void example(@MockComponent Component1 component1) {
        Mockito.when(component1.get()).thenReturn("?");
        assertEquals("?", component1.get());
    }
}
```

### Tags

Для внедрения зависимости которая имеет `@Tag`, требуется указать соответствующую аннотацию `@Tag` рядом с внедряемым аргументом:
```java
@KoraAppTest(value = ApplicationModules.class)
class ComponentJUnitExtensionTests {

    @Test
    void example(@Tag(Supplier.class) @MockComponent Supplier<String> supplier) {
        Mockito.when(supplier.get()).thenReturn("?");
        assertEquals("?", supplier.get());
    }
}
```

## Config Modifier

Для изменений/добавления конфига в рамках тестов предполагается чтобы тестовый класс реализовал интерфейс `KoraAppTestConfigModifier`, 
где требуется реализовать метод предоставления модификации конфига.

Пример добавления конфига `application.conf` в виде строки выглядеть так:
```java
@KoraAppTest(value = ApplicationModules.class)
class ComponentJUnitExtensionTests implements KoraAppTestConfigModifier {

    @Override
    public @Nonnull KoraConfigModification config() {
        return KoraConfigModification.ofString("""
                                    myconfig {
                                      myproperty = 1
                                    }
                                """);
    }
}
```

## Graph Modifier

Для добавления/подмены/мока компонентов в рамках графа можно реализовать интерфейс `KoraAppTestGraphModifier`,
где требуется реализовать метод предоставления модификации графа приложения.

### Add

Пример добавления компонента в граф:
```java
@KoraAppTest(value = ApplicationModules.class)
class ComponentJUnitExtensionTests implements KoraAppTestGraphModifier {

    @Override
    public @Nonnull KoraGraphModification graph() {
        return KoraGraphModification.create()
            .addComponent(TypeRef.of(Supplier.class, Integer.class), () -> (Supplier<Integer>) () -> 1);
    }

    @Test
    void example(@TestComponent Supplier<Integer> supplier) {
        assertEquals(1, supplier.get());
    }
}
```

### Replace

Пример замены компонента в графе:
```java
@KoraAppTest(value = ApplicationModules.class)
class ComponentJUnitExtensionTests implements KoraAppTestGraphModifier {

    @Override
    public @Nonnull KoraGraphModification graph() {
        return KoraGraphModification.create()
            .addComponent(TypeRef.of(Supplier.class, String.class), List.of(Supplier.class), () -> (Supplier<String>) () -> "?");
    }

    @Test
    void example(@Tag(Supplier.class) @TestComponent Supplier<String> supplier) {
        assertEquals(1, supplier.get());
    }
}
```

### Mock

Пример замены компонента в графе:
```java
@KoraAppTest(value = ApplicationModules.class)
class ComponentJUnitExtensionTests implements KoraAppTestGraphModifier {

    @Override
    public @Nonnull KoraGraphModification graph() {
        return KoraGraphModification.create()
            .mockComponent(Component1.class);
    }

    @Test
    void example(@TestComponent Component1 component1) {
        Mockito.when(component1.get()).thenReturn("?");
        assertEquals("?", component1.get());
    }
}
```

## Examples

### TestContainers & Postgres

Пример написания теста с использованием `@TestContainers` и базы данных `Postgres` с использованием миграции через Flyway.

Предположим что мы имеем класс ApplicationModules:
```java
@KoraApp
public interface JdbcApplicationModules extends
        ConfigModule,
        LogbackModule,
        JdbcDatabaseModule {}
```

Предположим что мы имеем класс Repository:
```java
@Repository
public interface EntityJdbcRepository extends JdbcRepository {

    record Entity(String id, String field1) {}

    @Query("""
        INSERT INTO entities(id, value1)
        VALUES (:entity.id, :entity.field1)
        """)
    void insert(Entity entity);

    @Query("DELETE FROM entities")
    int deleteAll();
}
```

Предположим что миграции лежат в стандартном пакете для Flyway: `/resources/db/migration`

Пример тестового класса:
```java
@Testcontainers
@KoraAppTest(value = ApplicationModules.class)
class ComponentJUnitExtensionTests implements KoraAppTestConfigModifier {

    @Container
    private static final PostgreSQLContainer container = new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.7-alpine"))
        .withDatabaseName("postgres")
        .withUsername("postgres")
        .withPassword("postgres")
        .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(PostgreSQLContainer.class)))
        .waitingFor(Wait.forListeningPort());

    @Override
    public @Nonnull KoraConfigModification config() {
        return KoraConfigModification.ofString("""
                                    db {
                                      jdbcUrl = "%s"
                                      username = "%s"
                                      password = "%s"
                                      maxPoolSize = 10
                                      poolName = "example"
                                    }
                                    """.formatted(container.getJdbcUrl(), "postgres", "postgres"));
    }

    @BeforeAll
    static void migrate() {
        Flyway.configure()
            .dataSource(container.getJdbcUrl(), "postgres", "postgres")
            .load()
            .migrate();
    }

    @BeforeEach
    void cleanup(@TestComponent EntityJdbcRepository repository) {
        repository.deleteAll();
    }

    @Test
    void example(@TestComponent EntityJdbcRepository repository) {
        repository.insert(new EntityJdbcRepository.EntityPart("1", "2"));
    }
}
```
