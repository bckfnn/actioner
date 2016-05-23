package io.github.bckfnn.actioner;

import com.typesafe.config.Config;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;

public class DbAuthProvider implements AuthProvider {
    Main main;
    Config groups;

    public DbAuthProvider(Main main, Config groups) {
        this.main = main;
        this.groups = groups;
    }

    @Override
    public void authenticate(JsonObject authInfo, Handler<AsyncResult<io.vertx.ext.auth.User>> handler) {
        //log.info("authenticate {}", authInfo);
        String username = authInfo.getString("username");
        String password = authInfo.getString("password");

        main.loadPrincipal(username, password, result -> {
            if (result.failed()) {
                handler.handle(Future.failedFuture(result.cause()));
            } else {
                DbUser dbUser = new DbUser(result.result(), this.groups);
                dbUser.setAuthProvider(this);
    
                handler.handle(Future.succeededFuture(dbUser));
            }
        });
    }
}