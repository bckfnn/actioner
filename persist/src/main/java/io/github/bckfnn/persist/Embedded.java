/*
 * Copyright 2014 [inno:vasion]
 */
package io.github.bckfnn.persist;

import io.vertx.core.json.JsonObject;

/**
 * Interface for model classes that is embedded in another model.
 */
public interface Embedded extends Orm {
    JsonObject getDoc();
    void setDoc(JsonObject doc);
    EntityManager getEntityManager();
    void setEntityManager(EntityManager entityManager);
}
