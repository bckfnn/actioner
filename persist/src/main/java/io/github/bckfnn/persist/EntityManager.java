/*
 * Copyright 2015 [inno:vasion]
 */
package io.github.bckfnn.persist;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;

public interface EntityManager {
    public <T extends Embedded> T create(Class<T> cls);
    public <T extends Entity> void store(T entity, Handler<AsyncResult<T>> handler);
    public <T extends Entity> void saveAttachment(T entity, ReadStream<Buffer> stream, Handler<AsyncResult<T>> handler);
    public void readAttachment(String entity, Handler<AsyncResult<ReadStream<Buffer>>> handler);
}
