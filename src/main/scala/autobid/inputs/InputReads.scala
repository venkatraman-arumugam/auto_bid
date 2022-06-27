package autobid.inputs

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json._

object InputReads {
  case class AuctionItem(itemId: String, inputType: String, itemName: String, description: String, timeOfAuction: Long)
  case class AuctionBidder(bidderId: String, inputType: String, bidderName: String, itemName: String, itemId: String, startingBid: Int, maxBid: Int, bidIncrement: Int )


  implicit val auctionItemRead: Reads[AuctionItem] = (
    (JsPath \ "id").read[String] and
      (JsPath \ "type").read[String] and
      (JsPath \ "name").read[String] and
      (JsPath \ "description").read[String] and
      (JsPath \ "timeOfAuction").read[Long]
    )(AuctionItem.apply _)

  implicit val auctionBidderRead: Reads[AuctionBidder] = (
    (JsPath \ "id").read[String] and
      (JsPath \ "type").read[String] and
      (JsPath \ "name").read[String] and
      (JsPath \ "itemName").read[String] and
      (JsPath \ "item").read[String] and
      (JsPath \ "startingBid").read[Int] and
      (JsPath \ "maxBid").read[Int] and
      (JsPath \ "bidIncrement").read[Int]
    )(AuctionBidder.apply _)

}
