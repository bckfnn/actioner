/*
 * Copyright 2015 [inno:vasion]
 */

package io.github.bckfnn.persist.couch;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public interface HttpDriver {
    public void init(Handler<AsyncResult<Void>> handler);
    public <T> void process(Operation<T> req, Handler<AsyncResult<T>> handler);
    public void close(Handler<AsyncResult<Void>> handler);
}
