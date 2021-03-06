package im.ghasedak.server.model.contact

final case class UserContact(
  ownerUserId:   Int,
  contactUserId: Int,
  localName:     String,
  hasPhone:      Boolean = false,
  hasEmail:      Boolean = false,
  isDeleted:     Boolean = false)