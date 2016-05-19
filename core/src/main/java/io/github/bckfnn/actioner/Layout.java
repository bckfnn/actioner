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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.rjeschke.txtmark.Configuration;
import com.github.rjeschke.txtmark.Processor;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

import io.github.bckfnn.taggersty.HtmlTags;
import io.github.bckfnn.taggersty.Tags;
import io.github.bckfnn.taggersty.vertx.VertxHtmlTags;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.web.RoutingContext;

/**
 * Base class for Layouts, classes that generates Html output.
 */
public abstract class Layout implements LayoutTemplate {
    public static final Logger log = LoggerFactory.getLogger(Layout.class);

    protected RoutingContext ctx;
    protected ActionRouter router;
    private Config translations;
    protected Handler<VertxHtmlTags> content;
    protected Handler<VertxHtmlTags> headContent;
    private boolean tablesorter;

    private String assets = "standard";

    //protected final HtmlBuilder g;
    //private final Writer writer;

    public Layout() {
    }

    public Layout ctx(RoutingContext ctx) {
        this.ctx = ctx;
        this.router = ctx.get(ActionRouter.class.getName());
        this.translations = ctx.get("translations");
        return this;
    }


    public Layout tablesorter(boolean tablesorter) {
        this.tablesorter = tablesorter;
        return this;
    }

    public boolean tablesorter() {
        return tablesorter;
    }

    public Layout assets(String assets) {
        this.assets = assets;
        return this;
    }

    public String assets() {
        return assets;
    }

    public Layout content(Handler<VertxHtmlTags> content) {
        this.content = content;
        return this;
    }

    public Layout headContent(Handler<VertxHtmlTags> headContent) {
        this.headContent = headContent;
        return this;
    }

    public void commonStyles(VertxHtmlTags g) {
        String contextRoot = ctx.get("contextRoot");
        if (tablesorter) {
            g.link("stylesheet", contextRoot + webjar("tablesorter", "css/theme.bootstrap.css"));
        }
    }

    public void commonScripts(HtmlTags g) {
        String contextRoot = ctx.get("contextRoot");
        if (tablesorter) {
            g.script("src", contextRoot + webjar("tablesorter", "js/jquery.tablesorter.min.js"));
            g.script("src", contextRoot + webjar("tablesorter", "js/jquery.tablesorter.widgets.js"));
        }
    }

    public static  <T extends Layout> void render(RoutingContext ctx, T layout, BiConsumer<T, VertxHtmlTags> tmpl, Handler<T> handler) {
        handler.handle(layout);
        layout.ctx(ctx);
        layout.content(g -> tmpl.accept(layout, g));
        ctx.put("template", layout);
        ctx.next();
    }


    @Override
    public abstract void layout(VertxHtmlTags htmlWriter);

    static String[] folders = { "org.webjars", "org.webjars.npm", "org.webjars.bower"  };

    public String webjar(String artifact, String file) {
        try {
            for (String folder : folders) {
                String propFilename = "META-INF/maven/" + folder + "/" + artifact + "/pom.properties";
                Enumeration<URL> urls = getClass().getClassLoader().getResources(propFilename);
                if (!urls.hasMoreElements()) {
                    continue;
                }
                URL url = urls.nextElement();
                Properties props = new Properties();
                try (InputStream is = url.openStream()) {
                    props.load(is);
                }
                /*
                while (urls.hasMoreElements()) {
                    log.warn("extra webjar artifact found as {} and {}", urls.nextElement(), url);
                }
                */
                String version = props.getProperty("version");
                return "/webjars/" + artifact + "/" + version + "/" + file;
            }
        } catch (IOException e) {
            throw Utils.rethrow(e);
        }
        log.error("missing webjar artifact {} searched as {}", artifact, Arrays.asList(folders));
        return "/webjars/" + artifact + "/v0/" + file;
    }


    public String label(String key) {
        if (translations.hasPath(key)) {
            return translations.getString(key);
        }
        return "??" + key + "??";
    }

    public boolean hasLabel(String key) {
        return translations.hasPath(key);
    }

    public Config translations() {
        return translations;
    }

