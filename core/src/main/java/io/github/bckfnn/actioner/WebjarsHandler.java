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

import java.io.InputStream;
import java.text.DateFormat;
import java.util.Date;

import javax.activation.MimetypesFileTypeMap;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

/**
 * Handler for serving data from a webjar.
 */
public class WebjarsHandler implements Handler<RoutingContext> {
    private final DateFormat dateTimeFormatter = io.vertx.ext.web.impl.Utils.createRFC1123DateTimeFormatter();

    private Date lastReboot = new Date(System.currentTimeMillis());
    private String lastRebootStr = dateTimeFormatter.format(lastReboot);

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

        InputStream stream = loadResource(path);
        if (stream != null) {
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
                ctx.response().end(Buffer.factory.buffer(Utils.readAsBytes(stream)));
            } catch (Exception e) {
                Utils.rethrow(e);
            }
        } else {
            ctx.next();
        }
    }

    public WebjarsHandler setCachingEnabled(boolean enabled) {
        this.cachingEnabled = enabled;
        return this;
    }

    private InputStream loadResource(String path) {
        return getClass().getResourceAsStream("/META-INF/resources/webjars/" + path);
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
