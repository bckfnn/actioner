/*
 * Copyright 2014 [inno:vasion]
 */
package io.github.bckfnn.persist;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * An enum state with build-in history of changes.
 * @param <T> type of the enum.
 *
 * <pre>
 * {
 *     "state" : {
 *          "value" : "PUBLISHED",
 *          "history" : [
 *              {
 *                  "date" : 123456,
 *                  "state" : "CREATED"
 *              },
 *              {
 *                  "date" : 234567,
 *                  "state" : "VALIDATED"
 *              },
 *              {
 *                  "date" : 345678,
 *                  "state" : "PUBLISHED"
 *              }
 *          ]
 *      }
 * }
 * </pre>
 */
public class StateHistory<T extends Enum<T>> {
    private Class<T> cls;
    private JsonObject field;


    public StateHistory(Class<T> cls, JsonObject parent, String name) {
        this.cls = cls;
        field = parent.getJsonObject(name);
        if (field == null) {
            field = new JsonObject();
            parent.put(name, field);
        }
    }

    public T value() {
        String v = field.getString("value", null);
        if (v == null) {
            for (T t : cls.getEnumConstants()) {
                try {
                    if (t.getClass().getField(t.name()).getAnnotation(DocState.class).isDefault()) {
                        return t;
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return null;
        }
        return Enum.valueOf(cls, v);
    }

    public void set(T value) {
        field.put("value", value.name());

        JsonArray history = field.getJsonArray("history");

        if (history == null) {
            history = new JsonArray();
            field.put("history", history);
        }
        JsonObject entry = new JsonObject()
                .put("date", new Date().getTime())
                .put("state", value.name());
        history.add(entry);
    }

    public List<DateState<T>> getHistory() {
        List<DateState<T>> ret = new ArrayList<>();

        JsonArray history = field.getJsonArray("history");
        if (history == null) {
            return ret;
        }

        for (int i = 0; i < history.size(); i++) {
            JsonObject e = history.getJsonObject(i);

            ret.add(new DateState<T>(new Date(e.getLong("date")), Enum.valueOf(cls, e.getString("state"))));
        }
        return ret;
    }


    public static class DateState<T> {
        public final Date date;
        public final T state;

        public DateState(Date date, T state) {
            this.date = date;
            this.state = state;
        }

    }
}
