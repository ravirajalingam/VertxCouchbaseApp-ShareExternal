package com.aexp.vc;

import com.couchbase.client.core.event.consumers.LoggingConsumer;
import com.couchbase.client.core.logging.CouchbaseLogLevel;
import com.couchbase.client.core.metrics.DefaultLatencyMetricsCollectorConfig;
import com.couchbase.client.core.metrics.DefaultMetricsCollectorConfig;
import com.couchbase.client.java.AsyncBucket;
import com.couchbase.client.java.CouchbaseAsyncCluster;
import com.couchbase.client.java.document.RawJsonDocument;
import com.couchbase.client.java.env.CouchbaseEnvironment;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import com.couchbase.client.java.error.DocumentDoesNotExistException;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Launcher;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Router;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import io.vertx.rxjava.core.AbstractVerticle;
import rx.Observable;
import rx.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);
    private CouchbaseAsyncCluster cluster;
    private volatile AsyncBucket bucket;
    private volatile AsyncBucket bucket2;
    private volatile AsyncBucket bucket3;




    @Override
    public void init(Vertx vertx, Context context) {
        super.init(vertx, context);
        Router router = Router.router(vertx);
        //getting the configuration JSON
        JsonObject config = context.config();

        //getting the bootstrap node, as a JSON array (default to localhost)
        JsonArray seedNodeArray = config.getJsonArray("couchbase.seedNodes", new JsonArray().add("52.32.243.92"));
        //convert to a List
        List seedNodes = new ArrayList<>(seedNodeArray.size());
        for (Object seedNode : seedNodeArray) {
            seedNodes.add((String) seedNode);
        }
        CouchbaseEnvironment cbenv = DefaultCouchbaseEnvironment
            .builder()
            .defaultMetricsLoggingConsumer(true, CouchbaseLogLevel.TRACE, LoggingConsumer.OutputFormat.JSON_PRETTY)
            .runtimeMetricsCollectorConfig(DefaultMetricsCollectorConfig.create(30, TimeUnit.SECONDS ))
            .networkLatencyMetricsCollectorConfig(DefaultLatencyMetricsCollectorConfig.create(30, TimeUnit.SECONDS ))
            .dnsSrvEnabled(false)
            .connectTimeout(15000)
            .socketConnectTimeout(15000)
            .build();
        //use that to bootstrap the Cluster
        this.cluster = CouchbaseAsyncCluster.create(cbenv, seedNodes);
        cbenv.eventBus().get().subscribe(System.out::println);


        router.get("/get/:id").handler(this::getHandler);
        router.get("/health").handler(routingContext -> routingContext.response().putHeader("content-type", "application/json").setStatusCode(200).end("OK"));
        router.get("/").handler(routingContext -> routingContext.response().putHeader("content-type", "application/json").setStatusCode(200).end("Welcome To Couchbase Vert.X app"));

        HttpServer server = vertx.createHttpServer().requestHandler(router::accept).listen(8080);
        //new HttpServerOptions().setSsl(true).setKeyStoreOptions(new JksOptions().setPassword("password").setPath("keystore.p12"))

    }

    @Override
    public void start() throws Exception {



        try {
cluster.openBucket(config().getString("couchbase.bucketName", "sampleData"), config().getString("couchbase.bucketPassword", "password"))
                    .doOnNext(openedBucket -> LOGGER.info("Bucket opened " + openedBucket.name()))
                    .doOnNext(openedBucket -> this.bucket = openedBucket)
            
             
                    .subscribe(
                            openedBucket -> bucket = openedBucket
                            );
        cluster.openBucket(config().getString("couchbase.bucketName", "beer-sample"), config().getString("couchbase.bucketPassword", "password"))
                    .doOnNext(openedBucket -> LOGGER.info("Bucket opened " + openedBucket.name()))
                    .doOnNext(openedBucket -> this.bucket2 = openedBucket)
            
             
                    .subscribe(
                            openedBucket -> bucket2 = openedBucket
                            );
        
            cluster.openBucket(config().getString("couchbase.bucketName", "travel-sample"), config().getString("couchbase.bucketPassword", "password"))
                    .doOnNext(openedBucket -> LOGGER.info("Bucket opened " + openedBucket.name()))
                    .doOnNext(openedBucket -> this.bucket3 = openedBucket)
            
             
                    .subscribe(
                            openedBucket -> bucket3 = openedBucket
                            );
        }catch(Exception e){
            System.out.println(e.getMessage());
        }
        finally{

            
        }
    }

    
    
    

    


    

    private void getHandler(RoutingContext routingContext) {
        String docId = routingContext.request().getParam("id");

        LOGGER.debug("getting the value from couchbase");
        bucket3.get(docId, RawJsonDocument.class)
            .switchIfEmpty(Observable.error(new DocumentDoesNotExistException()))
            .subscribe(doc -> routingContext.response().putHeader("content-type", "application/json").setStatusCode(200).end(doc.content()), error -> System.out.println("error occured fetching id ::" + error.getMessage()), () -> System.out.println("completeted get request"));
    }

    @Override
    public void stop(Future stopFuture) throws Exception {
        cluster.disconnect()
            .doOnNext(isDisconnectedCleanly -> LOGGER.info("Disconnected Cluster (cleaned threads: " + isDisconnectedCleanly + ")"))
                .subscribe(
            isDisconnectedCleanly -> stopFuture.complete(),
            stopFuture::fail,
            Schedulers::shutdown);
    }

    public static void main(String[] args) throws InterruptedException {
    
       Launcher.executeCommand("run", MainVerticle.class.getName());
    }
}
