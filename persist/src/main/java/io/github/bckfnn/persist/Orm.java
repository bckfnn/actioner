/*
 * Copyright 2015 [inno:vasion]
 */
package io.github.bckfnn.persist;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.ServiceHelper;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;

public interface Orm {
    public static final OrmFactory factory = ServiceHelper.loadFactory(OrmFactory.class);


    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD })
    public @interface Property {
        /**
         * name as stored in the database document.
         * @return the field name.
         */
        String value() default "";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD, ElementType.TYPE })
    public @interface Relation {
        String value();
    }

    public interface Query<T> {
        public void apply(Persistor persistor, Handler<AsyncResult<ReadStream<T>>> handler);
    }

    public interface JoinQuery<T> extends Query<T> {
        public String target();
    }

    public interface IndexCreator {
        public JsonObject create(Persistor persistor, Class<?> owner);
        public String getIndexName();
    }

    public abstract static class Index<T extends Entity> implements Query<T>, IndexCreator {
        protected Class<T> type;
        protected String indexName;
        protected String query;
        protected String[] keys;
        protected Function<Index<T>, String> key;
        protected Function<Index<T>, String> emit;
        protected Function<Index<T>, String> reduce;

        public static <T extends Entity> Index<T> make(Class<T> type, String indexName) {
            return factory.makeIndex(type, indexName);
        }


        public Index(Class<T> type, String indexName)  {
            this.type = type;
            this.indexName = indexName;
        }

        public Index<T> queryX(String query) {
            this.query = query;
            return this;
        }

        public Index<T> keyX(Function<Index<T>, String> key) {
            this.key = key;
            return this;
        }

        public Index<T> keys(String... keys) {
            this.keys = keys;
            return this;
        }

        public Index<T> emitX(Function<Index<T>, String> emit) {
            this.emit = emit;
            return this;
        }

        public Index<T> reduceX(Function<Index<T>, String> reduce) {
            this.reduce = reduce;
            return this;
        }

        @Override
        public String getIndexName() {
            return indexName;
        }

        public String table() {
            return type.getSimpleName();
        }

        public List<String> keys() {
            return Arrays.asList(keys);
        }

        public abstract Orm.Query<T> get(String key1);

        public abstract Orm.Query<T> get(String key1, String key2);

        public abstract Orm.Query<T> get(JsonArray value);

        //public abstract Orm.Query<T> group(Class<? extends Entity> ret, JsonArray extraKeys, int level);

        //public abstract Orm.Query<T> query(Class<? extends Entity> ret, JsonArray extraKeys);
    }

    public static interface HasMany<T> {
        public static interface Def {
            public Orm.BelongsTo.Def reverse();
            public Class<? extends Entity> type();
        }

        public static Def make(Class<? extends Entity> type, Orm.BelongsTo.Def belongsTo) {
            return new Def() {
                @Override
                public Orm.BelongsTo.Def reverse() {
                    return belongsTo;
                }

                @Override
                public Class<? extends Entity> type() {
                    return type;
                }
            };
        }

        public void join(Handler<AsyncResult<ReadStream<T>>> handler);

        public void join(boolean asc, Handler<AsyncResult<ReadStream<T>>> handler);

        public void join(boolean asc, JsonArray extraKeys, Handler<AsyncResult<ReadStream<T>>> handler);

        public T make();
    }

    public static interface BelongsTo<T>  {
        public static interface Def  {

            public Orm.Index<?> index();
            public Class<? extends Entity> type();
            public String label();
        }

        public static Def make(Class<? extends Entity> type, String label, Orm.Index<?> index) {
            return new Def() {
                @Override
                public Index<?> index() {
                    return index;
                }

                @Override
                public String label() {
                    return label;
                }

                @Override
                public Class<? extends Entity> type() {
                    return type;
                }
            };
        }
        public void load(Handler<AsyncResult<T>> handler);
    }

    public static interface HasOne<T> {
        public static interface Def {
            public Class<? extends Entity> type();
        }

        public static Def make(Class<? extends Entity> type) {
            return new Def() {
                @Override
                public Class<? extends Entity> type() {
                    return type;
                }
            };
        }

        public void join(Handler<AsyncResult<T>> handler);

        public T make();
        public void set(T value);
        public String getId();
    }

    public static interface HasManyEmbedded<T> {
        public static interface Def {
            public Class<? extends Entity> type();
        }

        public static Def make(Class<? extends Entity> type) {
            return new Def() {
                @Override
                public Class<? extends Entity> type() {
                    return type;
                }
            };
        }

        public void join(Handler<AsyncResult<ReadStream<T>>> handler);

        public T make();
        public void add(T add);
        public int size();
    }

    public interface EmbeddedMap<V> {
        public V get(String key);
        public void put(String key, V value);
        public Iterator<String> keys();
    }

    public interface EmbeddedList<V> {
        public V get(int idx);
        public void set(int idx, V value);
        public void add(V value);
        public int size();
        public boolean contains(V value);
    }
}
