package com.cala.websocket

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.settings.ServerSettings
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{CompletionStrategy, Materializer, OverflowStrategy}
import akka.{Done, NotUsed}

import java.util.concurrent.atomic.AtomicInteger
import scala.::
import scala.io.StdIn

object WebsocketServer {

  implicit val system: ActorSystem = ActorSystem()
  var list: List[ActorRef] = List()
  var actorRef: ActorRef = _

  def ge: Flow[Message, Message, NotUsed] = {
    val receiver = system.actorOf(Props(new AckingReceiver()))
    val source: Source[Message, ActorRef] =
      Source
        .actorRef[Message](
          completionMatcher = cm,
          failureMatcher = PartialFunction.empty[Any, Throwable],
          Int.MaxValue,
          OverflowStrategy.dropHead)

    val (actorRef:ActorRef, publisher) = source.toMat(Sink.asPublisher(fanout = false))(Keep.both).run()
    val out = Source.fromPublisher(publisher)
    val sink =
      Sink.actorRefWithBackpressure(
        receiver,
        InitMessage,

        onCompleteMessage = OnCompleteMessage,
        onFailureMessage = OnErrorMessage)

    this.list :+ actorRef
    this.actorRef = actorRef
    Flow.fromSinkAndSourceCoupled(sink, out)
  }

  val cm: PartialFunction[Any, CompletionStrategy] = {
    case Done =>
      CompletionStrategy.immediately
  }

  val AckMessage = AckingReceiver.Ack

  val InitMessage = AckingReceiver.StreamInitialized
  val OnCompleteMessage = AckingReceiver.StreamCompleted
  val OnErrorMessage = (ex: Throwable) => AckingReceiver.StreamFailure(ex)

  val route: Route =
    path("websocket") {
      get {
        handleWebSocketMessages(ge)
      }
    }

  val defaultSettings: ServerSettings = ServerSettings(system)

  val pingCounter = new AtomicInteger()

  def main(args: Array[String]): Unit = {
    val bind = Http().newServerAt("127.0.0.1", 8080).withSettings(defaultSettings).bind(route)
    while (true){
      val s = StdIn.readLine()
      list.foreach(actorRef => actorRef ! TextMessage(s"$s"))
    }



    import system.dispatcher // for the future transformations
    bind
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate())
  }

}
