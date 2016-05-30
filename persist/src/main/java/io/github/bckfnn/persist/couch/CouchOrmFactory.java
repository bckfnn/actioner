package io.github.bckfnn.persist.couch;

import io.github.bckfnn.callback.ElementReadStream;
import io.github.bckfnn.callback.Flow;
import io.github.bckfnn.persist.Embedded;
import io.github.bckfnn.persist.Entity;
import io.github.bckfnn.persist.EntityManager;
import io.github.bckfnn.persist.Orm;
import io.github.bckfnn.persist.OrmFactory;
import io.github.bckfnn.persist.Persistor;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;

public class CouchOrmFactory implements OrmFactory {
    @Override
    public <T extends Entity> Orm.Index<T> makeIndex(Class<T> type, String indexName) {
        return new IndexImpl<T>(type, indexName);
    }

    @Override
    public <T extends Entity> Orm.HasMany<T> makeHasMany(Entity proxy, String key, Class<T> classFor, Orm.HasMany.Def relation) {
        return new HasManyImpl<T>(proxy, key, classFor, relation);
    }

    @Override
    public <T extends Entity> Orm.BelongsTo<T> makeBelongsTo(Embedded proxy, String key, Class<T> classFor, Orm.BelongsTo.Def relation) {
        return new BelongsToImpl<T>(proxy, key, classFor, relation);
    }

    @Override
    public <T extends Entity> Orm.HasOne<T> makeHasOne(Embedded proxy, String key, Class<T> classFor, Orm.HasOne.Def relation) {
        return new HasOneImpl<T>(proxy, key, classFor, relation);
    }

    @Override
    public <T extends Entity> Orm.HasManyEmbedded<T> makeHasManyEmbedded(Entity proxy, String key, Class<T> classFor, Orm.HasManyEmbedded.Def relation) {
        return new HasManyEmbeddedImpl<T>(proxy, key, classFor, relation);
    }

    @Override
    public <T extends Entity> Orm.EmbeddedMap<T> makeEmbeddedMapRef(Embedded parent, String key, Class<T> valueClass) {
        return new EmbeddedMapRefImpl<>(parent, key, valueClass);
    }

    @Override
    public <T> Orm.EmbeddedMap<T> makeEmbeddedMapValue(Embedded parent, String key) {
        return new EmbeddedMapValueImpl<>(parent, key);
    }

    @Override
    public <T extends Entity> Orm.EmbeddedList<T> makeEmbeddedListRef(Embedded parent, String key, Class<T> valueClass) {
        return new EmbeddedListRefImpl<>(parent, key, valueClass);
    }

    @Override
    public <T> Orm.EmbeddedList<T> makeEmbeddedListValue(Embedded parent, String key) {
        return new EmbeddedListValueImpl<>(parent, key);
    }

    public static class IndexImpl<T extends Entity> extends Orm.Index<T> {

        IndexImpl(Class<T> type, String indexName) {
            super(type, indexName);
        }

        @Override
        public Orm.Query<T> get(String value) {
            return (persistor, handler) -> {
                JsonObject params = new JsonObject()
                        .put("key", new JsonArray().add(value))
                        .put("include_docs", true)
                        .put("reduce", false);

                query(persistor, params, handler);
            };
        }

        @Override
        public Orm.Query<T> get(String key1, String key2) {
            return (persistor, handler) -> {
                JsonObject params = new JsonObject()
                        .put("key", new JsonArray().add(key1).add(key2))
                        .put("include_docs", true)
                        .put("reduce", false);

                query(persistor, params, handler);
            };
        }

        @Override
        public Orm.Query<T> get(JsonArray value) {
            return (persistor, handler) -> {
                JsonObject params = new JsonObject()
                        .put("key", value)
                        .put("include_docs", true)
                        .put("reduce", false);

                query(persistor, params, handler);
            };
        }

