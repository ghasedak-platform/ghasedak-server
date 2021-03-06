package im.ghasedak.server.update

import akka.Done
import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{ Behavior, PostStop, Signal }
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.event.Logging
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.typesafe.config.Config
import im.ghasedak.rpc.update.ResponseGetDifference
import im.ghasedak.server.Processor
import im.ghasedak.server.db.DbExtension
import im.ghasedak.server.serializer.ActorRefConversions._
import im.ghasedak.server.serializer.ImplicitActorRef._
import im.ghasedak.server.update.UpdateEnvelope.{ Acknowledge, GetDifference, Seek, StreamGetDifference, Subscribe }
import slick.jdbc.PostgresProfile

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }
import scala.concurrent.duration._
object UpdateProcessor {
  val ShardingTypeName: EntityTypeKey[UpdatePayload] = EntityTypeKey[UpdatePayload]("UpdateProcessor")

  case object StopOffice extends UpdatePayload

  def apply(entityId: String): Behavior[UpdatePayload] = {
    Behaviors.setup[UpdatePayload](context ⇒ new UpdateProcessor(context, entityId))
  }
}

class UpdateProcessor(context: ActorContext[UpdatePayload], entityId: String) extends Processor[UpdatePayload]
  with UpdateHelper
  with PulsarHelper {

  import UpdateProcessor._

  protected implicit val system: ActorSystem = context.system.toUntyped
  protected implicit val mat: ActorMaterializer = ActorMaterializer()
  protected implicit val ec: ExecutionContext = system.dispatcher
  protected implicit val db: PostgresProfile.backend.Database = DbExtension(system).db
  protected val log = Logging(system, getClass)

  protected val config: Config = system.settings.config

  protected val (userId: Int, tokenId: String) = entityId.split("_").toList match {
    case a :: s :: Nil ⇒ (a.toInt, s)
    case _ ⇒
      val e = new RuntimeException("Wrong actor name")
      log.error(e, e.getMessage)

  }

  initialize()

  override def onReceive: Receive = {
    case StreamGetDifference(replyTo) ⇒
      createSourceRef.map(_ pipeTo replyTo)

      Behaviors.same

    case GetDifference(replyTo, maxMessages) ⇒
      val result = updateSource.map(_.source.map(_.receivedUpdates.head).take(maxMessages).takeWithin(1.seconds)
        .runWith(Sink.seq))

      result.map(_.map(r ⇒ ResponseGetDifference(r)) pipeTo replyTo)

      if (result.isEmpty) log.error("Consumer not initialized yet")

      Behaviors.same

    case s: Subscribe ⇒
      s.replyTo ! Done

      Behaviors.same

    case Acknowledge(replyTo, messageId) ⇒
      val result = consumer.map(_.acknowledgeCumulativeAsync(getMessageId(messageId.get)))
      result.foreach(_ ⇒ replyTo ! Done)
      if (result.isEmpty) log.error("Consumer not initialized yet")

      Behaviors.same

    case Seek(replyTo, messageId) ⇒
      val result =
        for {
          id ← messageId
          cons ← consumer
        } yield cons.seekAsync(getMessageId(id)).andThen {
          case Failure(e) ⇒ log.error(e, e.getMessage)
        }

      result.getOrElse(Future.failed(new RuntimeException("Seek must be set"))).map(_ ⇒ Done) pipeTo replyTo
      if (result.isEmpty) log.error("Seek must be set")

      Behaviors.same

    case StopOffice ⇒
      log.debug("Stopping ...")
      Behaviors.stopped
    case _ ⇒
      Behaviors.unhandled
  }

  override def onSignal: PartialFunction[Signal, Behavior[UpdatePayload]] = {
    case PostStop ⇒
      log.info("UpdateProcessor was stopped")
      consumer.foreach(_.close())
      updateSource.foreach(_.control.close())
      this
  }

  /*
    Will be called after actor started
   */
  private def initialize(): Unit = {
    createSource()
  }

}
