package com.cala.websoket;

//#websocket-example-using-core

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.ws.BinaryMessage;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.settings.ServerSettings;
import akka.http.javadsl.settings.WebSocketSettings;
import akka.http.scaladsl.model.AttributeKeys;
import akka.japi.Pair;
import akka.japi.function.Function;
import akka.stream.CompletionStrategy;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.*;
import akka.util.ByteString;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class WebSocketCoreExample {

    public static final Logger logger = LoggerFactory.getLogger(WebSocketCoreExample.class);
    static ActorRef actorRef = null;

    public static HttpResponse handleRequest(HttpRequest request, ActorSystem system) {
        if (request.getUri().path().equals("/websocket")) {
            return request.getAttribute(AttributeKeys.webSocketUpgrade())
                    .map(upgrade -> (HttpResponse) upgrade.handleMessagesWith(greeter(system)))
                    .orElse(
                            HttpResponse.create().withStatus(StatusCodes.BAD_REQUEST).withEntity("Expected WebSocket request")
                    );
        } else {
            return HttpResponse.create().withStatus(404);
        }
    }
    //#websocket-handling

    public static void main(String[] args) throws Exception {
        ActorSystem system = ActorSystem.create();
        try {
            //#websocket-ping-payload-server
            ServerSettings defaultSettings = ServerSettings.create(system);
            AtomicInteger pingCounter = new AtomicInteger();
            WebSocketSettings customWebsocketSettings = defaultSettings.getWebsocketSettings()
                    .withPeriodicKeepAliveData(() ->
                            ByteString.fromString(String.format("debug-%d", pingCounter.incrementAndGet())));
            ServerSettings customServerSettings = defaultSettings.withWebsocketSettings(customWebsocketSettings);
            final Function<HttpRequest, HttpResponse> handler = request -> handleRequest(request, system);
            CompletionStage<ServerBinding> serverBindingFuture =
                    Http.get(system)
                            .newServerAt("localhost", 8080)
                            .withSettings(customServerSettings)
                            .bindSync(handler);

            // will throw if binding fails
            serverBindingFuture.toCompletableFuture().get(1, TimeUnit.SECONDS);
            System.out.println("Press ENTER to stop.");
            Scanner scanner = new Scanner(System.in);
            for (; ; ) {
                String ss = scanner.next();
                actorRef.tell(BinaryMessage.create(ByteString.fromString(String.format("return %s", ss))), ActorRef.noSender());
            }

        } finally {
            system.terminate();
        }
    }


    static class StreamInitialized {
    }

    /**
     * A handler that treats incoming messages as a name,
     * and responds with a greeting to that name
     */
    public static Flow<Message, Message, NotUsed> greeter(ActorSystem system) {
        ActorRef receiver = system.actorOf(Props.create(AckReceiver.class));
        Sink<Message, NotUsed> sink =
                Sink.actorRef(
                        receiver,
                        new WebSocketCoreExample.StreamInitialized());
        Source<Message, ActorRef> source =
                Source.actorRef(
                        elem -> {
                            // complete stream immediately if we send it Done
                            if (elem == Done.done()) return Optional.of(CompletionStrategy.immediately());
                            else return Optional.empty();
                        },
                        // never fail the stream because of a message
                        elem -> Optional.empty(),
                        Integer.MAX_VALUE,
                        OverflowStrategy.dropHead());// note: backpressure is not supported
        Pair<ActorRef, Publisher<Message>> pair =
                source.toMat(Sink.asPublisher(AsPublisher.WITHOUT_FANOUT), (Keep.both()))
                        .run(system);
        var out = Source.fromPublisher(pair.second());
        actorRef = pair.first();
        receiver.tell("init", actorRef);
        return Flow.fromSinkAndSource(sink, out);
    }
}
