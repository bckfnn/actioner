/*
 * Copyright 2014 [inno:vasion]
 */
package io.github.bckfnn.persist;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;

/**
 * Persistor interface.
 */
public interface Persistor {
    public <T extends Embedded> T create(Class<T> cls);

    public <T extends Entity> void load(final Class<T> cls, String id, Handler<AsyncResult<T>> handler);

    public <T extends Entity> void load(Class<T> class1, Orm.Query<T> query, Handler<AsyncResult<T>> handler);

    public <T extends Entity> void query(Class<T> class1, Orm.Query<T> query, Handler<AsyncResult<ReadStream<T>>> handler);



    public void readAttachment(String id, Handler<AsyncResult<ReadStream<Buffer>>> handler);
    public <T extends Entity> void saveAttachment(T entity, ReadStream<Buffer> stream, Handler<AsyncResult<T>> handler);

    public <T extends Entity> void update(T entity, Handler<AsyncResult<T>> handler);

    public <T extends Entity> void delete(T entity, Handler<AsyncResult<T>> handler);

    public <T extends Entity> void store(T entity, Handler<AsyncResult<T>> handler);

    public void dropDatabase(Handler<AsyncResult<Boolean>> handler);
    public void createDatabase(Handler<AsyncResult<Boolean>> handler);
    public void createDesign(Handler<AsyncResult<Boolean>> handler);
}
