<p align="center">
  <a href="https://kora-projects.github.io/kora-docs">
    <img src="https://kora-projects.github.io/kora-docs/v2/ru/assets/img/kora-long.png" alt="Kora Framework" width="420">
  </a>
</p>

<h1 align="center">Kora Framework</h1>

<p align="center">
  <b>Простой и понятный JVM-фреймворк для Java и Kotlin с генерацией кода во время компиляции.</b><br>
  Явный код, строгие типы, понятная обратная связь во время компиляции и отсутствие магии во время исполнения.
</p>

<p align="center">
  <a href="https://central.sonatype.com/artifact/io.koraframework/common"><img src="https://img.shields.io/maven-central/v/io.koraframework/common.svg?label=maven%20central" alt="Maven Central"></a>
  <a href="https://github.com/kora-projects/kora/actions?query=workflow%3A%22Build+Master%22"><img src="https://github.com/kora-projects/kora/workflows/Build%20Master/badge.svg" alt="Build"></a>
  <a href="https://github.com/kora-projects/kora/blob/master/LICENSE"><img src="https://img.shields.io/github/license/kora-projects/kora.svg" alt="License"></a>
</p>

<p align="center">
  <a href="https://kora-projects.github.io/kora-docs">Документация</a> ·
  <a href="https://kora-projects.github.io/kora-docs/guides">Ознакомление</a> ·
  <a href="https://github.com/kora-projects/kora-examples">Примеры</a> ·
  <a href="https://github.com/kora-projects/kora-skills">Kora Skills</a>
</p>

> en English version: [README.md](README.md)

---

Kora — полнофункциональный облачно-ориентированный серверный фреймворк для Java и Kotlin. Вы пишете привычный высокоуровневый декларативный код — контроллеры, репозитории, слушатели, конфигурацию — а
обработчик аннотаций Kora превращает его в обычный читаемый код на Java/Kotlin **во время компиляции**: граф зависимостей, маршрутизация HTTP, реализации репозиториев и обёртки аспектов становятся
сгенерированным исходным кодом, который можно открыть и прочитать.

Получается стек, быстрый для машины и прозрачный для человека — и по тем же причинам необычайно удобный для рассуждения нейро-агентам.

```text
Ваш код + аннотации
        │  компиляция
        ▼
Обработчик аннотаций / KSP  →  граф, маршруты, репозитории, аспекты
        │  запуск
        ▼
Работающий сервис — без рефлексии, без динамических прокси, без сканирования пути классов
```

## Почему Kora

- **Простота** — один рекомендуемый, хорошо поддерживаемый способ решить задачу вместо пяти конкурирующих стилей. Знакомые идиомы Java/Kotlin, тонкие абстракции и сгенерированный код, по которому
  можно пройтись отладчиком — поэтому погружение быстрое, а контекст живёт в коде, а не в фольклоре фреймворка.
- **Прозрачность** — Kora генерирует читаемый исходный код с явными графами и бесплатными аспектами. Что вы читаете — то и выполняется, без чёрного ящика. Проверки во время компиляции превращают
  отсутствующее или неоднозначное связывание и даже неверные плейсхолдеры в SQL в **понятные ошибки компилятора, а не в трассировки стека в два часа ночи**.
