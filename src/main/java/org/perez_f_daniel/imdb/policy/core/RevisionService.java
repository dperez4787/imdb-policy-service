package org.perez_f_daniel.imdb.policy.core;

import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * Monotonic bundle revision, one counter document in bundle_meta. Every
 * mutation that can change the compiled bundle bumps it; the bundle endpoint
 * uses it as the ETag so the router's poller gets cheap 304s.
 */
@Service
public class RevisionService {

    private static final String COLLECTION = "bundle_meta";
    private static final String COUNTER_ID = "revision";

    private final MongoTemplate mongo;

    public RevisionService(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** Atomically increments and returns the new revision. */
    public long bump() {
        Document doc = mongo.getCollection(COLLECTION).findOneAndUpdate(
                new Document("_id", COUNTER_ID),
                new Document("$inc", new Document("value", 1L)),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
        return doc.getLong("value");
    }

    /** Current revision; 0 before anything has ever changed. */
    public long current() {
        Document doc = mongo.getCollection(COLLECTION)
                .find(new Document("_id", COUNTER_ID)).first();
        return doc == null ? 0L : doc.getLong("value");
    }
}