        @Override
        public void apply(Persistor persistor, Handler<AsyncResult<ReadStream<T>>> handler) {
            CouchPersistorImpl p = (CouchPersistorImpl) persistor;

            Database db = p.getDatabase();


            ElementReadStream<T> results = new ElementReadStream<>();

            JsonObject params = new JsonObject()
                    .put("include_docs", true)
                    .put("reduce", false);
            db.process(db.view("/_design/" + type.getSimpleName(), indexName, params), Flow.with(handler, r -> {
                handler.handle(Future.succeededFuture(results));
                r.getJsonArray("rows").stream().forEach(o -> {
                    T elm = ((CouchPersistorImpl) persistor).makeEntity(type, ((JsonObject) o).getJsonObject("doc"));
                    results.send(elm);
                });
                results.end();
            }));
        }
/*
        @Override
        public Orm.Query<T> query(JsonArray extraKeys) {
            return (persistor, db, handler) -> {
                JsonArray low = new JsonArray().addAll(extraKeys);
                JsonArray high = new JsonArray().addAll(extraKeys).add(new JsonObject());

                JsonObject params = new JsonObject();

                params.put("startkey", low);
                params.put("endkey", high);
                params.put("inclusive_end", false);
                //params.add("group", true);
                params.put("include_docs", true);
                params.put("reduce", false);

                db.process(db.view("/_design/" + type.getSimpleName(), indexName, params), S.ar(handler, r -> {
                    ElementReadStream<T> results = new ElementReadStream<>();
                    handler.handle(Future.succeededFuture(results));
                    r.getJsonArray("rows").stream().forEach(o -> {
                        results.send((T) ((CouchPersistorImpl) persistor).makeEntity(type, ((JsonObject) o).getJsonObject("doc")));
                    });
                    results.end();
                }));
            };
        }

        @Override
        public Orm.Query<T> group(Class<? extends Entity> ret, JsonArray extraKeys, int level) {
            return (persistor, db, handler) -> {
                JsonArray low = new JsonArray().addAll(extraKeys);
                JsonArray high = new JsonArray().addAll(extraKeys).add(new JsonObject());

                JsonObject params = new JsonObject();

                params.put("startkey", low);
                params.put("endkey", high);
                params.put("inclusive_end", false);
                //params.add("group", true);
                params.put("group_level", level);

                db.process(db.view("/_design/" + type.getSimpleName(), indexName, params), S.ar(handler, r -> {
                    ElementReadStream<T> results = new ElementReadStream<>();
                    handler.handle(Future.succeededFuture(results));
                    r.getJsonArray("rows").stream().forEach(o -> {
                        results.send((T) ((CouchPersistorImpl) persistor).makeEntity(ret, ((JsonObject) o)));
                    });
                    results.end();
                }));
            };
        }
*/
        @Override
        public JsonObject create(Persistor persistor, Class<?> owner) {
            String restrict = "doc.$type == \"" + type.getSimpleName() + "\"";
            if (query != null) {
                restrict = restrict += " && " + query;
            }

            String mapFunc = "function(doc) { if (" + restrict + ") { " + emit() + "}}";

            JsonObject v = new JsonObject()
                    .put("map", mapFunc);

            if (reduce != null) {
                v.put("reduce", reduce.apply(this));
            }
            return v;
        }

        private void query(Persistor persistor, JsonObject params, Handler<AsyncResult<ReadStream<T>>> handler) {
            CouchPersistorImpl p = (CouchPersistorImpl) persistor;

            Database db = p.getDatabase();

            db.process(db.view("/_design/" + type.getSimpleName(), indexName, params), Flow.with(handler, r -> {
                ElementReadStream<T> results = new ElementReadStream<>();
                handler.handle(Future.succeededFuture(results));
                r.getJsonArray("rows").stream().forEach(o -> {
                    results.send(((CouchPersistorImpl) persistor).makeEntity(type, ((JsonObject) o).getJsonObject("doc")));
                });
                results.end();
            }));
        }

        private String key() {
            if (key != null) {
                return this.key.apply(this);
            }
            if (keys != null) {
                StringBuilder s = new StringBuilder("[");
                for (int i = 0; i < keys.length; i++) {
                    if (i > 0) {
                        s.append(',');
                    }
                    s.append("doc." + keys[i]);
                }
                s.append(']');
                return s.toString();
            }

            return "[doc." + indexName + "]";
        }


        private String emit() {
            if (emit != null) {
                return emit.apply(this);
            } else {
                return "emit(" + key() + ", null);";
            }
        }
    };

    public static class HasManyImpl<T extends Entity> extends HasManyBase<T> implements Orm.HasMany<T> {
        public HasManyImpl(Entity proxy, String key, Class<T> classFor, Orm.HasMany.Def relation) {
            super(proxy, key, classFor, relation);
        }

