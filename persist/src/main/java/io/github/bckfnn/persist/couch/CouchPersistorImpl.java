/*
 * Copyright 2015 [inno:vasion]
 */
package io.github.bckfnn.persist.couch;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.bckfnn.callback.Flow;
import io.github.bckfnn.persist.Embedded;
import io.github.bckfnn.persist.Entity;
import io.github.bckfnn.persist.EntityManager;
import io.github.bckfnn.persist.Orm;
import io.github.bckfnn.persist.Persistor;
import io.github.bckfnn.persist.Schema;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;

public class CouchPersistorImpl implements Persistor, EntityManager {
    private final Vertx vertx;
    private final Schema schema;
    private final Client client;
    private final Database database;
    //private List<Operation<? extends Result>> queue = new ArrayList<>();
    private final String host;
    private final int port;
    private final String auth;

    private final static Logger log = LoggerFactory.getLogger(CouchPersistorImpl.class);

    private HttpDriver driver = new HttpDriver() {
        private HttpClient httpClient;

        @Override
        public void init(Handler<AsyncResult<Void>> handler) {
            this.httpClient = vertx.createHttpClient();
            handler.handle(Future.succeededFuture());
        }

        @Override
        public <T> void process(Operation<T> req, Handler<AsyncResult<T>> handler) {
            String uri = req.getUri();

            log.debug("{} {}", req.getMethod(), uri);

            HttpMethod method = method(req.getMethod());

            HttpClientRequest r = httpClient.request(method, port, host, uri, resp -> {
                req.handleResponse(resp, handler);
            });
            try {
                if (auth != null) {
                    r.putHeader("Authorization", "Basic " + auth);
                }
                req.sendRequest(r, vertx);
            } catch (Exception e) {
                handler.handle(Future.failedFuture(e));
            }
        }

        @Override
        public void close(Handler<AsyncResult<Void>> handler) {
            httpClient.close();
            handler.handle(Future.succeededFuture());
        }

        private HttpMethod method(String method) {
            return HttpMethod.valueOf(method);
        }
    };

    public CouchPersistorImpl(Vertx vertx, Schema schema, String databaseName, Handler<AsyncResult<Void>> handler) {
        this(vertx, schema, "localhost", 5984, null, null, databaseName, handler);
    }

    public CouchPersistorImpl(Vertx vertx, Schema schema, String host, int port, String username, String password, String databaseName, Handler<AsyncResult<Void>> handler) {
        this.vertx = vertx;
        this.schema = schema;
        this.host = host;
        this.port = port;
        if (username != null && username.length() > 0) {
            this.auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
        } else {
            this.auth = null;
        }

        client = new Client(driver);
        client.init(handler);
        database = client.getDatabase(databaseName);
    }

    public Database getDatabase() {
        return database;
    }

    @Override
    public <T extends Embedded> T create(Class<T> cls) {
        return create(cls, true);
    }



