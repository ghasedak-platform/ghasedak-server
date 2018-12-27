package ir.sndu.server.rpc.auth.helper

import java.util.UUID

import akka.event.LoggingAdapter
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import io.grpc.{ Context, Metadata, StatusRuntimeException }
import ir.sndu.persist.repo.auth.AuthTokenRepo
import ir.sndu.server.model.auth.AuthToken
import ir.sndu.server.rpc.Constant
import ir.sndu.server.rpc.auth.AuthRpcErrors
import ir.sndu.server.rpc.common.CommonRpcError._
import slick.jdbc.PostgresProfile

import scala.concurrent.{ ExecutionContext, Future }

object AuthTokenHelper {

  case class ClientData(userId: Int, orgId: Int, tokenId: String, tokenKey: String, token: String)

  def generateClientData(jwt: DecodedJWT, tokenId: String, tokenKey: String, token: String): ClientData = {
    ClientData(
      Integer.parseInt(jwt.getClaim("userId") asString ()),
      Integer.parseInt(jwt.getClaim("orgId") asString ()),
      tokenId, tokenKey, token)
  }

}

trait AuthTokenHelper {

  import AuthRpcErrors._
  import AuthTokenHelper._

  implicit val ec: ExecutionContext

  val log: LoggingAdapter

  val db: PostgresProfile.backend.Database

  private def authorize[T](tokenOpt: Option[String])(f: ClientData ⇒ Future[T]): Future[T] = {
    tokenOpt match {
      case None ⇒ Future.failed(MissingToken)
      case Some(token) ⇒
        try {
          val tokenId = JWT.decode(token).getClaim("tokenId").asString()
          // todo: Use cache
          db.run(AuthTokenRepo.find(tokenId)) flatMap {
            case None ⇒ Future.failed(InvalidToken)
            case Some(tokenKey) ⇒
              val algorithm = Algorithm.HMAC512(tokenKey)
              val verifier = JWT.require(algorithm).build()
              val jwt = verifier.verify(token)
              val clientData = generateClientData(jwt, tokenId, tokenKey, token)
              f(clientData)
          } recoverWith {
            case ex: JWTVerificationException ⇒
              log.error(ex, "invalid token {}", token)
              Future.failed(InvalidToken)
            case ex: StatusRuntimeException ⇒
              Future.failed(ex)
            case ex: Throwable ⇒
              log.error(ex, "Error in handling request")
              Future.failed(InternalError)
          }
        } catch {
          case _: JWTVerificationException ⇒
            log.warning("invalid token {}", token)
            Future.failed(InvalidToken)
        }
    }
  }

  def authorize[T](f: ClientData ⇒ Future[T]): Future[T] =
    authorize(Option(Constant.TOKEN_CONTEXT_KEY.get()))(f)

  def generateToken(userId: Int, orgId: Int): Future[(String, String)] = {
    val tokenKey = UUID.randomUUID().toString
    val algorithm = Algorithm.HMAC512(tokenKey)
    val tokenId = UUID.randomUUID().toString + userId
    val tokenStr = JWT.create()
      .withClaim("tokenId", tokenId)
      .withClaim("userId", userId.toString)
      .withClaim("orgId", orgId.toString)
      .sign(algorithm)
    val token = AuthToken(tokenId, tokenKey, None)
    db.run(AuthTokenRepo.create(token)) map (_ ⇒ (tokenId, tokenStr))
  }

}