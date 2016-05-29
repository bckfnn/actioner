package io.github.bckfnn.persist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;

public interface OrmFactory {

    public <T extends Entity> Orm.Index<T> makeIndex(Class<T> type, String indexName);

    public <T extends Entity> Orm.HasMany<T> makeHasMany(Entity proxy, String key, Class<T> classFor, Orm.HasMany.Def relation);

    public <T extends Entity> Orm.BelongsTo<T> makeBelongsTo(Embedded proxy, String key, Class<T> classFor, Orm.BelongsTo.Def relation);

    public <T extends Entity> Orm.HasOne<T> makeHasOne(Embedded proxy, String key, Class<T> classFor, Orm.HasOne.Def relation);

    public <T extends Entity> Orm.HasManyEmbedded<T> makeHasManyEmbedded(Entity proxy, String key, Class<T> classFor, Orm.HasManyEmbedded.Def relation);

    public <T extends Entity> Orm.EmbeddedMap<T> makeEmbeddedMapRef(Embedded parent, String key, Class<T> valueClass);

    public <T> Orm.EmbeddedMap<T> makeEmbeddedMapValue(Embedded parent, String key);

    public <T extends Entity> Orm.EmbeddedList<T> makeEmbeddedListRef(Embedded parent, String key, Class<T> valueClass);

    public <T> Orm.EmbeddedList<T> makeEmbeddedListValue(Embedded parent, String key);

    abstract class HasManyBase<T extends Entity> implements Orm.HasMany<T> {
        protected Entity proxy;
        protected String key;
        protected Class<T> clazz;
        protected Orm.HasMany.Def relation;

        public HasManyBase(Entity proxy, String key, Class<T> classFor, Orm.HasMany.Def relation) {
            this.proxy = proxy;
            this.key = key;
            this.clazz = classFor;
            this.relation = relation;
        }

        @Override
        public void join(Handler<AsyncResult<ReadStream<T>>> handler) {
            join(true, new JsonArray(), handler);
        }

        @Override
        public void join(boolean asc, Handler<AsyncResult<ReadStream<T>>> handler) {
            join(asc, new JsonArray(), handler);
        }

        @Override
        public T make() {
            T child = proxy.getEntityManager().create(clazz);
            child.getDoc().put(relation.reverse().label(), proxy.getId());
            return child;
        }
    }

    abstract static class BelongsToBase<T extends Entity> implements Orm.BelongsTo<T> {
        protected Embedded proxy;
        protected String key;
        protected Class<T> clazz;
        protected Orm.BelongsTo.Def relation;

        public BelongsToBase(Embedded proxy, String key, Class<T> classFor, Orm.BelongsTo.Def relation) {
            this.proxy = proxy;
            this.key = key;
            this.clazz = classFor;
            this.relation = relation;
        }
    }

    abstract class HasOneBase<T extends Entity> implements Orm.HasOne<T> {
        protected Embedded proxy;
        protected String key;
        protected Class<T> clazz;
        protected Orm.HasOne.Def relation;

        public HasOneBase(Embedded proxy, String key, Class<T> classFor, Orm.HasOne.Def relation) {
            this.proxy = proxy;
            this.key = key;
            this.clazz = classFor;
            this.relation = relation;
        }

        @Override
        public T make() {
            T child = proxy.getEntityManager().create(clazz);
            proxy.getDoc().put(key, child.getId());
            return child;
        }

        @Override
        public void set(T child) {
            proxy.getDoc().put(key, child.getId());
        }

        @Override
        public String getId() {
            return proxy.getDoc().getString(key);
        }
    }

    abstract class HasManyEmbeddedBase<T extends Entity> implements Orm.HasManyEmbedded<T> {
        protected Entity proxy;
        protected String key;
        protected Class<T> clazz;
        protected Orm.HasManyEmbedded.Def relation;

        public HasManyEmbeddedBase(Entity proxy, String key, Class<T> classFor, Orm.HasManyEmbedded.Def relation) {
            this.proxy = proxy;
            this.key = key;
            this.clazz = classFor;
            this.relation = relation;
        }

        @Override
        public T make() {
            T child = proxy.getEntityManager().create(clazz);
            JsonArray arr = proxy.getDoc().getJsonArray(key);
            if (arr == null) {
                arr = new JsonArray().add(child.getId());
                proxy.getDoc().put(key, arr);
            } else {
                arr.add(child.getId());
            }
            return child;
        }

