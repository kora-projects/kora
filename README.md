<p align="center">
  <a href="https://kora-projects.github.io/kora-docs">
    <img src="https://kora-projects.github.io/kora-docs/v2/en/assets/img/kora-long.png" alt="Kora Framework" width="420">
  </a>
</p>

<h1 align="center">Kora Framework</h1>

<p align="center">
  <b>Simple and easy compile-time JVM framework for Java &amp; Kotlin.</b><br>
  Explicit code, strong types, precise compiler feedback, and no runtime magic.
</p>

<p align="center">
  <a href="https://central.sonatype.com/artifact/io.koraframework/common"><img src="https://img.shields.io/maven-central/v/io.koraframework/common.svg?label=maven%20central" alt="Maven Central"></a>
  <a href="https://github.com/kora-projects/kora/actions?query=workflow%3A%22Build+Master%22"><img src="https://github.com/kora-projects/kora/workflows/Build%20Master/badge.svg" alt="Build"></a>
  <a href="https://github.com/kora-projects/kora/blob/master/LICENSE"><img src="https://img.shields.io/github/license/kora-projects/kora.svg" alt="License"></a>
</p>

<p align="center">
  <a href="https://kora-projects.github.io/kora-docs">Documentation</a> ·
  <a href="https://kora-projects.github.io/kora-docs/guides">Getting Started</a> ·
  <a href="https://github.com/kora-projects/kora-examples">Examples</a> ·
  <a href="https://github.com/kora-projects/kora-skills">Kora Skills</a>
</p>

> 🇷🇺 Русская версия: [README.ru.md](README.ru.md)

---

Kora is a full-stack, cloud-oriented server framework for Java and Kotlin. You write familiar, high-level declarative code — controllers, repositories, listeners, config — and Kora's annotation
processor turns it into ordinary, readable Java/Kotlin **at compile time**: the dependency graph, HTTP routing, repository implementations, and aspect wrappers are all generated source you can open
and read.

The result is a stack that is fast for the machine and transparent for the human — and, for the same reasons, unusually easy for AI coding agents to reason about.

```text
Your code + annotations
        │  compile
        ▼
Annotation Processor / KSP  →  generated graph, routes, repositories, aspects
        │  run
        ▼
A running service — no runtime reflection, no dynamic proxies, no classpath scanning
```

## Why Kora

- **Simplicity** — one recommended, well-supported way to solve each problem instead of five competing styles. Familiar Java/Kotlin idioms, thin abstractions, and generated code you can step
  through — so onboarding is fast and context lives in the code, not in framework folklore.
- **Transparency** — Kora generates human-readable source with explicit graphs and free aspects. What you read is what runs — no black box. Compile-time checks turn missing or ambiguous wiring, and
  even wrong SQL placeholders, into **readable compiler errors instead of 3 a.m. stack traces**.
- **Efficiency** — the dependency container is built at compile time and initialized as parallel as possible, so services start in **seconds, not tens of seconds** and reach peak throughput without
  a long, expensive JIT warm-up. Faster readiness makes horizontal scaling cheaper and rolling deploys smoother.