    public String markdown(String s) {
        if (s == null) {
            return null;
        }
        Configuration conf = Configuration.builder().forceExtentedProfile().build();
        return Processor.process(Utils.undent(s), conf);
    }

    public String link(String action) {
        return link(action, Utils.map());
    }

    public String link(String action, String id) {
        return link(action, Utils.map("id", id));
    }

    public String link(String action, Map<String, String> map) {
        Action act = router.getAction(action);
        if (act == null) {
            log.error("unknown action {}", action);
            return "#";
        }
        return link(act, map);
    }

    public String link(Action action) {
        return link(action, Utils.map());
    }

    public String link(Action action, String id) {
        return link(action, Utils.map("id", id));
    }

    public String link(Action action, Map<String, String> map) {
        Objects.requireNonNull(action, "action");

        String url = action.url();
        MultiMap requestMap = ctx.request().params();
        for (String grp : action.groups()) {

            String v = null;
            if (map.containsKey(grp)) {
                v = String.valueOf(map.get(grp));
            }
            if (v == null) {
                v = requestMap.get(grp);
            }
            if (v == null) {
                v = ctx.get(grp);
            }

            if (v == null) {
                throw new RuntimeException("Missing url replacement group " + grp + " for action " + action.name());
            }
            String val = v;
            url = url.replace(":" + grp, Utils.val(() -> URLEncoder.encode(val, "UTF-8")));
        }
        return url;
    }

    public String dateMedium(Date date) {
        if (date == null) {
            return "";
        }
        String format = translations.getString("dateformat.medium");
        return new SimpleDateFormat(format).format(date);
    }

    public String dateFull(Date date) {
        if (date == null) {
            return "";
        }
        String format = translations.getString("dateformat.full");
        return new SimpleDateFormat(format).format(date);
    }

    public static final List<Long> times = Arrays.asList(
            TimeUnit.DAYS.toMillis(365),
            TimeUnit.DAYS.toMillis(30),
            TimeUnit.DAYS.toMillis(1),
            TimeUnit.HOURS.toMillis(1),
            TimeUnit.MINUTES.toMillis(1),
            TimeUnit.SECONDS.toMillis(1) );
    public static final List<String> timesString = Arrays.asList("year","month","day","hour","minute","second");

    public String timeAgo(long duration) {

        StringBuffer res = new StringBuffer();
        for (int i = 0; i < times.size(); i++) {
            Long current = times.get(i);
            long temp = duration / current;
            if (temp > 0) {
                res.append(temp).append(" ").append(timesString.get(i)).append(temp > 1 ? "s" : "").append(" ago");
                break;
            }
        }
        if ("".equals(res.toString())) {
            return "0 second ago";
        } else {
            return res.toString();
        }
    }


    public String filesize(long size) {
        String format = translations.getString("numberformat.integer");
        return new DecimalFormat(format).format(size);
    }

    public void filesize(HtmlTags g, long size) {
        g.span("title", filesize(size), () -> {
            g.text(humanReadableByteCount(size, true));
        });
    }

    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public void fileicon(HtmlTags g, String filename) {
        String ext = Utils.extension(filename, '.');
        String type = "o";
        switch (ext) {
        case "pdf":
            type = "pdf-o";
            break;
        case "xls":
        case "xlsx":
            type = "excel-o";
            break;
        case "doc":
        case "docx":
            type = "word-o";
            break;
        case "zip":
        case "rar":
        case "jar":
            type = "archive-o";
            break;
        case "png":
        case "jpg":
        case "gif":
            type = "image-o";
            break;
        case "txt":
            type = "text-o";
            break;
        }
        g.i("class", "fa fa-file-" + type, () -> g.text(""));
    }

    public <T> ViewData<T> view(Collection<T> data, String attr, String value) {
        return view(data, Utils.map(attr, value));
    }

    public <T> ViewData<T> view(Stream<T> data, String attr, String value) {
        return view(data, Utils.map(attr, value));
    }


    public <T> ViewData<T> view(Collection<T> data, Map<String, String> maps) {
        return view(data.stream(), maps);
    }

    public <T> ViewData<T> view(Stream<T> data, Map<String, String> maps) {
        return new ViewData<T>(new StreamReadStream<T>(data), maps);
    }

