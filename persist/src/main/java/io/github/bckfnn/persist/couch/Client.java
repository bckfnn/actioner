/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.bckfnn.persist.couch;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public class Client {

    private HttpDriver httpDriver;

    public Client(HttpDriver httpDriver) {
        this.httpDriver = httpDriver;
    }

    /**
     * Connect the server using default host and port.
     * @param handler the next handler
     */
    public void init(Handler<AsyncResult<Void>> handler) {
        httpDriver.init(handler);
    }

    public Database getDatabase(String name) {
        return new Database(this, name);
    }

    public void close(Handler<AsyncResult<Void>> handler) {
        httpDriver.close(handler);
    }
    /*
    public Stream<Result.DatabaseList> databasesList() {
        return process(new Operation<>("GET", "/_api/database", null, Result.DatabaseList.class));
    }

    public Stream<Result.VersionResult> version(boolean details) {
        return process(new Operation<>("GET", "/_api/version?details=" + details, null, Result.VersionResult.class));
    }
     */
    public <T> void process(Operation<T> req, Handler<AsyncResult<T>> handler)  {
        httpDriver.process(req, handler);
    }
}
