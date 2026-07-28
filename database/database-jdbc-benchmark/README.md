# JDBC Pool Benchmarks

This module contains JMH benchmarks for comparing Kora JDBC modules backed by HikariCP and Agroal.

The `jdbcPoolBenchmark` Gradle task starts one PostgreSQL Testcontainer before the JMH run, passes its JDBC connection parameters to all benchmark forks, and stops the container after the full run.

## Run

Full run:

```powershell
.\gradlew.bat :database:database-jdbc-benchmark:jdbcPoolBenchmark --console plain
```

Short focused run:

```powershell
.\gradlew.bat :database:database-jdbc-benchmark:jdbcPoolBenchmark '-PjmhInclude=JdbcPoolBenchmark.selectOne' '-PjmhMode=thrpt,sample' -PjmhWarmupIterations=1 -PjmhIterations=2 -PjmhForks=1 -PjmhThreads=1 -PjmhMeasurementSeconds=5 --console plain
```

Useful properties:

- `-PjmhInclude=JdbcPoolBenchmark.selectOne`
- `-PjmhMode=thrpt,sample`
- `-PjmhWarmupIterations=2`
- `-PjmhIterations=3`
- `-PjmhWarmupSeconds=5`
- `-PjmhMeasurementSeconds=10`
- `-PjmhForks=1`
- `-PjmhThreads=1`

Results are written to:

```text
database/database-jdbc-benchmark/build/results/jmh/jdbc-pool-benchmark.json
```

## Benchmarks

- `acquireRelease`: `getConnection()` plus `close()`.
- `selectOne`: `SELECT 1` with a regular `Statement`.
- `preparedSelectById`: single-row lookup with `PreparedStatement`.
- `transactionRollback`: insert in transaction followed by rollback.
- `holdConnection5ms`: acquire connection and hold it for 5 ms.

## Latest Short Run

Environment:

- JDK: `25.0.2`
- PostgreSQL: Testcontainers, `postgres:17.6-alpine`
- Pool size: `10`
- Threads: `1`
- Forks: `1`
- Warmup: `1 x 5s`
- Measurement: `2 x 5s`

### `selectOne`

| Pool | avg | p50 | p95 | p99 | allocation |
|---|---:|---:|---:|---:|---:|
| Hikari | `164.369 us/op` | `159.488 us/op` | `196.864 us/op` | `254.464 us/op` | `1357.967 B/op` |
| Agroal | `164.182 us/op` | `157.184 us/op` | `198.400 us/op` | `243.410 us/op` | `1309.359 B/op` |

### `preparedSelectById`

| Pool | avg | p50 | p95 | p99 | allocation |
|---|---:|---:|---:|---:|---:|
| Hikari | `187.077 us/op` | `182.016 us/op` | `222.208 us/op` | `281.170 us/op` | `949.646 B/op` |
| Agroal | `185.378 us/op` | `181.248 us/op` | `217.600 us/op` | `275.456 us/op` | `989.007 B/op` |

## Latest 50-Thread Short Run

Environment:

- JDK: `25.0.2`
- PostgreSQL: Testcontainers, `postgres:17.6-alpine`
- Pool size: `10`
- Threads: `50`
- Forks: `1`
- Warmup: `1 x 5s`
- Measurement: `2 x 5s`

Commands:

```powershell
.\gradlew.bat :database:database-jdbc-benchmark:jdbcPoolBenchmark '-PjmhInclude=JdbcPoolBenchmark.selectOne' '-PjmhMode=thrpt,sample' -PjmhWarmupIterations=1 -PjmhIterations=2 -PjmhForks=1 -PjmhThreads=50 -PjmhMeasurementSeconds=5 --console plain
.\gradlew.bat :database:database-jdbc-benchmark:jdbcPoolBenchmark '-PjmhInclude=JdbcPoolBenchmark.preparedSelectById' '-PjmhMode=thrpt,sample' -PjmhWarmupIterations=1 -PjmhIterations=2 -PjmhForks=1 -PjmhThreads=50 -PjmhMeasurementSeconds=5 --console plain
```

### `selectOne`

| Pool | throughput | avg | p50 | p95 | p99 | allocation |
|---|---:|---:|---:|---:|---:|---:|
| Hikari | `0.031 ops/us` | `1588.026 us/op` | `310.784 us/op` | `418.816 us/op` | `37289.984 us/op` | `1582.096 B/op` |
| Agroal | `0.031 ops/us` | `1611.659 us/op` | `1601.536 us/op` | `1792.000 us/op` | `1906.688 us/op` | `1360.403 B/op` |

### `preparedSelectById`

| Pool | throughput | avg | p50 | p95 | p99 | allocation |
|---|---:|---:|---:|---:|---:|---:|
| Hikari | `0.029 ops/us` | `1731.958 us/op` | `340.992 us/op` | `446.464 us/op` | `38731.776 us/op` | `1270.370 B/op` |
| Agroal | `0.029 ops/us` | `1744.587 us/op` | `1736.704 us/op` | `1906.688 us/op` | `1998.848 us/op` | `1044.612 B/op` |

With 50 benchmark threads and pool size `10`, throughput is effectively equal in this local run. Hikari has much lower median latency, but shows large sampled tail spikes. Agroal is slower at the median, but has a much tighter p99 and lower allocation.

## Notes

Agroal's default `trackJdbcResources` adds wrappers for `Statement`, `PreparedStatement`, and `ResultSet`. The Kora Agroal module disables this by default for a lower-overhead fast path. Re-enable it only when JDBC resource tracking is required.

These numbers are local Testcontainer results and should be treated as smoke benchmarks. For more realistic pool comparison, also run with contention:

```powershell
.\gradlew.bat :database:database-jdbc-benchmark:jdbcPoolBenchmark -PjmhThreads=10 --console plain
.\gradlew.bat :database:database-jdbc-benchmark:jdbcPoolBenchmark -PjmhThreads=50 --console plain
```
