package io.github.bckfnn.persist.mongo;

import io.github.bckfnn.persist.Embedded;
import io.github.bckfnn.persist.Entity;
import io.github.bckfnn.persist.Orm;
import io.github.bckfnn.persist.OrmFactory;
import io.github.bckfnn.persist.Persistor;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;

public class MongoOrmFactory implements OrmFactory {

    @Override
    public <T extends Entity> Orm.Index<T> makeIndex(Class<T> type, String indexName) {
        return new IndexImpl<>(type, indexName);
    }

    @Override
    public <T extends Entity> Orm.HasMany<T> makeHasMany(Entity proxy, String key, Class<T> classFor, Orm.HasMany.Def relation) {
        return new HasManyImpl<>(proxy, key, classFor, relation);
    }

    @Override
    public <T extends Entity> Orm.BelongsTo<T> makeBelongsTo(Embedded proxy, String key, Class<T> classFor, Orm.BelongsTo.Def relation) {
        return new BelongsToImpl<>(proxy, key, classFor, relation);
    }

    @Override
    public <T extends Entity> Orm.HasOne<T> makeHasOne(Embedded proxy, String key, Class<T> classFor, Orm.HasOne.Def relation) {
        return new HasOneImpl<>(proxy, key, classFor, relation);
    }

    @Override
    public <T extends Entity> Orm.HasManyEmbedded<T> makeHasManyEmbedded(Entity proxy, String key, Class<T> classFor, Orm.HasManyEmbedded.Def relation) {
        return new HasManyEmbeddedImpl<>(proxy, key, classFor, relation);
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

    static class IndexImpl<T extends Entity> extends Orm.Index<T> {
        IndexImpl(Class<T> type, String indexName) {
            super(type, indexName);
        }

        @Override
        public JsonObject create(Persistor persistor, Class<?> owner) {
            JsonObject  index = new JsonObject();
            for (String k : keys()) {
                index.put(k, 1);
            }
            return index;
        }

        @Override
        public void apply(Persistor persistor, Handler<AsyncResult<ReadStream<T>>> handler) {
        }

        @Override
        public Orm.Query<T> get(String key1) {
            return (persistor, handler) -> {

                JsonObject query = new JsonObject().put(keys().get(0), key1);

                MongoPersistorImpl p = (MongoPersistorImpl) persistor;
                p.query(type, query, handler);
            };
        }

        @Override
        public Orm.Query<T> get(String key1, String key2) {
            return (persistor, handler) -> {

                JsonObject query = new JsonObject().put(keys().get(0), key1).put(keys().get(1), key2);

                MongoPersistorImpl p = (MongoPersistorImpl) persistor;
                p.query(type, query, handler);
            };
        }

        @Override
        public Orm.Query<T> get(JsonArray key) {
            return (persistor,handler) -> {

                JsonObject query = new JsonObject();
                for (int i = 0; i < keys.length; i++) {
                    query.put(keys().get(i), keys[i]);
                }

                MongoPersistorImpl p = (MongoPersistorImpl) persistor;
                p.query(type, query, handler);
            };
        }
    };

    public static class HasManyImpl<T extends Entity> extends HasManyBase<T> implements Orm.HasMany<T> {
        public HasManyImpl(Entity proxy, String key, Class<T> classFor, Orm.HasMany.Def relation) {
            super(proxy, key, classFor, relation);
        }

        @Override
        public void join(boolean asc, JsonArray extraKeys, Handler<AsyncResult<ReadStream<T>>> handler) {
            String key = relation.reverse().index().keys().get(0);
            JsonObject query = new JsonObject().put(key, proxy.getId());

            MongoPersistorImpl p = (MongoPersistorImpl) proxy.getEntityManager();
            p.query(clazz, query, handler);
        }
    }

    public static class BelongsToImpl<T extends Entity> extends BelongsToBase<T> implements Orm.BelongsTo<T> {
        public BelongsToImpl(Embedded proxy, String key, Class<T> classFor, Orm.BelongsTo.Def relation) {
            super(proxy, key, classFor, relation);
        }

        @Override
        public void load(Handler<AsyncResult<T>> handler) {
            MongoPersistorImpl p = (MongoPersistorImpl) proxy.getEntityManager();
            p.load(clazz, proxy.getDoc().getString(key), handler);
        }
    }

    public static class HasOneImpl<T extends Entity> extends HasOneBase<T> implements Orm.HasOne<T> {
        public HasOneImpl(Embedded proxy, String key, Class<T> classFor, Orm.HasOne.Def relation) {
            super(proxy, key, classFor, relation);
        }

        @Override
        public void join(Handler<AsyncResult<T>> handler) {
            MongoPersistorImpl p = (MongoPersistorImpl) proxy.getEntityManager();
            p.load(clazz, proxy.getDoc().getString(key), handler);
        }
    }

    public static class HasManyEmbeddedImpl<T extends Entity> extends HasManyEmbeddedBase<T> implements Orm.HasManyEmbedded<T> {
        public HasManyEmbeddedImpl(Entity proxy, String key, Class<T> classFor, Orm.HasManyEmbedded.Def relation) {
            super(proxy, key, classFor, relation);
        }

        @Override
        public void join(Handler<AsyncResult<ReadStream<T>>> handler) {
            JsonArray keyList = proxy.getDoc().getJsonArray(key);
            if (keyList == null) {
                keyList = new JsonArray();
            }
            JsonObject query = new JsonObject().put("_id", new JsonObject().put("$in", keyList));

            MongoPersistorImpl p = (MongoPersistorImpl) proxy.getEntityManager();
            p.query(clazz, query, handler);
        }
    }
}