    private <T extends Embedded> T create(Class<T> cls, boolean init) {
        T entity;
        try {
            entity = schema.newInstance(cls);
            entity.setEntityManager(this);
            if (init && entity instanceof Entity) {
                ((Entity) entity).updateCreationDate();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return entity;
    }

    public <T extends Entity> T makeEntity(Class<T> cls, JsonObject doc) {
        T ent = create(cls);
        if (doc != null) {
            ent.setDoc(doc);
        }
        //ent.setId(doc.get("_id").asText());
        //ent.setRev((String) doc.get("_rev"));
        return ent;
    }

    @Override
    public <T extends Entity> void query(Class<T> cls, Orm.Query<T> query, Handler<AsyncResult<ReadStream<T>>> handler) {
        query.apply(this, Flow.with(handler, r -> {
            handler.handle(Future.succeededFuture((ReadStream<T>) r));
        }));
    }

    @Override
    public <T extends Entity> void load(Class<T> cls, Orm.Query<T> query, Handler<AsyncResult<T>> handler) {
        query.apply(this, Flow.with(handler, r -> {
            log.trace("return from query");
            ReadStream<T> stream = (ReadStream<T>) r;
            AtomicInteger cnt = new AtomicInteger(0);
            AtomicReference<T> element = new AtomicReference<>();
            AtomicBoolean fired = new AtomicBoolean(false);

            stream.endHandler($ -> {
                log.info("loadquery endhandler {}", cnt.get());
                if (!fired.getAndSet(true)) {
                    handler.handle(Future.succeededFuture(element.get()));
                }
            });
            stream.exceptionHandler(e -> {
                log.info("loadquery exchandler {}", e);
                if (!fired.getAndSet(true)) {
                    handler.handle(Future.failedFuture(e));
                }
            });

            stream.handler(elm -> {
                log.info("loadquery datahandler {} {}", cnt.get(), elm);
                if (cnt.incrementAndGet() == 1) {
                    element.set(elm);
                } else {
                    if (!fired.getAndSet(true)) {
                        handler.handle(Future.failedFuture("Only one element expected"));
                    }
                }
            });
        }));
    }


    @Override
    public <T extends Entity> void load(Class<T> cls, String id, Handler<AsyncResult<T>> handler) {
        database.process(database.documentLoad(id), Flow.with(handler, r ->  {
            return makeEntity(cls, r);
        }));
    }


    @Override
    public void readAttachment(String id, Handler<AsyncResult<ReadStream<Buffer>>> handler) {
        database.process(database.attachmentLoad(id, "body"), handler);
    }

    @Override
    public <T extends Entity> void update(T entity, Handler<AsyncResult<T>> handler) {
        entity.updateModificationDate();
        database.process(database.documentUpdate(false, entity.getId(), entity.getDoc()), Flow.with(handler, r -> {
            if (r.getBoolean("ok", false)) {
                entity.setRev(r.getString("rev"));
            }
            return entity;
        }));
    }

    @Override
    public <T extends Entity> void delete(T entity, Handler<AsyncResult<T>> handler) {
        database.process(database.documentDelete(entity.getId(), entity.getRev()), Flow.with(handler, r -> {
            return entity;
        }));
    }


    @Override
    public void dropDatabase(Handler<AsyncResult<Boolean>> handler) {
        database.process(database.databaseDrop(), Flow.with(handler, r -> {
            return r.getBoolean("ok", false);
        }));
    }

    @Override
    public void createDatabase(Handler<AsyncResult<Boolean>> handler) {
        database.process(database.databaseCreate(), Flow.with(handler, r -> {
            System.out.println(r);
            if (r.getBoolean("ok", false)) {
                createDesign(handler);
            }
        }));
    }

    @Override
    public void createDesign(Handler<AsyncResult<Boolean>> handler) {

        Stream<Schema.SchemaModel<?>> x = schema.models().stream()
                .filter(model -> Entity.class.isAssignableFrom(model.getEntityClass()));
        Flow.forEach(x, (model, h) -> {
            System.out.println("create " + model.getEntityClass() + " " + model.getIndexes());
            JsonObject views = new JsonObject();

            model.getIndexes().stream().forEach(idx -> {
                Orm.IndexCreator creator = idx.getIndex();
                views.put(creator.getIndexName(), creator.create(this, model.getEntityClass()));
            });

            String id = "_design/" + model.getEntityClass().getSimpleName();
            JsonObject doc = new JsonObject()
                    .put("language", "javascript")
                    .put("views", views);

            database.process(database.documentCreate(true, id, doc), Flow.with(handler, r -> {
                h.handle(Future.succeededFuture());
            }));
        }, $ -> {
            handler.handle(Future.succeededFuture(true));
        });
    }


    @Override
    public <T extends Entity> void store(T entity, Handler<AsyncResult<T>> handler) {
        entity.getDoc().put("$type", entity.getCollectionName());
        entity.updateModificationDate();
        database.process(database.documentCreate(false, entity.getId(), entity.getDoc()), Flow.with(handler, r -> {
            log.debug("stored {}:" + entity.getDoc());
            if (r.getBoolean("ok", false)) {
                entity.setRev(r.getString("rev"));
            }
            return entity;
        }));
    }

    @Override
    public <T extends Entity> void saveAttachment(T entity, ReadStream<Buffer> stream, Handler<AsyncResult<T>> handler) {
        entity.updateModificationDate();
        database.process(database.attachmentCreate(false, entity.getId(), entity.getRev(), stream), Flow.with(handler, r -> {
            if (r.getBoolean("ok", false)) {
                entity.setRev(r.getString("rev"));
            }
            return entity;
        }));
    }


}
