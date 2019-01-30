package io.vertx.examples;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sync.SyncVerticle;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.ErrorHandler;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;
import org.json.simple.parser.JSONParser;

/*
 */
public class SearchServer extends SyncVerticle {
    private final static Logger LOGGER = Logger.getLogger(SearchServer.class.getName());
    private int port;

    private RedisClient redisClient;

    public static void main(String[] args) throws Exception {
        DeploymentOptions deploymentOptions = new DeploymentOptions();
        VertxOptions options = new VertxOptions();

        if (args.length == 2 && args[0].equals("-conf")) { // set up json conf file
            Object json = new JSONParser().parse(new FileReader(args[1]));
            deploymentOptions.setConfig(JsonObject.mapFrom(json));
            Runner.runExample(SearchServer.class, options, deploymentOptions);
        } else { // run default conf
            Runner.runExample(SearchServer.class, options, deploymentOptions);
        }
    }

    @Override
    public void stop() {
        redisClient.close(h -> {});
    }

    @Override
    public void start(Future<Void> fut) {
        vertx.executeBlocking(future -> {
            try {
                redisClient = initRedis();
                initDB();
                future.complete();
            } catch (Exception ignore) {
                future.fail(ignore);
            }
        }, res -> {
            if (res.succeeded()) {
                startService();
            } else {
                LOGGER.severe("Failed to start: "+res.cause());
            }
        });
    }

    private void startService() {
        // Create a router object.
        port = config().getInteger("http.port", 8888);

        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);
        router.route().failureHandler(ErrorHandler.create(true));
        router.get("/s/:queryString").handler(this::handleGetRouter);
        server.requestHandler(router);
        server.listen(port);

        LOGGER.info("Service up on port "+port);
    }

    private void initDB() {
        String fileName = config().getString("data.path", "data.json");
        LOGGER.info("Using data: "+ fileName);

        try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
            stream
                .map(l -> new JsonObject(l).getJsonArray("data"))
                .forEach(e -> {
                    for (int i=0; i<e.size(); i++) {
                        JsonObject entry = e.getJsonObject(i);
                        int rid = entry.getInteger("review_id");
                        String date = entry.getString("date");
                        String msg = entry.getString("message");

                        redisClient.set(rid+"_"+date, msg, h -> {});

                        Pattern.compile(" ").splitAsStream(msg).forEach(
                                w -> redisClient.sadd(w, rid+"_"+date, h -> {})
                        );
                    }
                });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private RedisClient initRedis() {
        // If a config file is set, read the host and port.
        String host = config().getString("redis.host", "127.0.0.1");
        int port = config().getInteger("redis.port", 6379);

        LOGGER.info("Using redis db: "+ host +":"+ port);
        // Create the redis client
        return RedisClient.create(vertx, new RedisOptions().setHost(host).setPort(port));
    }

    // GET /s/queryString
    private void handleGetRouter(RoutingContext rc) {
        String queryString = rc.request().getParam("queryString");
        HttpServerResponse response = rc.request().response();

        try {
            String[] terms = queryString.split(" ");
            handleRedisDoubleTrip(Arrays.asList(terms), response);
        } catch (Exception e) {
            LOGGER.severe(e.getMessage());
            e.printStackTrace();
            response.setStatusCode(504).end();
        }
    }

    private void handleRedisDoubleTrip(List<String> terms, HttpServerResponse response) {
        redisClient.sinter(terms, h -> {
            if (h.succeeded()) {
                List<String> keys = h.result().stream()
                        .map(String.class::cast)
                        .collect(Collectors.toList());

                redisClient.mgetMany(keys, h2 -> {
                    if (h2.succeeded()) {
                        JsonArray messages = h2.result();

                        JsonArray jsonArray = new JsonArray();
                        for (int i=0; i<keys.size(); i++) {
                            String[] k = keys.get(i).split("_");
                            jsonArray.add(k[0]);
                            jsonArray.add(k[1]);
                            jsonArray.add(messages.getString(i));
                        }
                        JsonObject entries = new JsonObject();
                        entries.put("entries", jsonArray);
                        entries.put("size", messages.size());
                        response.setStatusCode(200).end(entries.toString());
                    } else {
                        response.setStatusCode(200).end("No results: "+h2.cause().getMessage());
                    }
                });
            } else {
                response.setStatusCode(200).end("No results: "+h.cause().getMessage());
            }
        });
    }

    // Could be used to convert the date to epoch
    private long getEpoch(String date) {
        try {
            DateFormat fmt = new SimpleDateFormat("MMMM dd, yyyy");
            Date d = fmt.parse(date);
            return d.toInstant().toEpochMilli();
        } catch (java.text.ParseException e) {
            e.printStackTrace();
            return -1;
        }
    }
}
