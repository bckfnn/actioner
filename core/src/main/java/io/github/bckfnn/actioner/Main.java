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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.ext.dropwizard.Match;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.ErrorHandler;
import io.vertx.ext.web.handler.LoggerFormat;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.UserSessionHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.sstore.SessionStore;

/**
 * Main class for an application Verticle.
 */
public class Main extends AbstractVerticle {
    static Logger log = LoggerFactory.getLogger(Main.class);

    protected Config config;
    private Config translations;
    //private Map<String, Menus> menus;
    private ActionRouter actionRouter = new ActionRouter();
    //private MenuLoader menuLoader = new MenuLoader();
    private SessionStore sessionStore;
    private AuthProvider authProvider;
    private HttpServer server;

    private String translationFile;

    public Main() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(loggerContext);
        loggerContext.reset();
        try {
            configurator.doConfigure(Main.class.getResourceAsStream("/logback.xml"));
        } catch (JoranException e) {
            // empty, handled by StatusPrinter
        }
    }


    /**
     * Initialize.
     * @throws Exception
     */
    @Override
    public void start(Future<Void> future) throws Exception {
        log.info("Starting");
        config = loadConfig();

        if (config.hasPath("translations")) {
            translations = ConfigFactory.load(config.getString("translations"));
        }
/*
        menus = config.getStringList("app.menus").stream().collect(
                Collectors.toMap(l -> l, l -> new Menus(ConfigFactory.load(l))));

        config.getStringList("app.contextMenus").stream().forEach(m -> {
            menuLoader.loadMenus(m);
        });
*/
        log.debug("config loaded");

        boolean develop = config.getBoolean("develop");
        String contextRoot = config.getString("webserver.contextRoot");

        //Schema schema = makeSchema();
        //Persistor persistor = makePersistor(config, schema);

        if (config.hasPath("groups")) {
            //authProvider = new DbAuthProvider(persistor, ConfigFactory.load(config.getString("groups")));
        }

        Router router = Router.router(vertx);
        System.out.println(router);

        router.route().handler(ctx -> {

            ctx.put(Vertx.class.getName(), vertx);
            ctx.put(Router.class.getName(), router);
            ctx.put(ActionRouter.class.getName(), actionRouter);
            //ctx.put(MenuLoader.class.getName(), menuLoader);
            ctx.put(Config.class.getName(), config);
            ctx.put("ctx", ctx);
            ctx.put("startTime", System.currentTimeMillis());
            ctx.put("translations", translations);
            ctx.put("contextRoot", contextRoot);
            //ctx.put(Persistor.class.getName(), persistor);
            //ctx.put(Schema.class.getName(), schema);
            ctx.put(AuthProvider.class.getName(), authProvider);
            //ctx.data().putAll(menus);
            ctx.response().putHeader("X-Frame-Options", "deny");
            configContext(ctx);
            log.debug("request: {}", ctx.request().path());
            ctx.next();
        });

        if (authProvider != null) {
            //sessionStore = new PersistentLocalSessionStore(vertx, LocalSessionStore.DEFAULT_SESSION_MAP_NAME, LocalSessionStore.DEFAULT_REAPER_INTERVAL, config.getString("sessionStorage")); //LocalSessionStore.create(vertx);
        }

        router.route().handler(CookieHandler.create());
        router.route().handler(BodyHandler.create());

        System.out.println(config.hasPath("webserver") + " " +config.getObject("webserver"));
        if (config.hasPath("webserver.webjars")) {
            router.route().path(contextRoot + config.getString("webserver.webjars.uri")).handler(new WebjarsHandler());
        }
        if (config.hasPath("webserver.assets")) {
            router.route().path(contextRoot + config.getString("webserver.assets.uri")).handler(new AssetsHandler());
        }

        if (config.hasPath("webserver.public")) {
            router.route().path(contextRoot + config.getString("webserver.public.uri")).handler(StaticHandler.create(config.getString("app.publicFolder")).setCachingEnabled(!develop).setFilesReadOnly(!develop));
        }

        if (authProvider != null) {
            router.route().handler(SessionHandler.create(sessionStore).setNagHttps(!develop));
            router.route().handler(UserSessionHandler.create(authProvider));
        }
        router.route().handler(new AcceptLanguageHandler(true));
        router.route().handler(LoggerHandler.create(false, LoggerFormat.SHORT));

        configRouter(router);

        for (String cls : config.getStringList("app.actionClasses")) {
            actionRouter.addAction(contextRoot, router, getClass().getClassLoader().loadClass(cls));
        }

        router.route().handler(new LayoutTemplateHandler());
        router.route().failureHandler(ctx -> {
            if (ctx.failed() && ctx.failure() != null) {
                log.error("Error handler", ctx.failure());
                //ctx.failure().printStackTrace();
            }
            ErrorHandler.create(develop).handle(ctx);
        });

        BridgeOptions opts = new BridgeOptions()
                .addInboundPermitted(new PermittedOptions()
                    .setAddress("importList"))
                .addOutboundPermitted(new PermittedOptions()
                    .setAddress("importList"));
        SockJSHandler ebHandler = SockJSHandler.create(vertx).bridge(opts);
        router.route("/eventbus/*").handler(ebHandler);

        HttpServerOptions options = new HttpServerOptions();
        options.setPort(config.getInt("port"));
/*
        TimerContext timerContext = new TimerContext(vertx);
        timerContext.put(Persistor.class.getName(), persistor);
        timerContext.put(Config.class.getName(), config);
        startTimers(timerContext);
*/
        server = vertx.createHttpServer(options);
        server.requestHandler(router::accept).listen(res -> {
            if (res.failed()) {
                log.error("http server failed to start", res.cause());
                future.fail(res.cause());
            } else {
                log.info("http started");
            }
            future.complete();
        });
    }

    @Override
    public void stop() {
        log.info("Stopping");
        server.close();
        if (sessionStore != null) {
            sessionStore.close();
        }
        // http://logback.qos.ch/manual/configuration.html#stopContext
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.stop();
    }

    /**
     * Override to provide additional configuration of the RoutingContext.
     * @param ctx the RoutingContext.
     */
    protected void configContext(RoutingContext ctx) {

    }

    /**
     * Override to provide additional configuration of the Router.
     * @param ctx the RoutingContext.
     */
    protected void configRouter(Router router) {

    }

