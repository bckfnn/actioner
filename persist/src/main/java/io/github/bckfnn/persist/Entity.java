/*
 * Copyright 2014 [inno:vasion]
 */
package io.github.bckfnn.persist;

import java.util.Date;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 * Interface for model classes that is stored as seperate entities.
 */
public interface Entity extends Embedded {
    String getId();

    void setId(String id);

    String getRev();

    void setRev(String rev);

    Date getCreationDate();

    void updateCreationDate();

    Date getModificationDate();

    void updateModificationDate();

    String getCollectionName();

    public <T extends Entity> void save(Handler<AsyncResult<Void>> handler);
}
