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
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;

import com.codahale.metrics.MetricRegistry;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.exporter.common.TextFormat;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;

public class PrometheusMetricsHandler implements Handler<RoutingContext> {
    private final MetricRegistry registry;

    public PrometheusMetricsHandler(MetricRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void handle(RoutingContext ctx) {
        ctx.response().setStatusCode(200);
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain; version=0.0.4; charset=utf-8");
        List<MetricFamilySamples> metrics = new DropwizardExports(registry).collect();


        StringWriter writer = new StringWriter();
        try {
            TextFormat.write004(writer, Collections.enumeration(metrics));
            ctx.response().end(writer.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