    public <T> ViewData<T> view(ReadStream<T> data, String attr, String value) {
        return view(data, Utils.map(attr, value));
    }

    public <T> ViewData<T> view(ReadStream<T> data, Map<String, String> maps) {
        return new ViewData<T>(data, maps);
    }

    public class StreamReadStream<T> implements ReadStream<T> {
        private Stream<T> stream;
        private Handler<Void> endHandler;

        public StreamReadStream(Stream<T> stream) {
            this.stream = stream;
        }

        @Override
        public ReadStream<T> exceptionHandler(Handler<Throwable> exceptionHandler) {
            return this;
        }

        @Override
        public ReadStream<T> handler(Handler<T> dataHandler) {
            stream.forEach(item -> {
                dataHandler.handle(item);
            });
            if (endHandler != null) {
                endHandler.handle(null);
            }
            return this;
        }


        @Override
        public ReadStream<T> pause() {
            return this;
        }

        @Override
        public ReadStream<T> resume() {
            return this;
        }

        @Override
        public ReadStream<T> endHandler(Handler<Void> endHandler) {
            this.endHandler = endHandler;
            return this;
        }
    }

    public class ViewData<T> {
        private Map<String, String> map;
        private List<Column<T>> columns = new ArrayList<>();
        private ReadStream<T> data;

        public ViewData(ReadStream<T> data, Map<String, String> map) {
            this.data = data;
            this.map = map;
        }

        public ViewData<T> gen(VertxHtmlTags g, Handler<ViewData<T>> action) {
            action.handle(this);
            render(g);
            return this;
        }

        public Column<T> column(String name) {
            return column(name, true);
        }

        public Column<T> column(String name, boolean sort) {
            return column(name, true, 0);
        }

        public Column<T> column(String name, boolean sort, int width) {
            Column<T> col = new Column<T>(name, sort, width);
            columns.add(col);
            return col;
        }

        public void render(VertxHtmlTags g) {
            g.table("id", map.get("id"), "class", "table table-striped table-bordered table-condensed", () -> {
                for (Column<T> col : columns) {
                    if (col.getWidth() != 0) {
                        g.col("style", "width:" + String.valueOf(col.getWidth()) + "px");
                    } else {
                        g.col();
                    }
                }
                g.thead(() -> {
                    g.tr(() -> {
                        for (Column<T> col : columns) {
                            g.th(() -> {
                                col.render(g);
                            });
                        }
                    });
                });
                g.tbody(() -> {
                    g.forEach(data, (g2, item) -> {
                        g2.tr(() -> {
                            for (Column<T> col : columns) {
                                g2.td(() -> {
                                    col.getHandler().accept(g2, item);
                                });
                            }
                        });
                    });
                });
            });

            if (tablesorter && map.get("nosort") == null) {
                g.script(() -> {
                    g.textUnescaped("$(function(){ $('#" + map.get("id") + "').tablesorter({theme : 'bootstrap', headerTemplate : '{content} {icon}', widgets : [ 'uitheme' ]}); });");
                });
            }
        }
    }

    public class Column<T> {
        private String name;
        @SuppressWarnings("unused")
        private boolean sortable;
        private int width;
        private BiConsumer<VertxHtmlTags, T> handler;
        private Handler<Void> header;

        public Column(String name, boolean sort, int width) {
            this.name = name;
            this.sortable = sort;
            this.width = width;
        }

        public String getName() {
            return name;
        }

        public int getWidth() {
            return width;
        }


        public BiConsumer<VertxHtmlTags, T> getHandler() {
            return handler;
        }

        public Column<T> gen( BiConsumer<VertxHtmlTags, T> handler) {
            this.handler = handler;
            return this;
        }

        public Column<T> head(Handler<Void> header) {
            this.header = header;
            return this;
        }

        public void render(VertxHtmlTags g) {
            if (header == null) {
                if (getName() != null) {
                    g.text(label("view." + getName()));
                } else {
                    g.text("");
                }
            } else {
                header.handle(null);
            }
        }
    }