- **Производительность** — высокопроизводительный код, сгенерированный во время компиляции. Никакого Reflection API во время выполнения, никаких динамических прокси, тонкие точечные абстракции и
  бесплатные аспекты, только самые эффективные реализации модулей. Результаты уровня топа [TechEmpower](https://www.techempower.com/benchmarks/) из коробки, без ручной настройки.
- **Эффективность** — контейнер зависимостей строится во время компиляции и инициализируется максимально параллельно, поэтому сервисы стартуют за **секунды, а не за десятки секунд** и выходят на
  пиковую производительность без долгого и дорогого прогрева JIT. Быстрая готовность удешевляет горизонтальное масштабирование и сглаживает rolling-деплои.

## Замеры

**Пропускная способность** — внешний замер TechEmpower (одиночный запрос, больше значит лучше):

[![Kora TechEmpower](https://raw.githubusercontent.com/kora-projects/.github/refs/heads/master/storage/images/techempower_squiry_2024_01_24.jpeg "Внешний замер Kora на TechEmpower")](https://www.techempower.com/benchmarks/#section=test&resultsurl=https%3A%2F%2Fstatic.squiry.xyz%2Fresults%2F20240124114707.json&hw=ph&test=fortune)

**Старт и готовность** — 10× PetClinic (в 10 раз больше контроллеров, репозиториев и сервисов, чем в классической PetClinic на Spring, и всё с боевыми метриками, трассировкой, логами и пробами) в
контейнере на 1 ядре / 1 ГБ, меньше значит лучше:

| Фреймворк                        | Время до обслуживания трафика |
|----------------------------------|-------------------------------|
| **Kora**                         | **~4,9 с**                    |
| Spring (предельно оптимизирован) | ~21,1 с                       |
| Spring (без настройки)           | ~26,5 с                       |

![Kora Startup](https://raw.githubusercontent.com/kora-projects/.github/refs/heads/master/storage/images/run_in_container_joker_2024.jpeg "Замер старта приложения PetClinic на Kora")

**Время сборки** — чистая сборка артефакта той же 10× PetClinic, среднее по 5 прогонам на MacBook Pro 2019 (i7-9750H):

| Сборка                                                   | Время   |
|----------------------------------------------------------|---------|
| Kora — `./gradlew clean distTar`                         | ~11,6 с |
| Spring — `bootJar`, без оптимизаций                      | ~9,3 с  |
| Spring — `bootJar`, оптимизированный (layered jar + AOT) | ~18,7 с |

Честно: против **стокового** Spring время сборки сопоставимо (Kora даже чуть медленнее). Но быстрый старт Spring требует layered jar + AOT, что примерно **удваивает** время его сборки — Kora
собирается **в ~1,6 раза быстрее оптимизированного Spring** и получает быстрый старт бесплатно.

## Hello, Kora

Опишите граф приложения и контроллер — обычный Java, который читается как обычный Java:

```java

@KoraApp
public interface Application extends
    HoconConfigModule,
    JsonModule,
    LogbackModule,
    UndertowHttpServerModule {

    static void main(String[] args) {
        KoraApplication.run(ApplicationGraph::graph);
    }
}

@Component
@HttpController
public final class HelloController {

    @HttpRoute(method = HttpMethod.GET, path = "/hello")
    HttpServerResponse hello() {
        return HttpServerResponse.of(200, HttpBody.plaintext("Hello, Kora!"));
    }
}
```

`ApplicationGraph` генерируется во время компиляции — выполните `./gradlew classes` и откройте его в `build/generated/…`, чтобы увидеть, как именно создаются и связываются компоненты. Для Kotlin всё
то же самое, но вместо обработчиков аннотаций используется KSP.

> **Подключение** — добавьте BOM Kora, модуль HTTP-сервера (и любые другие, что вам нужны) и обработчик аннотаций для Java или KSP для Kotlin.
> В [руководстве Ознакомление](https://kora-projects.github.io/kora-docs/guides) есть готовая настройка для Gradle и Maven для обоих языков.

## Ментальная модель: опиши → скомпилируй → запусти

1. **Опишите** — контроллеры, репозитории, слушатели, конфигурацию и политики отказоустойчивости через конструкторы, интерфейсы и аннотации (`@KoraApp`, `@Component`, `@Module`, `@Tag`).
2. **Скомпилируйте** — обработчик проверяет весь граф и генерирует реализации. Отсутствующая или неоднозначная зависимость, цикл зависимостей или неверный плейсхолдер в `@Query` **валят сборку**, а не
   эксплуатацию.
3. **Запустите** — выполняется сгенерированный исходный код: без рефлексии, без динамических прокси, без сканирования пути классов. Всё отлаживается и трассируется, с тонкими честными трассировками
   стека.

Поскольку связывание объявлено явно на `@KoraApp` и ничего не сканируется автоматически и не подключается незаметно, граф зависимостей — это единая структура, которую вы (или инструмент) можете
проследить от начала до конца.

## Всё необходимое из коробки

Kora предоставляет по одной тщательно выбранной высокопроизводительной реализации на задачу — включайте только те модули, что нужны сервису:

- **HTTP** — [сервер](https://kora-projects.github.io/kora-docs/v2/ru/documentation/http-server/) и декларативный [клиент](https://kora-projects.github.io/kora-docs/v2/ru/documentation/http-client/),
  отображение запросов, перехватчики, служебные эндпоинты и строго
  типизированная [кодогенерация из OpenAPI](https://kora-projects.github.io/kora-docs/v2/ru/documentation/openapi-codegen/); [SOAP](https://kora-projects.github.io/kora-docs/v2/ru/documentation/soap-client/)
  -клиент.
- **Данные** — SQL-first [репозитории](https://kora-projects.github.io/kora-docs/v2/ru/documentation/database-common/) с проверкой `@Query` во время компиляции,
  поверх [JDBC](https://kora-projects.github.io/kora-docs/v2/ru/documentation/database-jdbc/), [R2DBC](https://kora-projects.github.io/kora-docs/v2/ru/documentation/database-r2dbc/), [Vert.x](https://kora-projects.github.io/kora-docs/v2/ru/documentation/database-vertx/)
  и [Cassandra](https://kora-projects.github.io/kora-docs/v2/ru/documentation/database-cassandra/); макросы колонок, сгенерированные преобразователи, батч-запросы
  и [миграции](https://kora-projects.github.io/kora-docs/v2/ru/documentation/database-migration/).
- **Сообщения и RPC** — потребители/издатели [Kafka](https://kora-projects.github.io/kora-docs/v2/ru/documentation/kafka/), сервер и
  клиент [gRPC](https://kora-projects.github.io/kora-docs/v2/ru/documentation/grpc-server/) и клиент [S3](https://kora-projects.github.io/kora-docs/v2/ru/documentation/s3-client/).
- **Аспекты** — [отказоустойчивость](https://kora-projects.github.io/kora-docs/v2/ru/documentation/resilient/) (`@Retry`, `@Timeout`, `@CircuitBreaker`,
  `@Fallback`), [кэширование](https://kora-projects.github.io/kora-docs/v2/ru/documentation/cache/) (Caffeine /
  Redis), [валидация](https://kora-projects.github.io/kora-docs/v2/ru/documentation/validation/), [планирование](https://kora-projects.github.io/kora-docs/v2/ru/documentation/scheduling/) — генерируются во
  время компиляции, без прокси во время выполнения.
- **Ядро** — [внедрение зависимостей](https://kora-projects.github.io/kora-docs/v2/ru/documentation/container/) во время компиляции,
  типизированная [конфигурация](https://kora-projects.github.io/kora-docs/v2/ru/documentation/config/) (HOCON / YAML), [JSON](https://kora-projects.github.io/kora-docs/v2/ru/documentation/json/) без
  рефлексии, конкурентность на виртуальных потоках.
- **Наблюдаемость** — метрики, трассировка, структурированное логирование и [пробы](https://kora-projects.github.io/kora-docs/v2/ru/documentation/probes/) для каждого модуля по стандарту `OpenTelemetry`,
  а также корректное завершение — заложены в основу, а не добавлены поздно.

## Тестирование

`@KoraAppTest` поднимает **настоящий граф приложения**, ровно как в эксплуатации — и автоматически обрезает его до объявленных `@TestComponent` и их зависимостей, поэтому вы тестируете именно то, что
связали, и ничего лишнего.

```java

@KoraAppTest(Application.class)
class PetServiceTests {

    @Mock
    @TestComponent
    private PetRepository petRepository;   // подменяем узел заглушкой

    @TestComponent
    private PetService petService;         // внедряем любой узел графа

    @Test
    void findByID() {
        Mockito.when(petRepository.findById(1)).thenReturn(Optional.of(pet));
        assertTrue(petService.findByID(1).isPresent());
    }
}
```

Быстрые [компонентные тесты](https://kora-projects.github.io/kora-docs/v2/ru/guides/testing-junit/), [интеграционные тесты](https://kora-projects.github.io/kora-docs/v2/ru/guides/testing-integration/) на
Testcontainers и [тесты чёрного ящика](https://kora-projects.github.io/kora-docs/v2/ru/guides/testing-black-box/) против собранного сервиса — все используют один и тот же явный граф.

## Создан для простоты и понимания

Качества, которые делают Kora понятной новичку, — это те же качества, что делают её посильной для нейро-агента:

- **Нет магии во время выполнения, которую нужно разгадывать** — выполняется сгенерированный исходный код; меньше рефлексии и скрытых правил — меньше контекста, который нужно держать в голове или
  угадывать модели.
- **Компилятор как второй ревьюер** — быстрый и строгий цикл `изменил → скомпилировал → получил точную ошибку → исправил`, плюс дешёвые компонентные и интеграционные тесты благодаря быстрому старту
  контекста.
- **Сквозная типизация** — от внутренних контрактов до внешнего API через генератор OpenAPI; ошибочное предположение становится ошибкой компиляции, а не ошибкой во время выполнения.
- **Один понятный путь** — небольшой набор ортогональных абстракций сужает пространство выбора, поэтому новичок не заходит в тупики, а модель не смешивает несовместимые стили.

Есть также [**kora-skills**](https://github.com/kora-projects/kora-skills) — официальный навык, с которым ваш нейро-агент учит и пишет Kora прямо по официальным руководствам и примерам.

## Документация и сообщество

- Документация — [Русский](https://kora-projects.github.io/kora-docs) · [English](https://kora-projects.github.io/kora-docs)
- Руководства — [Русский](https://kora-projects.github.io/kora-docs/guides) · [English](https://kora-projects.github.io/kora-docs/guides)
- Готовые примеры — [kora-examples](https://github.com/kora-projects/kora-examples)
- Нейро-навык — [kora-skills](https://github.com/kora-projects/kora-skills)

## Участие в разработке

Issues и pull request'ы приветствуются. Для сборки проекта нужен JDK 17+ и используется Gradle wrapper:

```bash
./gradlew build
```

Пожалуйста, заведите issue для обсуждения изменений, прежде чем присылать pull request.
