/*
 * Copyright 2015 [inno:vasion]
 */
package io.github.bckfnn.persist.couch;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.bckfnn.callback.Flow;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.Pump;
import io.vertx.core.streams.ReadStream;

public class Database {
    @SuppressWarnings("unused")
    private final static Logger log = LoggerFactory.getLogger(Database.class);

    private Client client;
    private String databaseName;

    public Database(Client client, String databaseName) {
        this.client = client;
        this.databaseName = databaseName;
    }

    public String getName() {
        return databaseName;
    }

    public String dbPath() {
        return "/" + databaseName;
    }


    public Operation<JsonObject> documentCreate(boolean batch, String id, Object doc) {
        Url url = new Url(dbPath() + "/" + Flow.val(() -> URLEncoder.encode(id, "UTF-8")));
        //url.parm("collection", collectionName);
        //url.parm("createCollection", createCollection, false);
        if (batch) {
            url.parm("batch", "ok");
        }
        return put(url, doc, JsonObject.class);
    }

    public Operation<JsonObject> documentUpdate(boolean batch, String id, Object doc) {
        return documentCreate(batch, id, doc);
    }

    public Operation<JsonObject> documentDelete(String id, String rev) {
        Url url = new Url(dbPath() + "/" + Flow.val(() -> URLEncoder.encode(id, "UTF-8")));
        //url.parm("collection", collectionName);
        //url.parm("createCollection", createCollection, false);
        url.parm("rev", rev);
        return del(url, JsonObject.class);
    }

    public Operation<JsonObject> attachmentCreate(boolean batch, String id, String rev, ReadStream<Buffer> rs) {
        Url url = new Url(dbPath() + "/" + Flow.val(() -> URLEncoder.encode(id, "UTF-8")) + "/body");
        //url.parm("collection", collectionName);
        //url.parm("createCollection", createCollection, false);
        if (batch) {
            url.parm("batch", "ok");
        }
        url.parm("rev", rev);
        return new Operation<JsonObject>("PUT", url.toString(), null, JsonObject.class) {
            @Override
            public void sendRequest(HttpClientRequest r, Vertx vertx) throws Exception {
                r.putHeader("Content-Type", "application/octet");
                r.setChunked(true);

                rs.endHandler($ -> {
                    r.end();
                });
                Pump.pump(rs, r).start();
            }
        };
    }

    public Operation<ReadStream<Buffer>> attachmentLoad(String id, String file) {
        Url url = new Url(dbPath() + "/" + Flow.val(() -> URLEncoder.encode(id, "UTF-8")) + "/body");
        //url.parm("collection", collectionName);
        //url.parm("createCollection", createCollection, false);

        return new Operation<ReadStream<Buffer>>("GET", url.toString(), null, null) {
            @Override
            public void handleResponse(HttpClientResponse resp, Handler<AsyncResult<ReadStream<Buffer>>> handler)  {
                handler.handle(Future.succeededFuture(resp));
            }
        };
    }



    public Operation<JsonObject> documentLoad(String id) {
        Objects.requireNonNull(id);
        Url url = new Url(dbPath() + "/" + Flow.val(() -> URLEncoder.encode(id, "UTF-8")));
        //url.parm("collection", collectionName);
        //url.parm("createCollection", createCollection, false);
        return get(url, JsonObject.class);
    }

    public Operation<JsonObject> databaseDrop() {
        Url url = new Url(dbPath());
        return del(url, JsonObject.class);
    }

    public Operation<JsonObject> databaseCreate() {
        Url url = new Url(dbPath());
        return put(url, null, JsonObject.class);
    }

    public Operation<JsonObject> view(String design, String view, JsonObject keys) {
        Url url = new Url(dbPath(), design, "/_view/" + view);

        for (String name : keys.fieldNames()) {
            url.parm(name, keys.getValue(name).toString());
        }
        return get(url, JsonObject.class);
    }

    public Operation<JsonObject> docs(JsonArray keys, JsonObject params) {
        Url url = new Url(dbPath(), "/_all_docs/");
        for (String name : params.fieldNames()) {
            url.parm(name, params.getValue(name).toString());
        }
        url.parm("keys", keys.toString());
        //JsonObject body = new JsonObject().put("keys", keys);

        return get(url, JsonObject.class);
    }

    static class Url {
        StringBuilder sb = new StringBuilder();
        boolean seenParms = false;

        public Url(String prefix) {
            sb.append(prefix);
        }

        public Url(String prefix, String postfix) {
            sb.append(prefix);
            sb.append(postfix);
        }

        public Url(String prefix, String postfix, String handle) {
            sb.append(prefix);
            sb.append(postfix);
            sb.append(handle);
        }

        public void parm(String name, String value) {
            sb.append(seenParms ? '&' : '?');
            sb.append(name);
            sb.append('=');
            try {
                sb.append(URLEncoder.encode(value, "UTF8"));
            } catch (UnsupportedEncodingException e) {
                throw Flow.rethrow(e);
            }
            seenParms = true;
        }

        public void parm(String name, boolean value) {
            sb.append(seenParms ? '&' : '?');
            sb.append(name);
            sb.append(value ? "=true" : "=false");
            seenParms = true;
        }

        public void parm(String name, boolean value, boolean dflt) {
            if (value == dflt) {
                return;
            }
            sb.append(seenParms ? '&' : '?');
            sb.append(name);
            sb.append(value ? "=true" : "=false");
            seenParms = true;
        }

        public void parm(String name, long value, long dflt) {
            if (value == dflt) {
                return;
            }
            sb.append(seenParms ? '&' : '?');
            sb.append(name);
            sb.append(Long.toString(value));
            seenParms = true;
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }

    public void close(Handler<AsyncResult<Void>> handler) {
        client.close(handler);
    }

    public <T> void process(Operation<T> op, Handler<AsyncResult<T>> handler) {
        client.process(op, handler);
    }

    private <T> Operation<T> get(Url url, Class<T> resClass) {
        return new Operation<>("GET", url.toString(), null, resClass);
    }

    private <T> Operation<T> put(Url url, Object body, Class<T> resClass) {
        return new Operation<>("PUT", url.toString(), body, resClass);
    }

    private <T> Operation<T> del(Url url, Class<T> resClass) {
        return new Operation<>("DELETE", url.toString(), null, resClass);
    }

    @SuppressWarnings("unused")
    private <T> Operation<T> post(Url url, Object body, Class<T> resClass) {
        return new Operation<>("POST", url.toString(), body, resClass);
    }

    @SuppressWarnings("unused")
    private <T> Operation<T> patch(Url url, Object body, Class<T> resClass) {
        return new Operation<>("PATCH", url.toString(), body, resClass);
    }
}
