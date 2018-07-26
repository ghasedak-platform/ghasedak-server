package ir.sndu.persist.repo.history

import java.time.{ LocalDateTime, ZoneId, ZoneOffset }

import ir.sndu.persist.repo.TypeMapper._
import ir.sndu.server.model.history.HistoryMessage
import ir.sndu.server.peer.{ ApiPeer, ApiPeerType }
import slick.dbio.Effect.{ Read, Write }
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag
import slick.sql.{ FixedSqlAction, FixedSqlStreamingAction, SqlAction }

final class HistoryMessageTable(tag: Tag) extends Table[HistoryMessage](tag, "history_messages") {
  def userId = column[Int]("user_id", O.PrimaryKey)

  def peerType = column[Int]("peer_type", O.PrimaryKey)

  def peerId = column[Int]("peer_id", O.PrimaryKey)

  def date = column[LocalDateTime]("date", O.PrimaryKey)

  def senderUserId = column[Int]("sender_user_id", O.PrimaryKey)

  def randomId = column[Long]("random_id", O.PrimaryKey)

  def messageContentHeader = column[Int]("message_content_header")

  def messageContentData = column[Array[Byte]]("message_content_data")

  def deletedAt = column[Option[LocalDateTime]]("deleted_at")

  def * = (userId, peerType, peerId, date, senderUserId, randomId, messageContentHeader, messageContentData, deletedAt) <>
    (applyHistoryMessage.tupled, unapplyHistoryMessage)

  private def applyHistoryMessage: (Int, Int, Int, LocalDateTime, Int, Long, Int, Array[Byte], Option[LocalDateTime]) ⇒ HistoryMessage = {
    case (userId, peerType, peerId, date, senderUserId, randomId, messageContentHeader, messageContentData, deletedAt) ⇒
      HistoryMessage(
        userId = userId,
        peer = ApiPeer(ApiPeerType.fromValue(peerType), peerId),
        date = date,
        senderUserId = senderUserId,
        randomId = randomId,
        messageContentHeader = messageContentHeader,
        messageContentData = messageContentData,
        deletedAt = deletedAt)
  }

  private def unapplyHistoryMessage: HistoryMessage ⇒ Option[(Int, Int, Int, LocalDateTime, Int, Long, Int, Array[Byte], Option[LocalDateTime])] = { historyMessage ⇒
    HistoryMessage.unapply(historyMessage) map {
      case (userId, peer, date, senderUserId, randomId, messageContentHeader, messageContentData, deletedAt) ⇒
        (userId, peer.`type`.value, peer.id, date, senderUserId, randomId, messageContentHeader, messageContentData, deletedAt)
    }
  }
}

object HistoryMessageRepo {
  private val SharedUserId = 0

  val messages = TableQuery[HistoryMessageTable]
  val messagesC = Compiled(messages)

  val notDeletedMessages = messages.filter(_.deletedAt.isEmpty)

  val withoutServiceMessages = notDeletedMessages.filter(_.messageContentHeader =!= 2)

  def byUserIdPeer(userId: Rep[Int], peerType: Rep[Int], peerId: Rep[Int]) =
    notDeletedMessages
      .filter(m ⇒ m.userId === userId && m.peerType === peerType && m.peerId === peerId)

  def create(message: HistoryMessage): FixedSqlAction[Int, NoStream, Write] =
    messagesC += message

  def create(newMessages: Seq[HistoryMessage]): FixedSqlAction[Option[Int], NoStream, Write] =
    messagesC ++= newMessages

  def find(userId: Int, peer: ApiPeer, dateOpt: Option[LocalDateTime], limit: Int): FixedSqlStreamingAction[Seq[HistoryMessage], HistoryMessage, Read] = {
    val baseQuery = notDeletedMessages
      .filter(m ⇒
        m.userId === userId &&
          m.peerType === peer.`type`.value &&
          m.peerId === peer.id)

    val query = dateOpt match {
      case Some(date) ⇒
        baseQuery.filter(_.date <= date).sortBy(_.date.desc)
      case None ⇒
        baseQuery.sortBy(_.date.asc)
    }

    query.take(limit).result
  }

  private val afterC = Compiled { (userId: Rep[Int], peerType: Rep[Int], peerId: Rep[Int], date: Rep[LocalDateTime], limit: ConstColumn[Long]) ⇒
    byUserIdPeer(userId, peerType, peerId)
      .filter(_.date >= date)
      .sortBy(_.date.asc)
      .take(limit)
  }

  def findAfter(userId: Int, peer: ApiPeer, date: LocalDateTime, limit: Long) =
    afterC((userId, peer.`type`.value, peer.id, date, limit)).result

  private val metaAfterC = Compiled { (userId: Rep[Int], peerType: Rep[Int], peerId: Rep[Int], date: Rep[LocalDateTime], limit: ConstColumn[Long]) ⇒
    byUserIdPeer(userId, peerType, peerId)
      .filter(_.date > date)
      .sortBy(_.date.asc)
      .take(limit)
      .map(hm ⇒ (hm.randomId, hm.date, hm.senderUserId, hm.messageContentHeader))
  }

  def findMetaAfter(userId: Int, peer: ApiPeer, date: LocalDateTime, limit: Long) =
    metaAfterC((userId, peer.`type`.value, peer.id, date, limit)).result

