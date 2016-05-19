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
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.activation.MimetypesFileTypeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

/**
 * Handler for serving data from a webjar.
 */
public class AssetsHandler implements Handler<RoutingContext> {
    public static final Logger log = LoggerFactory.getLogger(AssetsHandler.class);

    private final DateFormat dateTimeFormatter = io.vertx.ext.web.impl.Utils.createRFC1123DateTimeFormatter();

    private Date lastReboot = new Date(System.currentTimeMillis());
    private String lastRebootStr = dateTimeFormatter.format(lastReboot);
    Config assets = ConfigFactory.load("assets");

    /**
     * Default max age for cache headers
     */
    static final long DEFAULT_MAX_AGE_SECONDS = 86400; // One day

    private boolean cachingEnabled = true;
    private long maxAgeSeconds = DEFAULT_MAX_AGE_SECONDS; // One day

    @Override
    public void handle(RoutingContext ctx) {
        String path = ctx.normalisedPath();

        String prefix = ctx.currentRoute().getPath();
        path = path.substring(prefix.length());

        int dot = path.indexOf('.');
        String artifact = path.substring(0, dot);
        String type = path.substring(dot + 1);

        if (assets.hasPath(artifact)) {
            byte[] data;
            try {
                data = make(ctx, artifact, type);
            } catch (Exception e) {
                ctx.fail(e);
                return;
            }

            MultiMap headers = ctx.response().headers();

            headers.set(HttpHeaders.CONTENT_TYPE, MimetypesFileTypeMap.getDefaultFileTypeMap().getContentType(path));
            if (cachingEnabled) {
                // We use cache-control and last-modified
                // We *do not use* etags and expires (since they do the same thing - redundant)
                headers.set("cache-control", "public, max-age=" + maxAgeSeconds);
                headers.set("last-modified", lastRebootStr);
            }

            try {
                if (cachingEnabled) {
                    if (shouldUseCached(ctx.request())) {
                        ctx.response().setStatusCode(304).end();
                        return;
                    }
                }
                ctx.response().end(Buffer.factory.buffer(data));
            } catch (Exception e) {
                Utils.rethrow(e);
            }
        } else {
            ctx.next();
        }
    }

    public byte[] make(RoutingContext ctx, String target, String type) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.out.println("target " + target + " " + type);
        for (Config v : assets.getConfigList(target)) {
            //System.out.println(v);
            if (v.hasPath("webjar")) {
                String artifact = v.getString("webjar");
                if (v.hasPath(type)) {
                    for (String path : v.getStringList(type)) {
                        //System.out.println(webjar(artifact, path));
                        String file = webjar(artifact, path);
                        URL url = getClass().getResource("/META-INF/resources" + file);
                        if (ctx.request().getParam("raw") != null) {
                            file = "." + file;
                        }

                        if (type.equals("css")) {
                            String input = Utils.readAsString(url.openStream(), "UTF-8");
                            out.write(filterUrl(file, input).getBytes("UTF-8"));
                        } else {
                            byte[] b=Utils.readAsBytes(url.openStream());
                            //System.out.println(webjar(artifact, path) + " " + b.length);
                            out.write(b);
                        }
                        out.write('\r');
                        out.write('\n');
                    }
                }
            } else if (v.hasPath("public")) {
                if (v.hasPath(type)) {
                    for (String path : v.getStringList(type)) {
                        //System.out.println(path);
                        URL url = getClass().getResource(path);
                        if (ctx.request().getParam("raw") != null) {
                            path = "." + path;
                        }
                        if (type.equals("css")) {
                            String input = Utils.readAsString(url.openStream(), "UTF-8");
                            out.write(filterUrl(path, input).getBytes("UTF-8"));
                        } else {
                            byte[] b=Utils.readAsBytes(url.openStream());
                            //System.out.println(path + " " + b.length);
                            out.write(b);
                            //Utils.copy(url.openStream(), out);
                        }
                        out.write('\r');
                        out.write('\n');
                    }
                }
            }
        }
        return out.toByteArray();
    }

    public String filterUrl(String file, String input) {
        //Pattern url = Pattern.compile("url\\(\\\"(.+?)\\\"\\)");
        String s = "url\\([\"']?(.+?)[\"']?\\)";

        Pattern url = Pattern.compile(s);
        StringBuffer sb = new StringBuffer();
        Matcher m = url.matcher(input);
        while (m.find()) {
            String v = m.group(1);
            if (v.startsWith("data:") || v.startsWith("/")) {
                m.appendReplacement(sb, "url(\"" + v + "\")");
            } else {
                //System.out.println("replace url " + m.group(1));
                m.appendReplacement(sb, "url(\"" + file + "/../" + m.group(1) + "\")");
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

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
                String version = props.getProperty("version");
                return "/webjars/" + artifact + "/" + version + "/" + file;
            }
        } catch (IOException e) {
            throw Utils.rethrow(e);
        }
        log.error("missing webjar artifact {} searched as {}", artifact, Arrays.asList(folders));
        return "/webjars/" + artifact + "/v0/" + file;
    }

    public AssetsHandler setCachingEnabled(boolean enabled) {
        this.cachingEnabled = enabled;
        return this;
    }


    // return true if there are conditional headers present and they match what is in the entry
    boolean shouldUseCached(HttpServerRequest request) throws Exception {
        String ifModifiedSince = request.headers().get("if-modified-since");
        if (ifModifiedSince == null) {
            // Not a conditional request
            return false;
        }
        Date ifModifiedSinceDate = dateTimeFormatter.parse(ifModifiedSince);
        boolean modifiedSince = lastReboot.getTime() > ifModifiedSinceDate.getTime();
        return !modifiedSince;
    }
}
