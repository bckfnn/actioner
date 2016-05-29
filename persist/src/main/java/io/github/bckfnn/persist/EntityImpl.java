/*
 * Copyright 2014 [inno:vasion]
 */
package io.github.bckfnn.persist;

import java.util.Date;
import java.util.UUID;

import io.github.bckfnn.callback.Flow;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * Implementation base class for entity model classes.
 */
public abstract class EntityImpl implements Entity {
    protected JsonObject doc = new JsonObject();
    transient EntityManager entityManager;

    @Override
    public String getId() {
        return doc.getString("_id");
    }

    @Override
    public void setId(String id) {
        doc.put("_id", id);
    }

    @Override
    public String getRev() {
        return doc.getString("_rev", null);
    }

    @Override
    public void setRev(String rev) {
        doc.put("_rev", rev);
    }

    @Override
    public Date getCreationDate() {
        return Schema.convertToDate(doc, "$creationDate");
    }

    @Override
    public void updateCreationDate() {
        doc.put("$creationDate", Schema.dateFormat.format(new Date()));
    }

    @Override
    public Date getModificationDate() {
        return Schema.convertToDate(doc, "$modificationDate");
    }

    @Override
    public void updateModificationDate() {
        doc.put("$modificationDate", Schema.dateFormat.format(new Date()));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + doc + "]";
    }

    @Override
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
        setId(UUID.randomUUID().toString());
    }

    @Override
    public EntityManager getEntityManager() {
        return entityManager;
    }

    @Override
    public JsonObject getDoc() {
        return doc;
    }

    @Override
    public void setDoc(JsonObject doc) {
        this.doc = doc;
    }

    @Override
    public <T extends Entity> void save(Handler<AsyncResult<Void>> handler) {
        entityManager.store(this, Flow.with(handler, x -> null));
    }
}
