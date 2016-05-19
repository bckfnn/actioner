/*
 * Copyright 2016 Finn Bock
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.bckfnn.actioner;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class Utils {
    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    public static void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buf = new byte[8 * 1024];
        for (int len; (len = inputStream.read(buf)) >= 0; ) {
            outputStream.write(buf, 0, len);
        }
    }

    public static void closeQuitly(Closeable closeable)  {
        try {
            closeable.close();
        } catch (IOException e) {
            log.warn("closeQuitely caugth an exception:"  + e);
        }
    }

    /**
     * Fully read an input file.
     * @param reader the reader
     * @return the file content.
     * @throws IOException when error.
     */
    public static String readFile(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[1024];
        for (int len; (len = reader.read(buffer)) > 0;) {
            sb.append(buffer, 0, len);
        }
        return sb.toString();
    }

    /**
     * Fully read an file.
     * @param file the file.
     * @return the file content.
     * @throws IOException when error.
     */
    public static String readFile(File file) throws IOException {
        try (InputStream is = new FileInputStream(file)) {
            return readAsString(is);
        }
    }

    public static String readAsString(InputStream is, String charset) throws IOException {
        StringWriter sw = new StringWriter();
        InputStreamReader reader = new InputStreamReader(is, charset);
        char[] buf = new char[1024*8];
        for (int len; (len = reader.read(buf)) >= 0; ) {
            sw.write(buf, 0, len);
        }
        return sw.toString();
    }


    public static String readAsString(InputStream is) throws IOException {
        StringWriter sw = new StringWriter();
        InputStreamReader reader = new InputStreamReader(is, "UTF-8");
        char[] buf = new char[1024*8];
        for (int len; (len = reader.read(buf)) >= 0; ) {
            sw.write(buf, 0, len);
        }
        return sw.toString();
    }

    public static byte[] readAsBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        byte[] buf = new byte[1024*8];
        for (int len; (len = is.read(buf)) >= 0; ) {
            baos.write(buf, 0, len);
        }
        return baos.toByteArray();
    }

    public static void rethrowOld(Throwable t) {
        if (t.getCause() != null) {
            t = t.getCause();
        }
        if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        }
        throw new RuntimeException(t);
    }

    public static RuntimeException rethrow(Throwable t) {
        if (t.getCause() != null) {
            t = t.getCause();
        }
        if (t instanceof RuntimeException) {
            return (RuntimeException) t;
        }
        return new RuntimeException(t);
    }

    public static String escapeXml(String orig) {
        return replace(orig, c -> {
            switch (c) {
            case '&':
                return "&amp;";
            case '<':
                return "&lt;";
            case '>':
                return "&gt;";
            case '"':
                return "&quot;";
            case '\'':
                return "&apos;";
            }
            return null;
        });
    };

    public static String escapeHtml(String orig) {
        return replace(orig, c -> {
            switch (c) {
            case '&':
                return "&amp;";
            case '<':
                return "&lt;";
            case '>':
                return "&gt;";
            case '"':
                return "&quot;";
            case '\'':
                return "&#39;";
            }
            return null;
        });
    };

    public static String replace(String orig, ReplaceFunc f) {
        if (orig == null) return orig;

        StringBuilder sb = null; // lazy create for edge-case efficiency
        for (int i = 0, len = orig.length(); i < len; i++) {
            final char ch = orig.charAt(i);
            final String replacement = f.subst(ch);

            if (replacement != null) {
                // output differs from input; we write to our local buffer
                if (sb == null) {
                    sb = new StringBuilder((int) (1.1 * len));
                    sb.append(orig.substring(0, i));
                }
                sb.append(replacement);
            } else if (sb != null) {
                // earlier output differs from input; we write to our local buffer
                sb.append(ch);
            }
        }

        return sb == null ? orig : sb.toString();
    }

    public interface ReplaceFunc {
        String subst(char c);
    }

    public static String undent(String s) {
        if (s == null) {
            return null;
        }
        String[] r = s.split("\n");

        if (r.length < 2 || !r[1].startsWith(" ")) {
            return s;
        }
        String l = r[1];
        int indent = count(l);
        StringBuilder sb = new StringBuilder();
        for (String line : r) {
            if (indent < line.length()) {
                sb.append(line.substring(indent));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static int count(String l) {
        for (int i = 0; i < l.length(); i++) {
            if (l.charAt(i) != ' ') {
                return i;
            }
        }
        return l.length();
    }

    /**
     * Return the extension part of a filename.
     * @param filename the filename.
     * @return the extension.
     */
    public static String extension(String filename) {
        return extension(filename, '.');
    }

    /**
     * Return the extension part of the filename that follows after the separator.
     * @param filename the filename.
     * @param separator the separator to search for.
     * @return the extension.
     */
    public static String extension(String filename, char separator) {
        if (filename == null) {
            return null;
        }
        int idx = filename.lastIndexOf(separator);
        if (idx >= 0) {
            return filename.substring(idx + 1);
        }
        return null;
    }

    /**
     * return the file part of the filename.
     * @param filename the filename
     * @param separator the path separator, '/' or '\'
     * @return the file part.
     */
    public static String filepart(String filename, char separator) {
        return extension(filename, separator);
    }

    /**
     * Split a string into parts. Uses a StringTokenizer.
     * @param str the input stream
     * @param separator the separator.
     * @return the list of string parts.
     */
    public static List<String> split(String str, String separator) {
        List<String> result = new ArrayList<String>();
        if (str == null) {
            return result;
        }
        StringTokenizer st = new StringTokenizer(str, separator);
        while (st.hasMoreTokens()) {
            result.add(st.nextToken());
        }
        return result;
    }

    /**
     * Join a list of elements into a string, using the separator.
     * @param list the list.
     * @param separator the separator.
     * @return the joined string.
     */
    public static String join(Collection<?> list, String separator) {
        StringBuilder sb = new StringBuilder();
        for (Object s : list) {
            if (sb.length() > 0) {
                sb.append(separator);
            }
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * Limit the length of the input string to len chars, adding '...' if the string is truncated.
     * @param str the input string.
     * @param len the max length.
     * @return the shorted string.
     */
    public static String limit(String str, int len) {
        if (str == null) {
            return "";
        }
        if (str.length() > len) {
            return str.substring(0, len) + "...";
        }
        return str;
    }

    /**
     * Return true if the input is not empty or null.
     * @param str input.
     * @return true when not empty.
     */
    public static boolean isNotEmpty(String str) {
        return str != null && str.length() > 0;
    }

    /**
     * Return true if the input is empty or null.
     * @param str input.
     * @return true when empty.
     */
    public static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }


    public static JsonObject json(Handler<JsonObjectBuilder> handler) {
        JsonObject json = new JsonObject();
        handler.handle(new JsonObjectBuilder(json));
        return json;
    }

    public static class JsonObjectBuilder {
        Stack<Object> stack = new Stack<>();


        public JsonObjectBuilder(JsonObject obj) {
            stack.push(obj);
        }

        public void put(String key, String value) {
            obj().put(key, value);
        }

        public void put(String key, int value) {
            obj().put(key, value);
        }

        public void put(String key, JsonObject value) {
            obj().put(key, value);
        }

        public void put(String key, JsonArray value) {
            obj().put(key, value);
        }


        public void add(String value) {
            arr().add(value);
        }


        public void add(int value) {
            arr().add(value);
        }

        public void add(JsonObject value) {
            arr().add(value);
        }

        public JsonObject jsonObject(JsonGenerator handler) {
            JsonObject json = new JsonObject();
            stack.push(json);
            handler.gen();
            stack.pop();
            return json;
        }

        public JsonArray jsonArray(JsonGenerator handler) {
            JsonArray json = new JsonArray();
            stack.push(json);
            handler.gen();
            stack.pop();
            return json;
        }

        private JsonObject obj() {
            return (JsonObject) stack.peek();
        }

        private JsonArray arr() {
            return (JsonArray) stack.peek();
        }
    }

    @FunctionalInterface
    public interface JsonGenerator {
        public void gen();
    }


    public static List<JsonObject> arr(JsonArray arr) {
        List<JsonObject> ret = new ArrayList<>();
        for (Object e : arr) {
            ret.add((JsonObject) e);
        }
        return ret;
    }


    public interface SupplierExc<T> {
        T get() throws Exception;
    }

    public static <T> T val(SupplierExc<T> func) {
        try {
            return func.get();
        } catch (Exception e) {
            //e.printStackTrace();
            throw rethrow(e);
        }
    }

    public static <K, V> Map<K, V> map() {
        Map<K, V> map = new HashMap<K, V>(0);
        return map;
    }

    public static <K, V> Map<K, V> map(K k1, V v1) {
        Map<K, V> map = new HashMap<K, V>(1);
        map.put(k1, v1);
        return map;
    }

    public static <K, V> Map<K, V> map(K k1, V v1, K k2, V v2) {
        Map<K, V> map = new HashMap<K, V>(2);
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    public static <K, V> Map<K, V> map(K k1, V v1, K k2, V v2, K k3, V v3) {
        Map<K, V> map = new HashMap<K, V>(3);
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        return map;
    }

    public static <K, V> Map<K, V> map(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        Map<K, V> map = new HashMap<K, V>(4);
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        return map;
    }

    public static <K, V> Map<K, V> map(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        Map<K, V> map = new HashMap<K, V>(5);
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        map.put(k5, v5);
        return map;
    }

    public static <K, V> Map<K, V> map(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6) {
        Map<K, V> map = new HashMap<K, V>(6);
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        map.put(k5, v5);
        map.put(k6, v6);
        return map;
    }
}