/*
    protected Schema makeSchema() throws Exception {
        return new Schema();
    }

    protected void startTimers(TimerContext timerContext) throws Exception {

    }

    protected Persistor makePersistor(Config config, Schema schema) throws Exception {
        return null;
    }

    protected void loadPrincipal(Persistor persistor, String loginId, String password, Handler<AsyncResult<JsonObject>> handler) {
        handler.handle(Future.failedFuture("loadPrincipal not implemented"));
    }
*/
    public static void deploy(Class<? extends Verticle> verticle, String configName, String[] args) throws Exception {
        deploy(verticle, configName, args, res -> {});
    }

    public static void deploy(Class<? extends Verticle> verticle, String configName, String[] args, Handler<Vertx> result) throws Exception {
        if (args.length >= 1 && args[0].equals("stop")) {
            System.out.println("stopping");
            System.exit(1);
        }

        System.setProperty("appConfig", configName);
        System.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory");
        System.setProperty("vertx.disableFileCaching", "true");

        Config config = loadConfig();
        checkEndorsed(config, args);

        VertxOptions vertxOptions = new VertxOptions();
        vertxOptions.setWorkerPoolSize(config.getInt("workerPoolSize"));

        DropwizardMetricsOptions opt = new DropwizardMetricsOptions();
        opt.setEnabled(config.getBoolean("metricsEnabled"));
        opt.setRegistryName("vertxRegistry");
        opt.addMonitoredHttpServerUri(new Match().setValue(".*"));
        opt.addMonitoredHttpClientUri(new Match().setValue(".*"));
        opt.addMonitoredEventBusHandler(new Match().setValue(".*"));
        vertxOptions.setMetricsOptions(opt);

        Vertx vertx = Vertx.vertx(vertxOptions);

        /*
        MetricRegistry registry = SharedMetricRegistries.getOrCreate("vertxRegistry");

        ConsoleReporter rep = ConsoleReporter.forRegistry(registry)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        rep.start(10, TimeUnit.SECONDS);
         */
        /*
        System.out.println(vertx);
        MetricsService metricsService = MetricsService.create(vertx);
        JsonObject metrics = metricsService.getMetricsSnapshot(vertx);
        System.out.println("xx:" + metrics);
        System.out.println("xx:" + AbstractMetrics.class.getClassLoader());
         */
        DeploymentOptions opts = new DeploymentOptions();
        opts.setInstances(config.getInt("instances"));
        if (config.getBoolean("develop")) {
            opts.setIsolatedClasses(config.getStringList("isolatedClasses"));
        }

        AtomicReference<String> deploymentId = new AtomicReference<>();


        Function<Handler<Vertx>, Void> deploy = h -> {
            if (config.getBoolean("develop")) {
                opts.setIsolationGroup("ethics" + System.currentTimeMillis());
            }
            vertx.deployVerticle(verticle.getName(), opts, res -> {
                if (res.succeeded()) {
                    deploymentId.set(res.result());
                    h.handle(vertx);
                } else {
                    res.cause().printStackTrace();
                }
            });
            return null;
        };

        deploy.apply(result);
        if (config.getBoolean("develop")) {
            redeploy(() -> {
                vertx.undeploy(deploymentId.get(), r -> {
                    deploy.apply(h -> {
                        System.err.println("redeployed!");
                    });
                });
            });
        }
    }


    protected static void redeploy(Runnable g) throws Exception {
        WatchService watcher = FileSystems.getDefault().newWatchService();
        Path dir = Paths.get("target/classes");

        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path file, BasicFileAttributes attrs) throws IOException {
                //System.out.println(file);;
                file.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
                return FileVisitResult.CONTINUE;
            }
        });
        //System.out.println("walked");

        for (;;) {
            WatchKey key = watcher.take();
            key.reset();
            while ((key = watcher.poll(500, TimeUnit.MILLISECONDS)) != null) {
                //System.out.println("poll");
                for (WatchEvent<?> event: key.pollEvents()) {
                    @SuppressWarnings("unused")
                    WatchEvent.Kind<?> kind = event.kind();
                    //System.out.println("found " + key + " " + kind + " " + event.context() + " " + key.watchable() + " " + event.count());
                }

                key.reset();
            }
            g.run();
        }
    }


    protected static Config loadConfig() {

        Config config = ConfigFactory.load(System.getProperty("appConfig"))
                .withFallback(ConfigFactory.load());

        String vcapServices = System.getenv("VCAP_SERVICES");
        if (vcapServices != null) {
            config = config.withFallback(ConfigFactory.parseString(vcapServices));
        }

        if (config.getBoolean("develop")) {
            config = config.getConfig("develop_opts").withFallback(config);
        }
        return config.resolve();
    }

    static void checkEndorsed(Config config, String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("install")) {
            File extFolder = new File(config.getString("extFolder"));
            for (String url : config.getStringList("endorsedLibraries")) {
                URL u = new URL(url);
                File file = new File(extFolder, Utils.extension(u.getPath(), '/'));
                log.info("Installing extension {}", file);
                try (InputStream is = u.openStream(); OutputStream os = new FileOutputStream(file)) {
                    Utils.copy(is, os);
                }
            }

            for (String filename : config.getStringList("securityFiles")) {
                File securityFolder = new File(config.getString("securityFolder"));

                URL url = Main.class.getResource("/jdk/" + filename);
                File file = new File(securityFolder, filename);
                log.info("Installing security file {}", filename);
                try (InputStream is = url.openStream(); OutputStream os = new FileOutputStream(file)) {
                    Utils.copy(is, os);
                }
            }

            System.exit(0);
        }

        try {
            /*
            new LoadProvider().run();

            if (Security.getProvider("BC") != null) {
                return;
            }
             */
        } catch (Exception e) {
            log.error("Unable to load BC security provider", e);
            System.exit(-1);
        }

        try {
            Main.class.getClassLoader().loadClass("org.w3c.dom.ElementTraversal");
        } catch (Exception e) {
            log.error("Unable to load org.w3c.dom.ElementTraversal", e);
            System.exit(-1);
        }
    }
/*
    static class LoadProvider implements Runnable {
        @Override
        public void run() {
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }
    }
*/
    /*
    class DbAuthProvider implements AuthProvider {
        private Persistor persistor;
        Config groups;

        public DbAuthProvider(Persistor persistor, Config groups) {
            this.persistor = persistor;
            this.groups = groups;
        }

        @Override
        public void authenticate(JsonObject authInfo, Handler<AsyncResult<io.vertx.ext.auth.User>> handler) {
            //log.info("authenticate {}", authInfo);
            String username = authInfo.getString("username");
            String password = authInfo.getString("password");

            loadPrincipal(persistor, username, password, S.ar(handler, principal -> {
                DbUser dbUser = new DbUser(principal, this.groups);
                dbUser.setAuthProvider(this);

                handler.handle(Future.succeededFuture(dbUser));
            }));
        }
    }
    */
}
