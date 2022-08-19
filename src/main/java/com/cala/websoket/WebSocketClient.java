package com.cala.websoket;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.ConnectionContext;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.TextMessage;
import akka.http.javadsl.model.ws.WebSocketRequest;
import akka.http.javadsl.settings.ClientConnectionSettings;
import akka.http.javadsl.settings.WebSocketSettings;
import akka.japi.Pair;
import akka.stream.CompletionStrategy;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.*;
import akka.util.ByteString;
import org.reactivestreams.Publisher;

import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

public class WebSocketClient {
    public static void main(String[] args) {
        ActorSystem system = ActorSystem.create();
        Materializer materializer = Materializer.createMaterializer(system);
        Http http = Http.get(system);


        ClientConnectionSettings defaultSettings = ClientConnectionSettings.create(system);
        AtomicInteger pingCounter = new AtomicInteger();

        WebSocketSettings customWebsocketSettings = defaultSettings.getWebsocketSettings()
                .withPeriodicKeepAliveData(() ->
                        ByteString.fromString(String.format("debug-%d", pingCounter.incrementAndGet()))
                );
        ClientConnectionSettings customSettings =
                defaultSettings.withWebsocketSettings(customWebsocketSettings);
        Pair<Flow<Message, Message, NotUsed>, ActorRef> pair = getFlow(system);
        http.singleWebSocketRequest(
                WebSocketRequest.create("ws://localhost:9527/websocket"),
                pair.first(),
                ConnectionContext.noEncryption(),
                Optional.empty(),
                customSettings,
                system.log(),
                materializer
        );

// The first value in the pair is a CompletionStage<WebSocketUpgradeResponse> that
// completes when the WebSocket request has connected successfully (or failed)

// the second value is the completion of the sink from above
// in other words, it completes when the WebSocket disconnects

// in a real application you would not side effect here
// and handle errors more carefully
        Scanner scanner = new Scanner(System.in);
        for (; ; ) {
            String ss = scanner.next();
            pair.second().tell(TextMessage.create(String.format("%s", ss)), ActorRef.noSender());
        }
    }

    public static Pair<Flow<Message, Message, NotUsed>, ActorRef> getFlow(ActorSystem system) {
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
                        100,
                        OverflowStrategy.dropHead());// note: backpressure is not supported

        Pair<ActorRef, Publisher<Message>> pair =
                source.toMat(Sink.asPublisher(AsPublisher.WITHOUT_FANOUT), (Keep.both()))
                        .run(system);
        var out = Source.fromPublisher(pair.second());
        return new Pair<>(Flow.fromSinkAndSourceCoupled(sink, out), pair.first());
    }
}
