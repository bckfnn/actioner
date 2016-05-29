/*
 * Copyright 2015 [inno:vasion]
 */
package io.github.bckfnn.persist;

import io.vertx.core.json.JsonObject;


public class EmbeddedImpl implements Embedded {
    protected JsonObject doc = new JsonObject();
    transient EntityManager entityManager;

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + doc + "]";
    }

    @Override
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
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
}
