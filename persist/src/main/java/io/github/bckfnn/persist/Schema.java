/*
 * Copyright 2015 [inno:vasion]
 */
package io.github.bckfnn.persist;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.bckfnn.callback.Flow;
import io.vertx.core.json.JsonObject;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.ClassLoadingStrategy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.instrumentation.FixedValue;
import net.bytebuddy.instrumentation.MethodDelegation;
import net.bytebuddy.instrumentation.method.bytecode.bind.annotation.Field;
import net.bytebuddy.instrumentation.method.bytecode.bind.annotation.IgnoreForBinding;
import net.bytebuddy.instrumentation.method.bytecode.bind.annotation.RuntimeType;
import net.bytebuddy.instrumentation.method.bytecode.bind.annotation.TargetMethodAnnotationDrivenBinder.ParameterBinder;
import net.bytebuddy.instrumentation.method.bytecode.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.modifier.Visibility;

public class Schema {
    private Map<Class<?>, Class<?>> interceptors = new HashMap<>();

    private Map<Class<?>, SchemaModel<?>> models = new HashMap<>();

    public Schema() {
        interceptors.put(String.class, StringInterceptor.class);
        interceptors.put(Integer.class, IntInterceptor.class);
        interceptors.put(Long.class, LongInterceptor.class);
        interceptors.put(Boolean.class, BooleanInterceptor.class);
        interceptors.put(Boolean.TYPE, BooleanInterceptor.class);
        interceptors.put(Date.class, DateInterceptor.class);
        interceptors.put(BigDecimal.class, BigDecimalInterceptor.class);
        interceptors.put(UUID.class, UUIDInterceptor.class);
        interceptors.put(Enum.class, EnumInterceptor.class);
        interceptors.put(byte[].class, ByteArrayInterceptor.class);
        interceptors.put(StateHistory.class, StateHistoryInterceptor.class);
        interceptors.put(Embedded.class, EmbeddedInterceptor.class);
        interceptors.put(Entity.class, EntityInterceptor.class);

        interceptors.put(Orm.EmbeddedList.class, EmbeddedListInterceptor.class);
        interceptors.put(Orm.EmbeddedMap.class, EmbeddedMapInterceptor.class);

        interceptors.put(Orm.HasOne.class, RelHasOneInterceptor.class);
        interceptors.put(Orm.HasMany.class, RelHasManyInterceptor.class);
        interceptors.put(Orm.HasManyEmbedded.class, RelHasManyEmbeddedInterceptor.class);
        interceptors.put(Orm.BelongsTo.class, RelBelongsToInterceptor.class);

    }

    public <T extends Embedded> void addEntity(Class<T> cls) throws Exception {
        SchemaModel<T> model = new SchemaModel<>(cls);
        models.put(cls, model);
    }

    public Collection<SchemaModel<?>> models() {
        return models.values();
    }

    @SuppressWarnings("unchecked")
    public <T> T newInstance(Class<T> cls) throws Exception {
        SchemaModel<?> model = models.get(cls);
        if (model == null) {
            throw new IllegalArgumentException("schema model for " + cls + " not found");
        }
        return (T) models.get(cls).newInstance();
    }

    public class SchemaModel<T> implements Comparable<SchemaModel<T>> {
        private Class<T> entityCls;
        private Class<? extends T> proxyCls;
        private Map<String, Interceptor<?>> properties = new HashMap<>();

        private List<SchemaProperty> schemaProperties = new ArrayList<>();

        private List<SchemaIndex> schemaIndexes = new ArrayList<>();

        public SchemaModel(Class<T> entityCls) throws Exception {
            this.entityCls = entityCls;
            init();
        }

        public String getName() {
            return entityCls.getSimpleName();
        }

        public boolean isEntity() {
            return Entity.class.isAssignableFrom(entityCls);
        }

        public T newInstance() throws Exception {
            return proxyCls.newInstance();
        }

        public Class<T> getEntityClass() {
            return entityCls;
        }

        public Collection<SchemaProperty> getProperties() {
            return schemaProperties;
        }

        public Collection<SchemaIndex> getIndexes() {
            return schemaIndexes;
        }


