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

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;

/**
 * An Action is a Apex handler that will call the Invoker.invoke method when the request match the url and method
 * of the decorated handler.
 */
public class Action implements Handler<RoutingContext> {
    private static final Logger log = LoggerFactory.getLogger(Action.class);

    private String url;
    private int order;
    //private Route route;
    private Class<?> actionClass;
    private String name;
    private String permission;
    private List<String> groups;
    private String mainMenu;

    private Invoker invoker;

    public Action(Invoker invoker) {
        this.invoker = invoker;
    }

    public Action(A invoker) {
        this.invoker = invoker;
    }


    public void decorate(Router router, Class<?> actionClass, String name, String contextRoot, Get get) {
        this.actionClass = actionClass;
        this.name = name;
        this.url = contextRoot + get.url();
        this.permission = get.permission();
        this.order = get.order();
        init(router.get(url));
        init(router.head(url));
    }

    public void decorate(Router router, Class<?> actionClass, String name, String contextRoot, Post post) {
        this.actionClass = actionClass;
        this.name = name;
        this.url = contextRoot + post.url();
        this.permission = post.permission();
        this.order = post.order();
        init(router.post(url));
    }

    private void init(Route route) {
        this.groups = findGroups(url);
        if (Post.defaultPermission.equals(this.permission) || Get.defaultPermission.equals(this.permission)) {
            permission = name;
        }

        if (order != -1) {
            route.order(order);
        }
        MainMenu mainMenu = actionClass.getAnnotation(MainMenu.class);
        if (mainMenu != null) {
            this.mainMenu = mainMenu.name();
        }

        route.handler(this);
    }

    public String url() {
        return url;
    }

    public String name() {
        return name;
    }

    public String permission() {
        return permission;
    }

    public List<String> groups() {
        return groups;
    }

    public int order() {
        return order;
    }

    public String mainMenu() {
        return mainMenu;
    }

    public Class<?> actionClass() {
        return actionClass;
    }

    public void redirect(RoutingContext ctx) {
        redirect(ctx, Utils.map());
    }

    public void redirect(RoutingContext ctx,  Map<String, String> map) {
        String url = url();
        MultiMap requestMap = ctx.request().params();
        for (String grp : groups()) {
            String v = map.get(grp);
            if (v == null) {
                v = requestMap.get(grp);
            }
            if (v == null) {
                v = ctx.get(grp);
            }

            if (v == null) {
                throw new RuntimeException("Missing url replacement group " + grp);
            }
            String val = v;
            url = url.replace(":" + grp, Utils.val(() -> URLEncoder.encode(val, "UTF-8")));
        }
        ctx.response().setStatusCode(301);
        ctx.response().putHeader("Location", url);
        ctx.response().end();
    }

    private List<String> findGroups(String input) {
        Matcher m =  Pattern.compile(":([A-Za-z][A-Za-z0-9_]*)").matcher(input);

        List<String> groups = new  ArrayList<>();
        while (m.find()) {
            String group = m.group().substring(1);
            if (groups.contains(group)) {
                throw new IllegalArgumentException("Cannot use identifier " + group + " more than once in pattern string");
            }
            groups.add(group);
        }
        return groups;
    }

    @Override
    public void handle(RoutingContext ctx) {
        if (Utils.isEmpty(permission)) {
            invoke(ctx);
            return;
        }
        User user = ctx.user();

        Session session = ctx.session();
        if (session == null) {
            ctx.fail(new NullPointerException("No session - did you forget to include a SessionHandler?"));
        }
        if (user != null) { // TODO.isLoggedIn()) {
            // Already logged in, just authorise
            authorize(ctx);
        } else {
            Config config  = ctx.get(Config.class.getName());

            // Now redirect to the login url - we'll get redirected back here after successful login
            session.put(config.getString("returnURLParam"), ctx.request().path());
            ctx.response().putHeader("location", config.getString("loginPageURL")).setStatusCode(302).end();
        }
    }

    private void authorize(RoutingContext ctx) {
        User user = ctx.user();

        user.isAuthorised(permission, res -> {
            log.trace("has permission: {}, result ", permission + " " + res.result());

            if (res.succeeded()) {
                if (res.result()) {
                    invoke(ctx);
                } else {
                    ctx.fail(403);
                }
            } else {
                ctx.fail(res.cause());
            }
        });
    }

    private void invoke(RoutingContext ctx) {
        ctx.put(Action.class.getName(), this);
        ctx.put("actionName", name);

        ctx.response().putHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        ctx.response().putHeader("Pragma", "no-cache");
        ctx.response().putHeader("Expires", "0");
        try {
            String[] args = new String[groups.size()];
            for (int i = 0; i < groups.size(); i++) {
                String v = ctx.request().params().get(groups.get(i));
                if (v != null) {
                    args[i] = v;
                    ctx.request().params().set(groups.get(i), args[i]);
                }
            }
            invoker.invoke(ctx, args);
        } catch (Throwable t) {
            //t.printStackTrace();
            Utils.rethrow(t);
        }
    }
    public static interface Invoker {
        void invoke(RoutingContext ctx, String[] args) throws Exception;
    }

    public static interface A extends Invoker {
        void handle(RoutingContext ctx) throws Exception;

        @Override
        default void invoke(RoutingContext ctx, String[] args) throws Exception {
            handle(ctx);
        }
    }

    public static interface A1 extends Invoker {
        void handle(RoutingContext ctx, String a1) throws Exception;

        @Override
        default void invoke(RoutingContext ctx, String[] args) throws Exception {
            handle(ctx, args[0]);
        }
    }

    public static interface A2 extends Invoker {
        void handle(RoutingContext ctx, String a1, String a2) throws Exception;

        @Override
        default void invoke(RoutingContext ctx, String[] args) throws Exception {
            handle(ctx, args[0], args[1]);
        }
    }

    public static interface A3 extends Invoker {
        void handle(RoutingContext ctx, String a1, String a2, String a3) throws Exception;

        @Override
        default void invoke(RoutingContext ctx, String[] args) throws Exception {
            handle(ctx, args[0], args[1], args[2]);
        }
    }

    public static Action A(A invoker) {
        return new Action(invoker);
    }

    public static Action A(A1 invoker) {
        return new Action(invoker);
    }

    public static Action A(A2 invoker) {
        return new Action(invoker);
    }

    public static Action A(A3 invoker) {
        return new Action(invoker);
    }
}