- **Performance** — high-performant code generated at compile time. No runtime Reflection API, no dynamic proxies, thin fine-grained abstractions and free aspects, and only the most efficient module
  implementations. Top-tier [TechEmpower](https://www.techempower.com/benchmarks/) results out of the box, with nothing to tune.

## Benchmarks

**Throughput** — external TechEmpower measurement (single query, higher is better):

[![Kora TechEmpower](https://raw.githubusercontent.com/kora-projects/.github/refs/heads/master/storage/images/techempower_squiry_2024_01_24.jpeg "Kora TechEmpower external measurement")](https://www.techempower.com/benchmarks/#section=test&resultsurl=https%3A%2F%2Fstatic.squiry.xyz%2Fresults%2F20240124114707.json&hw=ph&test=fortune)

**Startup & readiness** — 10× PetClinic (ten times the controllers, repositories and services of the classic Spring PetClinic, with full production metrics, tracing, logs and probes) in a container on
1 CPU / 1 GB, lower is better:

| Framework                  | Time to serve traffic |
|----------------------------|-----------------------|
| **Kora**                   | **~4.9 s**            |
| Spring (heavily optimized) | ~21.1 s               |
| Spring (stock)             | ~26.5 s               |

![Kora Startup](https://raw.githubusercontent.com/kora-projects/.github/refs/heads/master/storage/images/run_in_container_joker_2024.jpeg "Kora startup benchmark of a PetClinic application")

**Build time** — clean artifact build of the same 10× PetClinic, average of 5 runs on a MacBook Pro 2019 (i7-9750H):

| Build                                             | Time    |
|---------------------------------------------------|---------|
| Kora — `./gradlew clean distTar`                  | ~11.6 s |
| Spring — `bootJar`, stock                         | ~9.3 s  |
| Spring — `bootJar`, optimized (layered jar + AOT) | ~18.7 s |

Honest note: against **stock** Spring, build times are comparable (Kora is even slightly slower). But Spring's fast startup requires a layered jar + AOT that roughly **doubles** its build time — Kora
builds **~1.6× faster than optimized Spring** and gets the fast startup for free.

## Hello, Kora

Declare the application graph and a controller — plain Java that reads like plain Java:

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

`ApplicationGraph` is generated at compile time — run `./gradlew classes` and open it under `build/generated/…` to see exactly how components are created and wired. The same is true for Kotlin, using
KSP instead of annotation processors.

> **Install** — add the Kora BOM, the HTTP server module (and any others you need), and the annotation processor for Java or KSP for Kotlin.
> The [Getting Started guide](https://kora-projects.github.io/kora-docs/guides) has copy-paste Gradle and Maven setup for both languages.

## The mental model: declare → compile → run

1. **Declare** — controllers, repositories, listeners, config, and resilience policies as constructors, interfaces, and annotations (`@KoraApp`, `@Component`, `@Module`, `@Tag`).
2. **Compile** — the processor validates the whole graph and generates implementations. A missing or ambiguous dependency, a dependency cycle, or a wrong `@Query` placeholder **fails the build**, not
   production.
3. **Run** — what executes is the generated source: no reflection, no dynamic proxies, no classpath scanning. Everything is debuggable and traceable, with thin, honest stack traces.

Because wiring is explicit on `@KoraApp` and nothing is auto-scanned or silently pulled in, the dependency graph is a single structure you (or a tool) can trace end to end.

## Batteries included

Kora ships one carefully chosen, high-performance implementation per problem — enable only the modules your service needs:

- **HTTP** — [server](https://kora-projects.github.io/kora-docs/v2/en/documentation/http-server/) & declarative [client](https://kora-projects.github.io/kora-docs/v2/en/documentation/http-client/), request
  mapping, interceptors, management endpoints, and strongly
  typed [OpenAPI codegen](https://kora-projects.github.io/kora-docs/v2/en/documentation/openapi-codegen/); [SOAP](https://kora-projects.github.io/kora-docs/v2/en/documentation/soap-client/) client.
- **Data** — SQL-first [repositories](https://kora-projects.github.io/kora-docs/v2/en/documentation/database-common/) with compile-time-checked `@Query`,
  over [JDBC](https://kora-projects.github.io/kora-docs/v2/en/documentation/database-jdbc/), [R2DBC](https://kora-projects.github.io/kora-docs/v2/en/documentation/database-r2dbc/), [Vert.x](https://kora-projects.github.io/kora-docs/v2/en/documentation/database-vertx/),
  and [Cassandra](https://kora-projects.github.io/kora-docs/v2/en/documentation/database-cassandra/); column macros, generated mappers, batches,
  and [migrations](https://kora-projects.github.io/kora-docs/v2/en/documentation/database-migration/).
- **Messaging & RPC** — [Kafka](https://kora-projects.github.io/kora-docs/v2/en/documentation/kafka/) consumers/producers, [gRPC](https://kora-projects.github.io/kora-docs/v2/en/documentation/grpc-server/)
  server & client, and an [S3](https://kora-projects.github.io/kora-docs/v2/en/documentation/s3-client/) client.
- **Aspects** — [resilience](https://kora-projects.github.io/kora-docs/v2/en/documentation/resilient/) (`@Retry`, `@Timeout`, `@CircuitBreaker`,
  `@Fallback`), [caching](https://kora-projects.github.io/kora-docs/v2/en/documentation/cache/) (Caffeine /
  Redis), [validation](https://kora-projects.github.io/kora-docs/v2/en/documentation/validation/), [scheduling](https://kora-projects.github.io/kora-docs/v2/en/documentation/scheduling/) — generated at
  compile time, no runtime proxies.
- **Core** — compile-time [dependency injection](https://kora-projects.github.io/kora-docs/v2/en/documentation/container/),
  typed [configuration](https://kora-projects.github.io/kora-docs/v2/en/documentation/config/) (HOCON / YAML), [JSON](https://kora-projects.github.io/kora-docs/v2/en/documentation/json/) without reflection,
  virtual-thread-friendly concurrency.
- **Observability** — metrics, tracing, structured logging, and [probes](https://kora-projects.github.io/kora-docs/v2/en/documentation/probes/) for every module, following the `OpenTelemetry` standard,
  plus graceful shutdown — designed in, not bolted on.

## Testing

`@KoraAppTest` spins up the **real application graph**, exactly as in production — and automatically trims it to the `@TestComponent`s you declare and their dependencies, so you test precisely what
you wire, nothing more.

```java

@KoraAppTest(Application.class)
class PetServiceTests {

    @Mock
    @TestComponent
    private PetRepository petRepository;   // replace a node with a stub

    @TestComponent
    private PetService petService;         // inject any node from the graph

    @Test
    void findByID() {
        Mockito.when(petRepository.findById(1)).thenReturn(Optional.of(pet));
        assertTrue(petService.findByID(1).isPresent());
    }
}
```

Fast [component tests](https://kora-projects.github.io/kora-docs/v2/en/guides/testing-junit/), [integration tests](https://kora-projects.github.io/kora-docs/v2/en/guides/testing-integration/) with
Testcontainers, and [black-box tests](https://kora-projects.github.io/kora-docs/v2/en/guides/testing-black-box/) against the assembled service all use the same explicit graph.

## Built for simplicity and clarity

The qualities that make Kora approachable for a newcomer are the same ones that make it tractable for an AI coding agent:

- **No runtime magic to reverse-engineer** — generated source is what runs; less reflection and fewer hidden rules mean less context to hold in your head or for a model to guess.
- **The compiler as a second reviewer** — a fast, strict `change → compile → precise error → fix` loop, plus cheap component/integration tests thanks to fast context startup.
- **Strong typing end to end** — from internal contracts to the external API via the OpenAPI generator; a wrong assumption becomes a compile error, not a runtime bug.
- **One clear way** — a small set of orthogonal abstractions shrinks the space of choices, so a newcomer avoids dead ends and a model avoids mixing incompatible styles.

There is also [**kora-skills**](https://github.com/kora-projects/kora-skills) — an official skill that lets your AI agent teach and build Kora straight from the official guides and examples.

## Documentation & community

- Documentation — [English](https://kora-projects.github.io/kora-docs)
- Guides — [English](https://kora-projects.github.io/kora-docs/guides)
- Runnable examples — [kora-examples](https://github.com/kora-projects/kora-examples)
- AI skill — [kora-skills](https://github.com/kora-projects/kora-skills)

## Contributing

Issues and pull requests are welcome. Building the project requires JDK 17+ and uses the Gradle wrapper:

```bash
./gradlew build
```

Please open an issue to discuss changes before submitting a pull request.
