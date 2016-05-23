package io.github.bckfnn.actioner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.impl.ClusterSerializable;
import io.vertx.ext.auth.AuthProvider;

public class DbUser implements io.vertx.ext.auth.User, ClusterSerializable {
    static Logger log = LoggerFactory.getLogger(Main.class);

    private JsonObject principal;
    private Config groups;

    public DbUser() {
    }

    public DbUser(JsonObject principal, Config groups) {
        this.principal = principal;
        this.groups = groups;
    }

    @Override
    public io.vertx.ext.auth.User isAuthorised(String authority, Handler<AsyncResult<Boolean>> resultHandler) {
        JsonArray groups = principal.getJsonArray("groups");
        //log.info("isAuthorized:" + authority + " in " + groups);
        for (int i = 0; i < groups.size(); i++) {
            //System.out.println("grp " + groups.getString(i) + " " + this.groups.getStringList(groups.getString(i)));
            if (this.groups.getStringList(groups.getString(i)).contains(authority)) {
                log.trace("Access to {} succeded for {}", authority, principal);
                resultHandler.handle(Future.succeededFuture(true));
                return this;
            }
        }
        log.debug("Access to {} failed for {}", authority, principal);
        resultHandler.handle(Future.succeededFuture(false));
        return this;
    }

    @Override
    public io.vertx.ext.auth.User clearCache() {
        log.info("clearCache");
        return this;
    }

    @Override
    public JsonObject principal() {
        return principal;
    }

    @Override
    public void setAuthProvider(AuthProvider authProvider) {
        groups = ((DbAuthProvider) authProvider).groups;
    }

    @Override
    public void writeToBuffer(Buffer buffer) {
        principal.writeToBuffer(buffer);
    }

    @Override
    public int readFromBuffer(int pos, Buffer buffer) {
        principal = new JsonObject();
        return principal.readFromBuffer(pos, buffer);
    }
}