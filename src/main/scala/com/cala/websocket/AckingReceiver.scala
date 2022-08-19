package com.cala.websocket

import akka.actor.{Actor, ActorLogging}
import akka.http.javadsl.model.ws.TextMessage
import akka.http.scaladsl.model.ws.BinaryMessage
import com.cala.websocket.AckingReceiver.{StreamCompleted, StreamInitialized}
import protocol.p2.sc4

import java.nio.ByteOrder

object AckingReceiver {
  case object Ack

  case object StreamInitialized

  case object StreamCompleted

  final case class StreamFailure(ex: Throwable)
}

class AckingReceiver() extends Actor with ActorLogging {
  implicit val byteOrder: ByteOrder = java.nio.ByteOrder.LITTLE_ENDIAN

  override def receive: Receive = {
    case StreamCompleted => log.info(s"streamCompleted => ")
    case StreamInitialized =>
      log.info("Stream initialized!")
    case el: String =>
      log.info("Received element: {}", el)
    case tm: TextMessage => log.info(s"receive => ${tm.asTextMessage.toString}")
    case bm: BinaryMessage =>
      var data = bm.getStrictData
      val leng = data.iterator.getShort
      data = data drop 2
      val id = data.iterator.getShort
      val sc41 = sc4.parseFrom((data drop 2).toArray)
      log.debug(s"messageLeng = $leng, protocolId == $id,  success = ${sc41.success},  id == ${sc41.userId}")
  }
}
