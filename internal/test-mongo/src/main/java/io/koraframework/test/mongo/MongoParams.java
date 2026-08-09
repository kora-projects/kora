package io.koraframework.test.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

/**
 * @param connectionString cluster connection string without a database
 * @param database         database the test works with
 */
public record MongoParams(String connectionString, String database) {

    public String uri() {
        return this.connectionString + "/" + this.database;
    }

    public MongoClient client() {
        return MongoClients.create(this.uri());
    }

    public MongoParams withDatabase(String database) {
        return new MongoParams(this.connectionString, database);
    }
}
