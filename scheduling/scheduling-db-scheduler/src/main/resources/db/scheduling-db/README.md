# Kora scheduling-db schema resources

`db-scheduler` does not create its table automatically.
Applications should apply the schema with their regular migration tool before the scheduler starts.

The scripts create the `kora_scheduling_db_jobs` table, which is the default value of `scheduling.dbScheduler.tableName`.
If `tableName` is changed, these scripts do not match it: create the table with your own migration
or enable `scheduling.dbScheduler.initializeTable`, which applies the same schema with the configured name.

Flyway locations:

- `classpath:db/scheduling-db/flyway/postgresql`
- `classpath:db/scheduling-db/flyway/mysql`
- `classpath:db/scheduling-db/flyway/mariadb`
- `classpath:db/scheduling-db/flyway/mssql`
- `classpath:db/scheduling-db/flyway/oracle`
- `classpath:db/scheduling-db/flyway/hsql`

Liquibase changelog:

- `classpath:db/scheduling-db/liquibase/changelog.yaml`

The Liquibase changelog contains database-specific changesets guarded by `dbms`.
