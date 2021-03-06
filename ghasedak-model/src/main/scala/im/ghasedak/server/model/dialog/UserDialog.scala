package im.ghasedak.server.model.dialog

import java.time.{ LocalDateTime, ZoneId, ZoneOffset }

import im.ghasedak.api.messaging.{ ApiDialog, ApiMessage, ApiMessageContainer }
import im.ghasedak.api.peer.ApiPeer
import im.ghasedak.server.model.history.HistoryMessage

final case class DialogCommon(
  dialogId:        String,
  lastMessageDate: LocalDateTime,
  lastMessageSeq:  Int,
  lastReceivedSeq: Int,
  lastReadSeq:     Int)

final case class UserDialog(
  userId:               Int,
  peer:                 ApiPeer,
  ownerLastReceivedSeq: Int,
  ownerLastReadSeq:     Int,
  createdAt:            LocalDateTime)

final case class Dialog(
  userId:               Int,
  peer:                 ApiPeer,
  ownerLastReceivedSeq: Int,
  ownerLastReadSeq:     Int,
  lastMessageSeq:       Int,
  lastMessageDate:      LocalDateTime,
  lastReceivedSeq:      Int,
  lastReadSeq:          Int,
  createdAt:            LocalDateTime) {

  def toApi(msgOpt: Option[HistoryMessage]): ApiDialog = {
    val history = msgOpt.getOrElse(HistoryMessage.empty(userId, peer, lastMessageDate))
    val msgDate = lastMessageDate.toInstant(ZoneOffset.UTC).toEpochMilli
    ApiDialog(
      Some(peer),
      lastMessageSeq - ownerLastReadSeq,
      msgDate,
      Some(ApiMessageContainer(
        history.senderUserId,
        history.sequenceNr,
        msgDate,
        Some(ApiMessage.parseFrom(history.messageContentData)))))
  }

}

object Dialog {

  def from(common: DialogCommon, dialog: UserDialog): Dialog =
    Dialog(
      userId = dialog.userId,
      peer = dialog.peer,
      ownerLastReceivedSeq = dialog.ownerLastReceivedSeq,
      ownerLastReadSeq = dialog.ownerLastReadSeq,
      lastMessageDate = common.lastMessageDate,
      lastMessageSeq = common.lastMessageSeq,
      lastReceivedSeq = common.lastReceivedSeq,
      lastReadSeq = common.lastReadSeq,
      createdAt = dialog.createdAt)

}