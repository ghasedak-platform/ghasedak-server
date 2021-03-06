package im.ghasedak.server.repo.auth

import java.time.{ LocalDateTime, ZoneOffset }

import com.github.tminglei.slickpg.ExPostgresProfile.api._
import im.ghasedak.server.repo.TypeMapper._
import im.ghasedak.server.model.auth.AuthToken
import slick.dbio.Effect
import slick.lifted.Tag
import slick.sql.{ FixedSqlAction, SqlAction }

class AuthTokenTable(tag: Tag) extends Table[AuthToken](tag, "auth_tokens") {

  def tokenId = column[String]("token_id", O.PrimaryKey)

  def tokenKey = column[String]("token_key")

  def deletedAt = column[Option[LocalDateTime]]("deleted_at")

  override def * = (tokenId, tokenKey, deletedAt) <> (AuthToken.tupled, AuthToken.unapply)

}

object AuthTokenRepo {

  val tokens = TableQuery[AuthTokenTable]

  val active = tokens.filter(_.deletedAt.isEmpty)

  def create(token: AuthToken): FixedSqlAction[Int, NoStream, Effect.Write] =
    tokens += token

  def find(tokenId: String): SqlAction[Option[String], NoStream, Effect.Read] =
    active.filter(_.tokenId === tokenId).map(_.tokenKey).result.headOption

  def delete(tokenId: String): FixedSqlAction[Int, NoStream, Effect.Write] =
    tokens.filter(_.tokenId === tokenId).map(_.deletedAt)
      .update(Some(LocalDateTime.now(ZoneOffset.UTC)))

}