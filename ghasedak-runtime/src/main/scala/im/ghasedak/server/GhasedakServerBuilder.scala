package im.ghasedak.server

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.grpc.scaladsl.ServiceHandler
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.{ ActorMaterializer, Materializer }
import com.typesafe.config.Config
import im.ghasedak.rpc.auth.AuthServiceHandler
import im.ghasedak.rpc.chat.ChatServiceHandler
import im.ghasedak.rpc.contact.ContactServiceHandler
import im.ghasedak.rpc.messaging.MessagingServiceHandler
import im.ghasedak.rpc.test.TestServiceHandler
import im.ghasedak.rpc.update.UpdateServiceHandler
import im.ghasedak.rpc.user.UserServiceHandler
import im.ghasedak.server.frontend.Frontend
import im.ghasedak.server.rpc.auth.AuthServiceImpl
import im.ghasedak.server.rpc.chat.ChatServiceImpl
import im.ghasedak.server.rpc.contact.ContactServiceImpl
import im.ghasedak.server.rpc.messaging.MessagingServiceImpl
import im.ghasedak.server.rpc.test.TestServiceImpl
import im.ghasedak.server.rpc.update.UpdateServiceImpl
import im.ghasedak.server.rpc.user.UserServiceImpl

import scala.concurrent.{ ExecutionContext, Future }

object GhasedakServerBuilder {

  def start(implicit config: Config): ActorSystem = {
    implicit val system: ActorSystem =
      ActorSystem(config.getString("server-name"), config)

    if (config.getList("akka.cluster.seed-nodes").isEmpty) {
      Cluster(system).join(Cluster(system).selfAddress)
    }

    implicit val ex: ExecutionContext = system.dispatcher
    implicit val mat: Materializer = ActorMaterializer()

    Frontend.start(ServiceDescriptors.services)

    system
  }

}

object ServiceDescriptors {

  def services(implicit system: ActorSystem, ec: ExecutionContext, mat: Materializer): HttpRequest ⇒ Future[HttpResponse] = {
    // explicit types not needed but included in example for clarity
    val testService: PartialFunction[HttpRequest, Future[HttpResponse]] =
      TestServiceHandler.partial(new TestServiceImpl)
    val authService: PartialFunction[HttpRequest, Future[HttpResponse]] =
      AuthServiceHandler.partial(new AuthServiceImpl)
    val messagingService: PartialFunction[HttpRequest, Future[HttpResponse]] =
      MessagingServiceHandler.partial(new MessagingServiceImpl)
    val chatService: PartialFunction[HttpRequest, Future[HttpResponse]] =
      ChatServiceHandler.partial(new ChatServiceImpl)
    val contactService: PartialFunction[HttpRequest, Future[HttpResponse]] =
      ContactServiceHandler.partial(new ContactServiceImpl)
    val userService: PartialFunction[HttpRequest, Future[HttpResponse]] =
      UserServiceHandler.partial(new UserServiceImpl)
    val updateService: PartialFunction[HttpRequest, Future[HttpResponse]] =
      UpdateServiceHandler.partial(new UpdateServiceImpl)

    ServiceHandler.concatOrNotFound(
      testService,
      authService,
      messagingService,
      chatService,
      contactService,
      userService,
      updateService)
  }

}
