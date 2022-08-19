package com.cala.websoket;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.http.javadsl.model.ws.BinaryMessage;
import akka.http.javadsl.model.ws.TextMessage;
import akka.util.ByteString;

public class AckReceiver extends AbstractLoggingActor {
    ActorRef actorRef;

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(BinaryMessage.class, this::toBinaryMessage)
                .match(TextMessage.class, this::toTextMessage)
                .match(String.class, this::s)
                .matchAny(this::ss)
                .build();

    }

    private void toBinaryMessage(BinaryMessage message) {
        ByteString byteString = message.asBinaryMessage().getStrictData();
        log().debug("BinaryMessage->{}", byteString);
        byteString.asByteBuffer().getShort();
        if (actorRef != null) {
            actorRef.tell(TextMessage.create("BinaryMessage:" + message.asTextMessage().getStrictText()), ActorRef.noSender());
        }
    }

    private void toTextMessage(TextMessage message) {
        log().debug("TextMessage->{}",message.asTextMessage().getStrictText());
        if (actorRef != null) {
            actorRef.tell(TextMessage.create("TextMessage:" + message.asTextMessage().getStrictText()), ActorRef.noSender());
        }
    }
    private void s(String s) {
        if (s.equals("init")) {
            actorRef = sender();
            return;
        }
        System.out.printf("String -> %s" , s);
    }
    private void ss(Object o) {
        System.out.println(o);
    }

}