  private val beforeC = Compiled { (userId: Rep[Int], peerId: Rep[Int], peerType: Rep[Int], date: Rep[LocalDateTime], limit: ConstColumn[Long]) ⇒
    byUserIdPeer(userId, peerType, peerId)
      .filter(_.date <= date)
      .sortBy(_.date.asc)
      .take(limit)
  }

  private val beforeExclC = Compiled { (userId: Rep[Int], peerId: Rep[Int], peerType: Rep[Int], date: Rep[LocalDateTime], limit: ConstColumn[Long]) ⇒
    byUserIdPeer(userId, peerType, peerId)
      .filter(_.date < date)
      .sortBy(_.date.asc)
      .take(limit)
  }

  private val byUserIdPeerRidC = Compiled { (userId: Rep[Int], peerType: Rep[Int], peerId: Rep[Int], randomId: Rep[Long]) ⇒
    byUserIdPeer(userId, peerType, peerId).filter(_.randomId === randomId)
  }

  def existstWithRandomId(userId: Int, peer: ApiPeer, randomId: Long): DBIO[Boolean] =
    byUserIdPeerRidC.applied((userId, peer.`type`.value, peer.id, randomId)).exists.result

  def findBefore(userId: Int, peer: ApiPeer, date: LocalDateTime, limit: Long) =
    beforeC((userId, peer.id, peer.`type`.value, date, limit)).result

  def findBidi(userId: Int, peer: ApiPeer, date: LocalDateTime, limit: Long) =
    (beforeExclC.applied((userId, peer.`type`.value, peer.id, date, limit)) ++
      afterC.applied((userId, peer.`type`.value, peer.id, date, limit))).result

  def findBySender(senderUserId: Int, peer: ApiPeer, randomId: Long): FixedSqlStreamingAction[Seq[HistoryMessage], HistoryMessage, Read] =
    notDeletedMessages.filter(m ⇒ m.senderUserId === senderUserId && m.peerType === peer.`type`.value && m.peerId === peer.id && m.randomId === randomId).result

  def findUserIds(peer: ApiPeer, randomIds: Set[Long]): DBIO[Seq[Int]] =
    notDeletedMessages
      .filter(m ⇒ m.peerType === peer.`type`.value && m.peerId === peer.id && (m.randomId inSet randomIds))
      .map(_.userId)
      .result

  def findNewest(userId: Int, peer: ApiPeer): SqlAction[Option[HistoryMessage], NoStream, Read] =
    notDeletedMessages
      .filter(m ⇒ m.userId === userId && m.peerType === peer.`type`.value && m.peerId === peer.id)
      .sortBy(_.date.desc)
      .take(1)
      .result
      .headOption

  def find(userId: Int, peer: ApiPeer): FixedSqlStreamingAction[Seq[HistoryMessage], HistoryMessage, Read] =
    notDeletedMessages
      .filter(m ⇒ m.userId === userId && m.peerType === peer.`type`.value && m.peerId === peer.id)
      .sortBy(_.date.desc)
      .result

  def find(userId: Int, peer: ApiPeer, randomIds: Set[Long]): FixedSqlStreamingAction[Seq[HistoryMessage], HistoryMessage, Read] =
    notDeletedMessages.filter(m ⇒ m.userId === userId && m.peerType === peer.`type`.value && m.peerId === peer.id && (m.randomId inSet randomIds)).result

  def updateContentAll(userIds: Set[Int], randomId: Long, peerType: ApiPeerType, peerIds: Set[Int],
                       messageContentHeader: Int, messageContentData: Array[Byte]): FixedSqlAction[Int, NoStream, Write] =
    notDeletedMessages
      .filter(m ⇒ m.randomId === randomId && m.peerType === peerType.value)
      .filter(_.peerId inSet peerIds)
      .filter(_.userId inSet userIds)
      .map(m ⇒ (m.messageContentHeader, m.messageContentData))
      .update((messageContentHeader, messageContentData))

  def getUnreadCount(historyOwner: Int, clientUserId: Int, peer: ApiPeer, lastReadAt: LocalDateTime, noServiceMessages: Boolean = false): FixedSqlAction[Int, NoStream, Read] =
    (if (noServiceMessages) withoutServiceMessages else notDeletedMessages)
      .filter(m ⇒ m.userId === historyOwner && m.peerType === peer.`type`.value && m.peerId === peer.id)
      .filter(m ⇒ m.date > lastReadAt && m.senderUserId =!= clientUserId)
      .length
      .result

  def deleteAll(userId: Int, peer: ApiPeer): FixedSqlAction[Int, NoStream, Write] = {
    notDeletedMessages
      .filter(m ⇒ m.userId === userId && m.peerType === peer.`type`.value && m.peerId === peer.id)
      .map(_.deletedAt)
      .update(Some(LocalDateTime.now(ZoneId.systemDefault())))
  }

  def delete(userId: Int, peer: ApiPeer, randomIds: Set[Long]) =
    notDeletedMessages
      .filter(m ⇒ m.userId === userId && m.peerType === peer.`type`.value && m.peerId === peer.id)
      .filter(_.randomId inSet randomIds)
      .map(_.deletedAt)
      .update(Some(LocalDateTime.now(ZoneId.systemDefault())))
}