        @Override
        public void add(T child) {
            JsonArray arr = proxy.getDoc().getJsonArray(key);
            if (arr == null) {
                arr = new JsonArray().add(child.getId());
                proxy.getDoc().put(key, arr);
            } else {
                arr.add(child.getId());
            }
        }

        @Override
        public int size() {
            JsonArray arr = proxy.getDoc().getJsonArray(key);
            if (arr == null) {
                return 0;
            } else {
                return arr.size();
            }
        }
    }

    public class EmbeddedMapRefImpl<V extends Embedded> implements Orm.EmbeddedMap<V> {
        private final Embedded parent;
        private JsonObject map;

        private final Class<V> valueClass;
        private final java.util.Map<String, V> valueMap;


        public EmbeddedMapRefImpl(Embedded parent, String key, Class<V> valueClass) {
            this.parent = parent;
            this.valueClass = valueClass;

            map = parent.getDoc().getJsonObject(key);
            System.out.println(key + " " + map);
            if (map == null) {
                map = new JsonObject();
                parent.getDoc().put(key, map);
            }
            valueMap = new HashMap<String, V>(map.size());
        }

        @Override
        public V get(String key) {
            V val = valueMap.get(key);
            if (val != null) {
                return val;
            }
            JsonObject e = map.getJsonObject(key);
            if (e != null) {
                val = parent.getEntityManager().create(valueClass);
                val.setDoc(e);
                valueMap.put(key, val);
                return val;
            }
            return null;
        }

        @Override
        public void put(String key, V value) {
            valueMap.put(key, value);
            map.put(key, value.getDoc());
        }

        @Override
        public Iterator<String> keys() {
            return map.fieldNames().iterator();
        }
    }

    public class EmbeddedMapValueImpl<V> implements Orm.EmbeddedMap<V> {
        private JsonObject map;

        public EmbeddedMapValueImpl(Embedded parent, String key) {
            map = parent.getDoc().getJsonObject(key);
            if (map == null) {
                map = new JsonObject();
                parent.getDoc().put(key, map);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public V get(String key) {
            return (V) (map.getString(key));
        };

        @Override
        public void put(String key, V value) {
            map.put(key, (String) value);
        }

        @Override
        public Iterator<String> keys() {
            return map.fieldNames().iterator();
        }
    }

    public class EmbeddedListRefImpl<V extends Embedded> implements Orm.EmbeddedList<V> {
        private final Embedded parent;
        private final Class<V> valueClass;
        private final java.util.List<V> valueList;
        private JsonArray list;

        public EmbeddedListRefImpl(Embedded parent, String key, Class<V> valueClass) {
            this.parent = parent;
            this.valueClass = valueClass;

            list = parent.getDoc().getJsonArray(key);
            System.out.println(key + " " + list);
            if (list == null) {
                list = new JsonArray();
                parent.getDoc().put(key, list);
            }
            valueList = new ArrayList<V>(list.size());
            for (int i = 0; i < list.size(); i++) {
                valueList.add(null);
            }
        }

        @Override
        public V get(int idx) {
            if (idx < valueList.size() && valueList.get(idx) != null) {
                return valueList.get(idx);
            }

            V value = parent.getEntityManager().create(valueClass);
            value.setDoc(list.getJsonObject(idx));
            valueList.set(idx, value);
            return value;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void set(int idx, V value) {
            valueList.set(idx, value);
            list.getList().set(idx, value.getDoc());
        }

        @Override
        public void add(V value) {
            valueList.add(value);
            list.add(value.getDoc());
        }

        @Override
        public int size() {
            return list.size();
        }

        @Override
        public boolean contains(V value) {
            return valueList.contains(value);
        }
    }

    public class EmbeddedListValueImpl<V> implements Orm.EmbeddedList<V> {
        private JsonArray list;

        public EmbeddedListValueImpl(Embedded parent, String key) {
            list = parent.getDoc().getJsonArray(key);

            if (list == null) {
                list = new JsonArray();
                parent.getDoc().put(key, list);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public V get(int idx) {
            return (V) list.getString(idx);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void set(int idx, V value) {
            list.getList().set(idx, value);
        }

        @Override
        public void add(V value) {
            list.add((String) value);
        }

        @Override
        public int size() {
            return list.size();
        }

        @Override
        public boolean contains(V value) {
            return list.contains(value);
        }

    }
}