    public static <T> FormLayout<T> form(String id,  Action2<FormLayout<T>.Data, T> handler) {
        FormLayout<T> f = new FormLayout<>(id, handler);
        return f;
    }


    public static class FormLayout<T> {
        private final String id;
        Action2<FormLayout<T>.Data, T> handler;
        public String enctype = "application/x-www-form-urlencoded";

        FormLayout(String id, Action2<FormLayout<T>.Data, T> handler) {
            this.id = id;
            this.handler = handler;
        }




        public void render(T entity, State state, Action action, Layout layout, HtmlTags g) {
            Data data = new Data(state, layout, g);

            if (isFormReadOnly(state)) {
                g.div("class", "Xform-horizontal well", () -> {
                    handler.run(data, entity);
                });
            } else {
                g.form("role", "form", "class", "Xform-horizontal", () -> {
                    g.attr("id", id);
                    g.attr("action", layout.link(action));
                    g.attr("method", "POST");
                    g.attr("enctype", enctype);
                    handler.run(data, entity);
                });
            }
        }

        private boolean isFormReadOnly(State state) {
            return state == State.READONLY;
        }

        public class Data {
            private State state;
            private Layout layout;
            private HtmlTags g;

            public Data(State state, Layout layout, HtmlTags g) {
                this.state = state;
                this.layout = layout;
                this.g = g;
            }

            public TextField text(String name) {
                TextField f = new TextField(name);
                return f;
            }

            public PasswordField password(String name) {
                PasswordField f = new PasswordField(name);
                return f;
            }

            public TextAreaField textarea(String name) {
                TextAreaField f = new TextAreaField(name);
                return f;
            }

            public TextField file(String name) {
                TextField f = new TextField(name);
                return f;
            }

            public DateField date(String name) {
                DateField f = new DateField(name);
                return f;
            }

            public SelectField select(String name, Config values) {
                SelectField f = new SelectField(name, values);
                return f;
            }

            public RadioField radio(String name, Map<String, Object> values) {
                RadioField f = new RadioField(name, values);
                return f;
            }

            public boolean isFormReadOnly() {
                return state == State.READONLY;
            }

            public abstract class Field<R> {
                protected final String name;
                protected int size = 10;

                public Getter<R> getter;
                public Setter<R> setter;

                public Field(String name) {
                    this.name = name;
                }

                public abstract void render();

                public boolean isReadOnly() {
                    return false;
                }

                public Field<R> size(int size) {
                    this.size = size;
                    return this;
                }

                public Field<R> get(Getter<R> getter) {
                    this.getter = getter;
                    return this;
                }

                public Field<R> set(Setter<R> setter) {
                    this.setter = setter;
                    return this;
                }

                public R getValue() {
                    if (state != State.NEW && getter != null) {
                        return getter.get();
                    }
                    return null;
                }

                public void setValue(R value) {
                    if (setter != null) {
                        setter.set(value);
                    }
                }

                public boolean readOnly() {
                    return isFormReadOnly() || isReadOnly();
                }

                public void placeholder(String name) {
                    if (layout.hasLabel("form." + name + ".place")) {
                        g.attr("placeholder", layout.label("form." + name + ".place"));
                    }
                }

                public void renderField(String value, Tags.Generator gen) {
                        g.div("class", "form-group", () -> {
                            g.label("class", "Xcol-xs-2 control-label", () -> {
                                if (!readOnly()) {
                                    g.attr("for", name);
                                }
                                g.text(layout.label("form." + name + ".label"));
                            });

                            g.div("class", "Xcol-xs-" + size, () -> {
                                if (readOnly()) {
                                    g.p("class", "form-control-static", () -> g.text(value));
                                } else {
                                    gen.gen();
                                    if (layout.hasLabel("form." + name + ".help")) {
                                        g.span("id", name + ".helpBlock", "class", "helpBlock", () -> {
                                            g.text(layout.label("form." + name + ".help"));
                                        });
                                    }
                                }
                            });
                        });
                }
            }

            public class TextField extends Field<String>  {
                public TextField(String name) {
                    super(name);
                }

