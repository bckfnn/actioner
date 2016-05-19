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

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import io.vertx.ext.web.Router;

/**
 * The ActionRouter keep track of all the actions in the application.
 */
public class ActionRouter {
    private Map<String, Action> routes = new LinkedHashMap<>();

    public void add(Action action) {
        routes.put(action.name(), action);
    }

    public Action getAction(String name) {
        return routes.get(name);
    }

    public Collection<Action> getActions() {
        return routes.values();
    }

    public void addAction(String contextRoot, Router router, Class<?> actionCls) {
        Arrays.asList(actionCls.getFields()).stream().forEach(f -> {
            try {
                //System.out.println(f);
                Object field = f.get(null);
                if (!(field instanceof Action)) {
                    return;
                }
                Action action = (Action) f.get(null);
                Get get = f.getAnnotation(Get.class);
                if (get != null) {
                    action.decorate(router, f.getDeclaringClass(), f.getName(), contextRoot, get);
                }
                Post post = f.getAnnotation(Post.class);
                if (post != null) {
                    action.decorate(router, f.getDeclaringClass(), f.getName(), contextRoot, post);
                }
                add(action);
            } catch (Exception e) {
                throw Utils.rethrow(e);
            }
        });
    }
}
