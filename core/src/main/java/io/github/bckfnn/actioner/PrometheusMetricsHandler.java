package io.github.bckfnn.actioner;


import java.io.StringWriter;
import java.util.Map;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;

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
        StringWriter writer = new StringWriter();
        try {

            long now = System.currentTimeMillis();
            for (Map.Entry<String, Counter> counter : registry.getCounters().entrySet()) {
                //writer.write("# HELP " + counter.getKey() + " xx\n");
                writer.write("# TYPE " + counter.getKey() + " counter\n");
                writer.write(counter.getKey() + " " + counter.getValue().getCount() + " " + now + "\n");
            };
            for (Map.Entry<String, Gauge> gauge : registry.getGauges().entrySet()) {
                //writer.write("# HELP " + counter.getKey() + " xx\n");
                writer.write("# TYPE " + gauge.getKey() + " counter\n");
                writer.write(gauge.getKey() + " " + gauge.getValue().getValue() + " " + now + "\n");
            };

            for (Map.Entry<String, Histogram> histogram : registry.getHistograms().entrySet()) {
                //writer.write("# HELP " + counter.getKey() + " xx\n");
                String name = name(histogram.getKey());
                writer.write("# TYPE " + name + " histogram\n");
                Snapshot snap = histogram.getValue().getSnapshot();
                writer.write(name + "_bucket{le=\"0.001\"} " + snap.get999thPercentile()  + " " + now + "\n");
                writer.write(name + "_bucket{le=\"0.01\"} " + snap.get99thPercentile()  + " " + now + "\n");
                writer.write(name + "_bucket{le=\"0.02\"} " + snap.get98thPercentile()  + " " + now + "\n");
                writer.write(name + "_bucket{le=\"0.05\"} " + snap.get95thPercentile() + " " + now + "\n");
                writer.write(name + "_bucket{le=\"0.25\"} " + snap.get75thPercentile()  + " " + now + "\n");
                writer.write(name + "_bucket{le=\"+Inf\"} " + histogram.getValue().getCount()  + " " + now + "\n");

                writer.write(name + "_count " + histogram.getValue().getCount() + " " + now + "\n");
            };

            ctx.response().end(writer.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String name(String n) {
        return n.replace('.', '_').replace(':', '_').replace('-', '_');
    }
}
