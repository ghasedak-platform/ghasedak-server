package ir.sndu.server

import io.grpc.{ ManagedChannel, ManagedChannelBuilder }
import ir.sndu.server.auth.AuthServiceGrpc
import ir.sndu.server.contacts.ContactServiceGrpc
import ir.sndu.server.messaging.MessagingServiceGrpc
import ir.sndu.server.rpc.users.UserServiceGrpc

object GrpcStubs {
  private val channel: ManagedChannel =
    ManagedChannelBuilder.forAddress(CliConfigs.host, CliConfigs.port).usePlaintext(true).build
  val authStub = AuthServiceGrpc.blockingStub(channel)
  val messagingStub = MessagingServiceGrpc.blockingStub(channel)
  val contactsStub = ContactServiceGrpc.blockingStub(channel)
  val userStub = UserServiceGrpc.blockingStub(channel)
}
