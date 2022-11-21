package ru.tinkoff.kora.database.jdbc;


import ru.tinkoff.kora.database.common.annotation.Query;

import javax.annotation.Nullable;

/*
{table_name}
{id_field}
{id_column}
{id_field_*}
{id_column_*}
{columns}
{non_id_columns}

{non_id_placeholders}
{update_columns}
 */
public interface PostgresJdbcCrudRepository<ENTITY, ID> {
    @Nullable
    @Query("SELECT {columns} FROM {table_name} WHERE {id_column} = :id")
    ENTITY findOne(ID id);

    @Nullable
    @Query("DELETE FROM {table_name} WHERE {id_column} = :id returning {columns}")
    ENTITY delete(ID id);

    @Query("INSERT INTO {entity.table_name}({entity.non_id_columns}) VALUES ({entity.non_id_placeholders}) RETURNING {columns}")
    ENTITY insert(ENTITY entity);

    @Query("UPDATE {entity.table_name} SET {entity.update_columns} WHERE {entity.id_column} = :entity.{entity.id_field} returning {columns}")
    ENTITY update(ENTITY entity);
}
