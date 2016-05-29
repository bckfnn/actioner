/*
 * Copyright 2015 [inno:vasion]
 */

package io.github.bckfnn.persist.couch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonObject;

public class Operation<T> {
    private final static Logger log = LoggerFactory.getLogger(Operation.class);

    private final String method;
    private String uri;
    private Object body;
    private Class<T> responseClass;

    public Operation(String method) {
        this.method = method;
    }

    public Operation(String method, String uri, Object body, Class<T> responseClass) {
        this.method = method;
        this.uri = uri;
        this.body = body;
        this.responseClass = responseClass;
    }


    public String getMethod() {
        return method;
    }

    public String getUri() {
        return uri;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    public Class<T> XgetResponseClass() {
        return responseClass;
    }


    public void sendRequest(HttpClientRequest r, Vertx vertx) throws Exception {
        if (getBody() != null) {
            Buffer b = Buffer.factory.buffer(getBody().toString());
            r.end(b);
        } else {
            r.end();
        }
    }

    @SuppressWarnings("unchecked")
    public void handleResponse(HttpClientResponse resp, Handler<AsyncResult<T>> handler)  {
        resp.bodyHandler(body -> {
            log.debug("persistor resp {} {}", resp.statusCode(), body);
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                try {
                    JsonObject result = new JsonObject(body.toString("UTF-8"));
                    //                    result = mapper..readValue(body.getBytes(), getResponseClass());
                    //System.out.println(result.getClass() + " " + result);
                    handler.handle(Future.succeededFuture((T) result));
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            } else {
                log.error("persistor error {} {}", resp.statusCode(), resp.statusMessage());
                String msg = "" + resp.statusCode() + " " + new String(body.getBytes());
                msg = msg.replace('\n', ' ').replace('\r', ' ');
                handler.handle(Future.failedFuture(new Exception(msg)));
            }
        });
        resp.exceptionHandler(e -> {
            //log.debug("http exc", e);
        });
    }

    @Override
    public String toString() {
        return method + " " + uri + " " + body;
    }
}