        @Override
        public void join(boolean asc, JsonArray extraKeys, Handler<AsyncResult<ReadStream<T>>> handler) {
            JsonArray low = new JsonArray().add(proxy.getId()).addAll(extraKeys);
            JsonArray high = new JsonArray().add(proxy.getId()).addAll(extraKeys).add(new JsonObject());

            JsonObject keys = new JsonObject();

            if (asc) {
                keys.put("startkey", low);
                keys.put("endkey", high);
            } else {
                keys.put("startkey", high);
                keys.put("endkey", low);

                keys.put("descending", true);
            }
            keys.put("include_docs", true);
            keys.put("reduce", false);
            keys.put("inclusive_end", false);

            CouchPersistorImpl p = (CouchPersistorImpl) proxy.getEntityManager();

            Database db = p.getDatabase();
            db.process(db.view("/_design/" + relation.type().getSimpleName(), relation.reverse().index().getIndexName(), keys), Flow.with(handler, r-> {
                ElementReadStream<T> results = new ElementReadStream<>();

                handler.handle(Future.succeededFuture(results));
                loadDocs(proxy.getEntityManager(), clazz, r, results);

                results.end();
            }));
        }
    }

    public static class BelongsToImpl<T extends Entity> extends BelongsToBase<T> implements Orm.BelongsTo<T> {
        public BelongsToImpl(Embedded proxy, String key, Class<T> classFor, Orm.BelongsTo.Def relation) {
            super(proxy, key, classFor, relation);
        }

        @Override
        public void load(Handler<AsyncResult<T>> handler) {
            CouchPersistorImpl p = (CouchPersistorImpl) proxy.getEntityManager();
            Database db = p.getDatabase();
            System.out.println(key + " " + proxy);
            db.process(db.documentLoad(proxy.getDoc().getString(key)), Flow.with(handler, r -> {
                T ent = p.makeEntity(clazz, r);
                handler.handle(Future.succeededFuture(ent));
            }));
        }
    }

    public static class HasOneImpl<T extends Entity> extends HasOneBase<T> implements Orm.HasOne<T> {
        public HasOneImpl(Embedded proxy, String key, Class<T> classFor, Orm.HasOne.Def relation) {
            super(proxy, key, classFor, relation);
        }

        @Override
        public void join(Handler<AsyncResult<T>> handler) {
            CouchPersistorImpl p = (CouchPersistorImpl) proxy.getEntityManager();

            Database db = p.getDatabase();
            db.process(db.documentLoad(proxy.getDoc().getString(key)), Flow.with(handler, r -> {
                T ent = p.makeEntity(clazz, r);
                handler.handle(Future.succeededFuture(ent));
            }));
        }
    }

    public static class HasManyEmbeddedImpl<T extends Entity> extends HasManyEmbeddedBase<T> implements Orm.HasManyEmbedded<T> {
        public HasManyEmbeddedImpl(Entity proxy, String key, Class<T> classFor, Orm.HasManyEmbedded.Def relation) {
            super(proxy, key, classFor, relation);
        }

        @Override
        public void join(Handler<AsyncResult<ReadStream<T>>> handler) {
            JsonObject params = new JsonObject()
                    .put("include_docs", true)
                    .put("reduce", false);

            JsonArray keyList = proxy.getDoc().getJsonArray(key);
            if (keyList == null) {
                keyList = new JsonArray();
            }
            JsonArray keyList$ = keyList;

            CouchPersistorImpl p = (CouchPersistorImpl) proxy.getEntityManager();

            Database db = p.getDatabase();

            db.process(db.docs(keyList$, params), Flow.with(handler, r -> {
                ElementReadStream<T> results = new ElementReadStream<>();
                handler.handle(Future.succeededFuture(results));
                loadDocs(proxy.getEntityManager(), clazz, r, results);
                results.end();
            }));
        }
    }



    private static <T extends Entity> void loadDocs(EntityManager em, Class<T> type, JsonObject res, ElementReadStream<T> results) {
        CouchPersistorImpl p = ((CouchPersistorImpl) em);

        for (Object o : res.getJsonArray("rows")) {
            JsonObject r = (JsonObject) o;
            JsonObject doc = r.getJsonObject("doc");
            T ent = p.makeEntity(type, doc);
            results.send(ent);
        }
    }
}
