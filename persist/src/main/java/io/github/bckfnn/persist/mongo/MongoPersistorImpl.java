package io.github.bckfnn.persist.mongo;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.bckfnn.callback.ElementReadStream;
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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.mongo.MongoClient;

public class MongoPersistorImpl implements Persistor, EntityManager {
    private static final Logger log = LoggerFactory.getLogger(MongoPersistorImpl.class);
    @SuppressWarnings("unused")
    private final Vertx vertx;
    private final Schema schema;
    private MongoClient client;
    @SuppressWarnings("unused")
    private String databaseName;

    public MongoPersistorImpl(Vertx vertx, Schema schema, String databaseName, Handler<AsyncResult<Void>> handler) {
        this(vertx, schema, "localhost", 27017, null, null, databaseName, handler);
    }

    public MongoPersistorImpl(Vertx vertx, Schema schema, String host, int port, String username, String password, String databaseName, Handler<AsyncResult<Void>> handler) {
        this.vertx = vertx;
        this.schema = schema;
        this.databaseName = databaseName;

        JsonObject config = new JsonObject();
        config.put("host", host);
        config.put("port", port);
        config.put("db_name", databaseName);
        client = MongoClient.createShared(vertx, config, "MyPoolName");
    }

    @Override
    public <T extends Embedded> T create(Class<T> cls) {
        T entity;
        try {
            entity = schema.newInstance(cls);
            entity.setEntityManager(this);
            /*
            if (init && entity instanceof Entity) {
                ((Entity) entity).updateCreationDate();
            }
             */
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
        return ent;
    }


    @Override
    public <T extends Entity> void load(Class<T> cls, String id, Handler<AsyncResult<T>> handler) {
        JsonObject query = new JsonObject();
        query.put("_id", id);
        client.findOne(cls.getSimpleName(), query, null, r -> {
            if (r.succeeded()) {
                handler.handle(Future.succeededFuture(makeEntity(cls, r.result())));
            }
        });
    }

    @Override
    public <T extends Entity> void load(Class<T> class1, Orm.Query<T> query, Handler<AsyncResult<T>> handler) {
        query.apply(this, Flow.with(handler, r -> {
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

    public <T extends Entity> void query(Class<T> clazz, JsonObject query, Handler<AsyncResult<ReadStream<T>>> handler) {
        ElementReadStream<T> rs = new ElementReadStream<>();

        handler.handle(Future.succeededFuture(rs));
        client.find(clazz.getSimpleName(), query, Flow.with(handler, docs -> {
            for (JsonObject d : docs) {
                rs.send(makeEntity(clazz, d));
            }
            rs.end();
        }));
    }

    public <T extends Entity> void find(Class<T> clazz, JsonObject query, Handler<AsyncResult<T>> handler) {
        client.findOne(clazz.getSimpleName(), query, null, Flow.with(handler, doc -> {
            handler.handle(Future.succeededFuture(makeEntity(clazz, doc)));
        }));
    }

    @Override
    public <T extends Entity> void query(Class<T> class1, Orm.Query<T> query, Handler<AsyncResult<ReadStream<T>>> handler) {
        query.apply(this, Flow.with(handler, r -> {
            handler.handle(Future.succeededFuture((ReadStream<T>) r));
        }));
    }

    @Override
    public void readAttachment(String id, Handler<AsyncResult<ReadStream<Buffer>>> handler) {
        log.error("readAttachment nyi");
    }

    @Override
    public <T extends Entity> void saveAttachment(T entity, ReadStream<Buffer> stream, Handler<AsyncResult<T>> handler) {
        log.info("saveAttachment");
        int chunkSize = 255*1024;
        AtomicInteger pending = new AtomicInteger(0);
        String fileId = "xxx";
        AtomicInteger cnt = new AtomicInteger(0);
        AtomicBoolean end = new AtomicBoolean(false);

        JsonObject file = new JsonObject();

        file.put("filename", "body-" + entity.getId());
        file.put("contentType", "application/binary");
        file.put("chunkSize", chunkSize);

        Handler<Void> complete = $ -> {
            log.info("saveAttachment file");
            file.put("length", cnt.get() * chunkSize + pending.get());
            client.save("fs.files", file, Flow.with(handler, r -> {
                entity.getDoc().put("_attachmentId", r);

                client.save(entity.getCollectionName(), entity.getDoc(), Flow.with(handler, $2 -> {
                    handler.handle(Future.succeededFuture(entity));
                }));
            }));
        };

        RecordParser rp = RecordParser.newFixed(chunkSize, b -> {
            log.info("saveAttachment block");
            stream.pause();
            JsonObject chunk = new JsonObject();
            chunk.put("files_id", fileId);
            chunk.put("n", cnt.getAndIncrement());
            chunk.put("data", b.getBytes());
            pending.addAndGet(- b.length());

            log.info("call save chunk");
            client.save("fs.chunks", chunk, Flow.with(handler, r -> {
                log.info("saveAttachment block resume");
                if (end.get()) {
                    complete.handle(null);
                } else {
                    stream.resume();
                }
            }));
        });


        stream.endHandler($ -> {
            log.info("saveAttachment end " + pending.get());

            end.set(true);
            if (pending.get() > 0) {
                rp.fixedSizeMode(pending.get());
                rp.handle(Buffer.buffer());
            } else {
                complete.handle(null);
            }
        });
        stream.exceptionHandler(exc -> {
            log.info("saveAttachment exc ");
            handler.handle(Future.failedFuture(exc));
        });
        stream.handler(buf -> {
            log.info("saveAttachment handler " + buf.length() + " " + buf.getBuffer(0, 5));
            pending.addAndGet(buf.length());
            rp.handle(buf);
        });
        log.info("resume");
        //stream.resume();
    }

    @Override
    public <T extends Entity> void update(T entity, Handler<AsyncResult<T>> handler) {
        log.error("update  nyi");
        handler.handle(Future.succeededFuture(entity));
    }

    @Override
    public <T extends Entity> void delete(T entity, Handler<AsyncResult<T>> handler) {
        log.error("delete nyi");
        handler.handle(Future.succeededFuture(entity));
    }

    @Override
    public <T extends Entity> void store(T entity, Handler<AsyncResult<T>> handler) {
        client.save(entity.getCollectionName(), entity.getDoc(), Flow.with(handler, r -> {
            handler.handle(Future.succeededFuture(entity));
        }));
    }

    @Override
    public void dropDatabase(Handler<AsyncResult<Boolean>> handler) {
        JsonObject cmd = new JsonObject();
        cmd.put("dropDatabase", 1);
        client.runCommand("dropDatabase", cmd, r -> {
            log.info("dropDatabase {}", r.result());
            if (r.succeeded()) {
                handler.handle(Future.succeededFuture(true));
            } else {
                handler.handle(Future.failedFuture(r.cause()));
            }
        });
    }

    @Override
    public void createDatabase(Handler<AsyncResult<Boolean>> handler) {
        createDesign(handler);
        //handler.handle(Future.succeededFuture(true));
    }

    @Override
    public void createDesign(Handler<AsyncResult<Boolean>> handler) {
        Stream<Schema.SchemaModel<?>> x = schema.models().stream()
                .filter(model -> Entity.class.isAssignableFrom(model.getEntityClass()));
        Flow.forEach(x, (model, h) -> {
            System.out.println("create " + model.getEntityClass() + " " + model.getIndexes());

            JsonObject cmd = new JsonObject();
            cmd.put("create", model.getEntityClass().getSimpleName());
            client.runCommand("create", cmd, Flow.with(h, r -> {
                JsonArray views = new JsonArray();

                model.getIndexes().stream().forEach(idx -> {
                    Orm.IndexCreator creator = idx.getIndex();
                    views.add(creator.create(this, model.getEntityClass()));
                });

                JsonObject indexCmd = new JsonObject();
                indexCmd.put("createIndexes", model.getEntityClass().getSimpleName());
                indexCmd.put("indexes", views);
                log.info("cmd {}", indexCmd);
                if (views.size() > 0) {
                    client.runCommand("createIndexes", indexCmd, Flow.with(h, $ -> {
                        h.handle(Future.succeededFuture());
                    }));
                } else {
                    h.handle(Future.succeededFuture());
                }
            }));
        }, $ -> {
            handler.handle(Future.succeededFuture(true));
        });

    }
}