        @SuppressWarnings("unchecked")
        private void init() throws Exception {
            ParameterBinder<Field> paramBinder = Field.Binder.install(Getter.class, Setter.class);

            Class<?> superCls = Entity.class.isAssignableFrom(entityCls) ? EntityImpl.class : EmbeddedImpl.class;

            DynamicType.Builder<?> builder = new ByteBuddy()
                    .subclass(superCls, ConstructorStrategy.Default.DEFAULT_CONSTRUCTOR)
                    .implement(entityCls)
                    .name(entityCls.getName() + "$proxy");

            List<Method> methods = Arrays.asList(entityCls.getMethods());
            for (Method m : methods) {
                if (m.getAnnotation(Orm.Property.class) != null) {
                    String methodName = m.getName();
                    String fieldName = propName(methodName);
                    if (fieldName == null) {
                        throw new RuntimeException("can make property from method " + m);
                    }

                    Interceptor<?> interceptor = properties.get(fieldName);
                    if (interceptor == null) {
                        interceptor = makePropertyInterceptor(m, fieldName);
                        properties.put(fieldName, interceptor);
                        builder = builder.defineField(fieldName, interceptor.cls, Visibility.PRIVATE);

                        schemaProperties.add(new SchemaProperty(fieldName, interceptor.cls, "simple"));
                    }

                    builder = builder.defineMethod(m).intercept(MethodDelegation.to(interceptor).appendParameterBinder(paramBinder));
                }



                if (m.getAnnotation(Embedded.Relation.class) != null) {
                    String methodName = m.getName();
                    String fieldName = propName(methodName);
                    if (fieldName == null) {
                        throw new RuntimeException("can't make property from method " + m);
                    }
                    Interceptor<?> interceptor = properties.get(fieldName);
                    if (interceptor == null) {
                        interceptor = makeRelInterceptor(m, fieldName);
                        properties.put(fieldName, interceptor);
                        builder = builder.defineField(fieldName, interceptor.cls, Visibility.PRIVATE);

                        schemaProperties.add(new SchemaProperty(fieldName, interceptor.classFor(interceptor.cls), interceptor.cls.getSimpleName()));
                    }

                    builder = builder.defineMethod(m).intercept(MethodDelegation.to(interceptor).appendParameterBinder(paramBinder));
                }

            }

            builder = builder.method(ElementMatchers.named("getCollectionName")).intercept(FixedValue.value(entityCls.getSimpleName()));
            proxyCls = (Class<? extends T>) builder
                    .make()
                    .load(getClass().getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                    .getLoaded();

            List<java.lang.reflect.Field> fields = Arrays.asList(entityCls.getFields());
            for (java.lang.reflect.Field f : fields) {
                if (Orm.IndexCreator.class.isAssignableFrom(f.getType())) {
                    try {
                        schemaIndexes.add(new SchemaIndex(f.getName(), (Orm.IndexCreator) f.get(null)));
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }

        Interceptor<?> makePropertyInterceptor(Method m, String fieldName) throws Exception {
            Type type = propType(m);
            Class<?> cls = propClass(m);
            Orm.Property prop = m.getAnnotation(Orm.Property.class);
            return getInterceptorClass(cls).newInstance().key(prop.value()).type(type).cls(cls);
        }

        Interceptor<?> makeRelInterceptor(Method m, String fieldName) throws Exception {
            Type type = propType(m);
            Class<?> cls = propClass(m);
            Embedded.Relation prop = m.getAnnotation(Embedded.Relation.class);
            String label = prop.value(); //propName(prop.value());

            // get relation field.
            Object relation = entityCls.getField(fieldName).get(null);

            if (relation == null) {
                throw new RuntimeException("Rel.Relation field is missing");
            }

            return getInterceptorClass(cls).newInstance().key(label).type(type).cls(cls).relation(relation);
        }

        Class<Interceptor<?>> getInterceptorClass(Class<?> type) {
            List<Class<?>> candidates = new ArrayList<>();
            for (Class<?> t = type; t != null; t = t.getSuperclass()) {
                candidates.add(t);
                candidates.addAll(Arrays.asList(t.getInterfaces()));
            }
            for (Class<?> t : candidates) {
                @SuppressWarnings("unchecked")
                Class<Interceptor<?>> interceptorClass = (Class<Interceptor<?>>) interceptors.get(t);
                if (interceptorClass != null) {
                    return interceptorClass;
                }
            }
            throw new RuntimeException("unhandled field type:" + type + " in " + entityCls);
        }

        private Class<?> propClass(Method m) {
            if (m.getName().startsWith("get") || m.getName().startsWith("is")) {
                if (m.getGenericParameterTypes().length != 0) {
                    throw new RuntimeException("getter " + m + " must not take any arguments");
                }
                return m.getReturnType();
            }
            if (m.getName().startsWith("set")) {
                if (m.getGenericParameterTypes().length != 1) {
                    throw new RuntimeException("setter " + m + " must take 1 argument");
                }
                return m.getParameterTypes()[0];
            }
            throw new RuntimeException("type can not be deduced from method " + m);
        }

        private Type propType(Method m) {
            if (m.getName().startsWith("get") || m.getName().startsWith("is")) {
                if (m.getGenericParameterTypes().length != 0) {
                    throw new RuntimeException("getter " + m + " must not take any arguments");
                }
                return m.getGenericReturnType();
            }
            if (m.getName().startsWith("set")) {
                if (m.getGenericParameterTypes().length != 1) {
                    throw new RuntimeException("setter " + m + " must take 1 argument");
                }
                return m.getGenericParameterTypes()[0];
            }
            throw new RuntimeException("type can not be deduced from method " + m);
        }

        private String propName(String name) {
            for (String pref : prefix) {
                if (name.startsWith(pref)) {
                    int l = pref.length();
                    return Character.toLowerCase(name.charAt(l)) + name.substring(l + 1);
                }
            }
            return null;
        }

        @Override
        public int compareTo(SchemaModel<T> o) {
            return getName().compareTo(o.getName());
        }
    }

    static class SchemaProperty {
        String name;
        Class<?> cls;
        String type;

        public SchemaProperty(String name, Class<?> cls, String type) {
            super();
            this.name = name;
            this.cls = cls;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public Class<?> getCls() {
            return cls;
        }
    }

    public static class SchemaIndex {
        String name;
        Orm.IndexCreator index;

        public SchemaIndex(String name, Orm.IndexCreator index) {
            this.name = name;
            this.index = index;
        }

        public String getName() {
            return name;
        }

        public Orm.IndexCreator getIndex() {
            return index;
        }
    }

    static final String[] prefix = {
            "get", "set", "is",
    };

    public interface Getter<T> {
        T get();
    }

    public interface Setter<T> {
        void set(T val);
    }

    public abstract static class Interceptor<T> {
        protected String key;
        private Type type;
        protected Class<?> cls;
        protected Object relation;

        @IgnoreForBinding
        public Interceptor<T> key(String key) {
            this.key = key;
            return this;
        }

        @IgnoreForBinding
        public Interceptor<T> type(Type type) {
            this.type = type;
            return this;
        }

        @IgnoreForBinding
        public Interceptor<T> cls(Class<?> cls) {
            this.cls = cls;
            return this;
        }

        @IgnoreForBinding
        public Interceptor<T> relation(Object relation) {
            this.relation = relation;
            return this;
        }


        protected boolean noCache() {
            return false;
        }

        @RuntimeType
        public Object getter(@Field Setter<Object> set, @Field Getter<Object> get, @This Embedded proxy) {
            if (noCache()) {
                return getFromJson(proxy);
            }
            Object value = get.get();
            if (value != null) {
                return value;
            }

            value = getFromJson(proxy);

            if (value != null) {
                set.set(value);
            }
            return value;
        }

        @IgnoreForBinding
        abstract protected T getFromJson(Embedded proxy);

        @RuntimeType
        public Object setter(@Field Setter<Object> set, @This Embedded proxy, @RuntimeType T value) {
            if (value == null) {
                proxy.getDoc().remove(key);
                set.set(null);
                return null;
            }
            setToJson(proxy, value);
            set.set(value);
            return null;
        }

        @IgnoreForBinding
        abstract protected void setToJson(Embedded proxy, T value);


        @SuppressWarnings("unchecked")
        protected <X> Class<X> classFor(Type type) {
            try {
                String n = type.getTypeName();
                if (n.equals("byte[]")) {
                    n = "[B";
                } else if (n.equals("boolean")) {
                    return (Class<X>) Boolean.TYPE;
                }
                return (Class<X>) Class.forName(n);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        Type type() {
            return type;
        }
    }

    public static class StringInterceptor<T> extends Interceptor<T> {
        @SuppressWarnings("unchecked")
        @Override
        protected T getFromJson(Embedded proxy) {
            return (T) proxy.getDoc().getString(key, null);
        }

        @Override
        protected void setToJson(Embedded proxy, T value) {
            proxy.getDoc().put(key, value.toString());
        }
    }

    public static class IntInterceptor extends Interceptor<Integer> {
        @Override
        protected Integer getFromJson(Embedded proxy) {
            return proxy.getDoc().getInteger(key);
        }

        @Override
        protected void setToJson(Embedded proxy, Integer value) {
            proxy.getDoc().put(key, value);
        }
    }

    public static class LongInterceptor extends Interceptor<Long> {
        @Override
        protected Long getFromJson(Embedded proxy) {
            Long n = proxy.getDoc().getLong(key);
            if (n == null) {
                return 0l;
            }
            return n.longValue();
        }

        @Override
        protected void setToJson(Embedded proxy, Long value) {
            proxy.getDoc().put(key, value);
        }
    }

    public static class BooleanInterceptor extends Interceptor<Boolean> {

        @Override
        protected boolean noCache() {
            return true;
        }

        @Override
        protected Boolean getFromJson(Embedded proxy) {
            //System.out.println("getbool:" + key + " " + proxy.getDoc().get(key));
            return proxy.getDoc().getBoolean(key, false);
        }

        @Override
        protected void setToJson(Embedded proxy, Boolean value) {
            proxy.getDoc().put(key, value);
        }
    }

    static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");

    static Date convertToDate(JsonObject doc, String key) {
        String n = doc.getString(key, null);
        if (n != null) {
            try {
                return dateFormat.parse(n);
            } catch (ParseException e) {
                return null;
            }
        }
        return null;
    }

    public static class DateInterceptor extends Interceptor<Date> {
        @Override
        protected Date getFromJson(Embedded proxy) {
            return convertToDate(proxy.getDoc(), key);
        }

        @Override
        protected void setToJson(Embedded proxy, Date value) {
            proxy.getDoc().put(key, dateFormat.format(value));
        }
    }

    public static class BigDecimalInterceptor extends Interceptor<BigDecimal> {
        @Override
        protected BigDecimal getFromJson(Embedded proxy) {
            return new BigDecimal(proxy.getDoc().getString(key));
        }

        @Override
        protected void setToJson(Embedded proxy, BigDecimal value) {
            proxy.getDoc().put(key, value.toString());
        }
    }

    public static class UUIDInterceptor extends Interceptor<UUID> {
        @Override
        protected UUID getFromJson(Embedded proxy) {
            return UUID.fromString(proxy.getDoc().getString(key));
        }

        @Override
        protected void setToJson(Embedded proxy, UUID value) {
            proxy.getDoc().put(key, value.toString());
        }
    }

    public static class EnumInterceptor extends StringInterceptor<Enum<?>> {
        @Override
        protected Enum<?> getFromJson(Embedded proxy) {
            String v = proxy.getDoc().getString(key);
            Type actualType = ((ParameterizedType) type()).getActualTypeArguments()[0];
            Class<Enum<?>> c = classFor(actualType);
            if (v != null) {
                for (Enum<?> t : c.getEnumConstants()) {
                    if (t.name().equals(v)) {
                        return t;
                    }
                }
            } else {
                for (Enum<?> t : c.getEnumConstants()) {
                    try {
                        if (c.getField(t.name()).getAnnotation(DocState.class).isDefault()) {
                            return t;
                        }
                    } catch (Exception e) {
                        throw Flow.rethrow(e);
                    }
                }

            }
            return null;
        }
    }

    public static class ByteArrayInterceptor extends Interceptor<byte[]> {
        @Override
        protected byte[] getFromJson(Embedded proxy) {
            String b64 = proxy.getDoc().getString(key, null);
            if (b64 == null) {
                return null;
            }
            return Base64.getMimeDecoder().decode(b64);
        }

        @Override
        protected void setToJson(Embedded proxy, byte[] value) {
            proxy.getDoc().put(key, value);
        }
    }

    public static abstract class ReadonlyInterceptor<T> extends Interceptor<T> {
        Type actualType;

        @Override
        public Interceptor<T> type(Type type) {
            actualType = ((ParameterizedType) type).getActualTypeArguments()[0];
            return super.type(type);
        }

        @Override
        protected void setToJson(Embedded proxy, T value) {
            throw new RuntimeException("state history can not be assigned");
        }
    }

    public static class StateHistoryInterceptor<T extends Enum<T>> extends ReadonlyInterceptor<StateHistory<T>> {
        @Override
        protected StateHistory<T> getFromJson(Embedded proxy) {
            return new StateHistory<T>(classFor(actualType), proxy.getDoc(), key);
        }
    }

    public static class EmbeddedInterceptor<T extends Embedded> extends Interceptor<T> {
        @Override
        @SuppressWarnings("unchecked")
        protected T getFromJson(Embedded proxy) {
            T value = proxy.getEntityManager().create((Class<T>) cls);
            //            System.out.println("embed:" + key + " " + proxy.getDoc().fieldNames());
            JsonObject emb = proxy.getDoc().getJsonObject(key);
            if (emb != null) {
                value.setDoc(emb);
            } else {
                emb = new JsonObject();
                value.setDoc(emb);
                proxy.getDoc().put(key, emb);
            }
            return value;
        }

        @Override
        protected void setToJson(Embedded proxy, T value) {
            throw new RuntimeException("embedded can not be assigned");
        }
    }

    public static class EntityInterceptor<T extends Entity> extends Interceptor<T> {
        @SuppressWarnings("unchecked")
        @Override
        protected T getFromJson(Embedded proxy) {
            if (proxy.getDoc().containsKey(key + "_")) {
                T value = proxy.getEntityManager().create((Class<T>) cls);
                value.setDoc(proxy.getDoc().getJsonObject(key + "_"));
                return value;
            }
            throw new RuntimeException("entity can not be read from document json " + key + ", must be joined");
        }

        @Override
        protected void setToJson(Embedded proxy, T value) {
            proxy.getDoc().put(key, value.getId());
        }
    }
/*
    public static class EmbeddedListInterceptor<T extends Embedded> extends ReadonlyInterceptor<List<?>> {
        @Override
        protected List<?> getFromJson(Embedded proxy) {
            //System.out.println("get key:" + key);
            JsonArray list = proxy.getDoc().getJsonArray(key);
            if (list == null) {
                list = Json.createArrayBuilder().build();
                proxy.getDoc().put(key, list);
            }

            JsonArray list$ =list;
            List<Object> ret = new ArrayList<Object>() {

                private static final long serialVersionUID = 1L;

                @Override
                public boolean add(Object e) {
                    list$.add(((Embedded) e).getDoc());
                    return super.add(e);
                }

            };

            for (JsonValue val : list) {
                T value = proxy.getEntityManager().create(classFor(actualType));
                value.setDoc(val.asJsonObject());
                ret.add(value);
            }
            return ret;
        }
    }
*/
    public static class EmbeddedListInterceptor extends ReadonlyInterceptor<Orm.EmbeddedList<?>> {
        @Override
        protected Orm.EmbeddedList<?> getFromJson(Embedded proxy) {
            if (actualType.getTypeName().equals("java.lang.String")) {
                return Orm.factory.makeEmbeddedListValue(proxy, key);
            } else {
                return Orm.factory.makeEmbeddedListRef(proxy, key, classFor(actualType));
            }
        }
    }

    public static class EmbeddedMapInterceptor extends ReadonlyInterceptor<Orm.EmbeddedMap<?>> {
        @Override
        protected Orm.EmbeddedMap<?> getFromJson(Embedded proxy) {
            if (actualType.getTypeName().equals("java.lang.String")) {
                return Orm.factory.makeEmbeddedMapValue(proxy, key);
            } else {
                return Orm.factory.makeEmbeddedMapRef(proxy, key, classFor(actualType));
            }
        }
    }

    public static class RelHasOneInterceptor extends ReadonlyInterceptor<Orm.HasOne<?>> {
        @Override
        protected Orm.HasOne<?> getFromJson(Embedded proxy) {
            return Orm.factory.makeHasOne(proxy, key, classFor(actualType), (Orm.HasOne.Def) relation);
        }
    }

    public static class RelHasManyInterceptor extends ReadonlyInterceptor<Orm.HasMany<?>> {
        @Override
        protected Orm.HasMany<?> getFromJson(Embedded proxy) {
            return Orm.factory.makeHasMany((Entity) proxy, key, classFor(actualType), (Orm.HasMany.Def) relation);
        }
    }

    public static class RelBelongsToInterceptor extends ReadonlyInterceptor<Orm.BelongsTo<?>> {
        @Override
        protected Orm.BelongsTo<?> getFromJson(Embedded proxy) {
            return Orm.factory.makeBelongsTo(proxy, key, classFor(actualType), (Orm.BelongsTo.Def) relation);
        }
    }

    public static class RelHasManyEmbeddedInterceptor extends ReadonlyInterceptor<Orm.HasManyEmbedded<?>> {
        @Override
        protected Orm.HasManyEmbedded<?> getFromJson(Embedded proxy) {
            return Orm.factory.makeHasManyEmbedded((Entity) proxy, key, classFor(actualType), (Orm.HasManyEmbedded.Def) relation);
        }
    }
}
