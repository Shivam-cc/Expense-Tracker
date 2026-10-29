package com.expensetracker.config;

import com.expensetracker.model.IdentifiedDocument;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

@Component
@Order(200)
@RequiredArgsConstructor
public class MongoDocumentIds implements BeforeConvertCallback<IdentifiedDocument> {
    private final MongoClient client;
    private final MongoDatabaseFactory factory;

    public long nextId(String collection) {
        // Allocate outside the caller's transaction: unrelated inserts must not contend on a counter document.
        Document counter = client.getDatabase(factory.getMongoDatabase().getName()).getCollection("_sequences")
                .findOneAndUpdate(new Document("_id", collection),
                        new Document("$inc", new Document("value", 1L)),
                        new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
        if (counter == null) throw new IllegalStateException("MongoDB did not allocate an ID for " + collection);
        return counter.getLong("value");
    }

    @Override
    public IdentifiedDocument onBeforeConvert(IdentifiedDocument entity, String collection) {
        if (entity.getId() == null) entity.setId(nextId(collection));
        return entity;
    }
}
