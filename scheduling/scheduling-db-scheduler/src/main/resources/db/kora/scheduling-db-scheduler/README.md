# Kora scheduling-db schema resources

`db-scheduler` does not create its table automatically.
Applications should create it with their regular migration tool before the scheduler starts,
or enable `scheduling.dbScheduler.initializeTable`.

Every object created by the scripts is prefixed with the table name, so it does not clash with application objects:

| Object      | Name                                                            |
|-------------|-----------------------------------------------------------------|
| Table       | `kora_scheduling_db_scheduler_jobs`                             |
| Primary key | `kora_scheduling_db_scheduler_jobs_pk`                          |
| Index       | `kora_scheduling_db_scheduler_jobs_execution_time_idx`          |
| Index       | `kora_scheduling_db_scheduler_jobs_last_heartbeat_idx`          |
| Index       | `kora_scheduling_db_scheduler_jobs_priority_execution_time_idx` |

`kora_scheduling_db_scheduler_jobs` is the default value of `scheduling.dbScheduler.tableName`.
If `tableName` is changed, replace the name in the copied script or enable `initializeTable`,
which applies the same schema with the configured name used as the prefix of every object.

## Flyway

The module does not ship versioned Flyway migrations: Flyway keeps one version sequence per history table,
so a bundled `V1__...` would clash with the application's own migrations.
Copy the script for your database into the application migrations under the next free version,
for example `db/migration/V42__create_kora_scheduling_db_scheduler_jobs.sql`:

- `db/kora/scheduling-db-scheduler/schema/postgresql.sql`
- `db/kora/scheduling-db-scheduler/schema/mysql.sql`
- `db/kora/scheduling-db-scheduler/schema/mariadb.sql`
- `db/kora/scheduling-db-scheduler/schema/mssql.sql`
- `db/kora/scheduling-db-scheduler/schema/oracle.sql`
- `db/kora/scheduling-db-scheduler/schema/hsql.sql`

## Liquibase

Include the changelog from the application master changelog:

```yaml
databaseChangeLog:
  - include:
      file: db/kora/scheduling-db-scheduler/liquibase/changelog.yaml
```

The changelog contains database-specific changesets guarded by `dbms`, with the stable logical file path
`kora/scheduling-db-scheduler/changelog.yaml` and ids prefixed with `kora-scheduling-db-scheduler-jobs-`.
Each changeset is marked as ran without changes when `kora_scheduling_db_scheduler_jobs` already exists.
