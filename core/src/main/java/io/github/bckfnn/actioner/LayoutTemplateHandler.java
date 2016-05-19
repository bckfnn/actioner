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

import io.github.bckfnn.taggersty.StandardFilter;
import io.github.bckfnn.taggersty.vertx.VertxHtmlTags;
import io.github.bckfnn.taggersty.vertx.VertxHtmlTags.VertxOutput;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;

public class LayoutTemplateHandler implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext ctx) {
        LayoutTemplate template = ctx.get("template");
        if (template == null) {
            ctx.next();
            return;
        }
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/html");
        ctx.response().setChunked(true);

        VertxOutput out = new VertxOutput(ctx.response());
        out.endHandler($ -> {
            System.out.println("end");
            ctx.response().end();
        });
        VertxHtmlTags htmlWriter = new VertxHtmlTags(out);
        htmlWriter.filter(new StandardFilter());

        htmlWriter.setAutoIndent(false);
        htmlWriter.setAutoIndent(true);
        template.layout(htmlWriter);
        htmlWriter.close();
    }
}