                @Override
                public void render() {
                    Object value = getValue();
                    String v = value != null ? String.valueOf(value) : "";
                    renderField(v, () -> {
                        g.input("type", "text", "class", "form-control", () -> {
                            g.attr("name", name);
                            g.id(name);
                            g.attr("value", v);
                            placeholder(name);
                        });
                    });
                }
            }

            public class PasswordField extends Field<String>  {
                public PasswordField(String name) {
                    super(name);
                }

                @Override
                public void render() {
                    Object value = getValue();
                    String v = value != null ? String.valueOf(value) : "";
                    renderField(v, () -> {
                        g.input("type", "password", "class", "form-control", () -> {
                            g.attr("name", name);
                            g.id(name);
                            g.attr("value", v);
                            placeholder(name);
                        });
                    });
                }
            }

            public class TextAreaField extends Field<String>  {
                public TextAreaField(String name) {
                    super(name);
                }

                @Override
                public void render() {
                    Object value = getValue();
                    System.out.println(getter + " " + value);
                    String v = value != null ? String.valueOf(value) : "";
                    renderField(v, () -> {
                        g.textarea(Tags._suppress, null, "class", "form-control", () -> {
                            g.attr("name", name);
                            g.id(name);

                            placeholder(name);
                        });
                    });
                }
            }

            public class FileField extends Field<Object> {
                public FileField(String name) {
                    super(name);
                }

                @Override
                public void render() {
                    renderField(null, () -> {
                        g.input("type", "file", () -> {
                            g.attr("name", name);
                            g.id(name);
                            placeholder(name);
                        });
                    });
                }
            }

            public class DateField extends Field<Date>  {
                public DateField(String name) {
                    super(name);
                }

                @Override
                public void render() {
                    Object value = getValue();
                    String v = value != null ? String.valueOf(value) : "";
                    System.out.println(name + " " + value + " " + v);
                    renderField(v, () -> {
                        g.div("class", "input-group date form_datetime", "id", name, () -> {

                            g.input("type", "text", "class", "form-control", () -> {
                                g.attr("name", name);
                                //g.id(name);
                                g.attr("value", v);
                                //placeholder(name);
                            });
                            g.span("class", "input-group-addon", () -> {
                                g.span("class", "fa fa-calendar", () -> {});
                            });
                            g.script(() -> {
                                g.textUnescaped("$(function() {$('#" + name + "').datetimepicker({format: 'DD/MM/YYYY'});});");
                            });
                        });

                    });
                }
            }


            public class SelectField extends Field<String>  {
                private Config values;

                public SelectField(String name, Config values) {
                    super(name);
                    this.values = values;
                }

                @Override
                public void render() {
                    Object value = getValue();
                    String v = value != null ? String.valueOf(value) : "";
                    renderField(v, () -> {
                        g.select("class", "form-control", () -> {
                            g.attr("name", name);
                            g.id(name);
                            for (Map.Entry<String, ConfigValue> o : values.entrySet()) {
                                g.option("value", o.getKey(), () -> {
                                    g.text(o.getValue().unwrapped().toString());
                                });
                            }
                            g.attr("value", v);
                            placeholder(name);
                        });
                    });
                }
            }

            public class RadioField extends Field<String>  {
                private Map<String, Object> values;

                public RadioField(String name, Map<String, Object> values) {
                    super(name);
                    this.values = values;
                }

                @Override
                public void render() {
                    Object value = getValue();
                    String v = value != null ? String.valueOf(value) : "";
                    renderField(v, () -> {
                        for (Map.Entry<String, Object> o : values.entrySet()) {
                            g.input("type", "radio", () -> {
                                g.attr("name", name);
                                g.id(name);
                            });
                            g.label("for", name, () -> {
                                g.text(layout.label("form." + name + "." + o.getKey()));
                            });
                        }
                    });
                }
            }
        }
    }

    public static interface Handler3<A, B, C> {
        void handle(A arg1, B arg2, C arg3);
    }

    public static interface Getter<T> {
        T get();
    }

    public static interface Setter<T> {
        void set(T value);
    }



    public enum State {
        READONLY,
        EDIT,
        NEW
    }




    public interface Action2<T1, T2> {
        public void run(T1 a, T2 b);
    }
}