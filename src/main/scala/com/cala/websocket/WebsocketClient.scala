package com.cala.websocket

import akka.Done
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws._
import akka.stream.scaladsl._
import akka.stream.{CompletionStrategy, OverflowStrategy}
import akka.util.ByteString
import protocol.p2.cs4
import scalapb.GeneratedMessage

import java.nio.ByteOrder
import scala.io.StdIn

object WebsocketClient {
  var actorRef1: ActorRef = _
  implicit val system: ActorSystem = ActorSystem()
  implicit val byteOrder: ByteOrder = java.nio.ByteOrder.LITTLE_ENDIAN

  def ge = {
    val receiver = system.actorOf(Props(new AckingReceiver()))
    val source: Source[Message, ActorRef] =
      Source
        .actorRef[Message](
          completionMatcher = cm,
          failureMatcher = PartialFunction.empty[Any, Throwable],
          Int.MaxValue,
          OverflowStrategy.dropHead)

    val (actorRef, publisher) = source.toMat(Sink.asPublisher(fanout = false))(Keep.both).run()
    val out = Source.fromPublisher(publisher)
    val sink =
      Sink.actorRef(
        receiver,
        onCompleteMessage = OnCompleteMessage,
        onFailureMessage = onErrorMessage)

    this.actorRef1 = actorRef
    Flow.fromSinkAndSourceCoupled(sink, out)
  }

  val cm: PartialFunction[Any, CompletionStrategy] = {
    case Done =>
      CompletionStrategy.immediately
  }
  val OnCompleteMessage = AckingReceiver.StreamCompleted
  val onErrorMessage = (ex: Throwable) => AckingReceiver.StreamFailure(ex)

  def main(args: Array[String]): Unit = {
    val webSocketFlow = Http().singleWebSocketRequest(WebSocketRequest("ws://localhost:9527/websocket"), ge)
//    for (i <- 1 to 99999) {
//      println(s"$i")
//      actorRef1 !  TextMessage(s"$i")
//    }
    while (true) {
      val s = StdIn.readLine()
      actorRef1 ! BinaryMessage.Strict(protocolToByteString(1000, cs4(s.toInt)))
    }
  }

  final def protocolToByteString(protocolId: Int, message: GeneratedMessage): ByteString = {
    val bytesBuilder = ByteString.newBuilder
    val bs = bytesBuilder.putShort(message.serializedSize + 2).putShort(protocolId).putBytes(message.toByteArray).result()
    bytesBuilder.clear()
    bs
  }
}
