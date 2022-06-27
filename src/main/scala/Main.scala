import autobid.Auction
import autobid.inputs.AuctionInputs.{AuctionAutoBidder, NewAuctionItem}
import play.api.libs.json.Json

import java.io.{File, FileInputStream}
import autobid.inputs.InputReads._
import autobid.inputs.JsEither._

object Main extends App {
  val autoBid = "Auto Bid System"
  val stream = new FileInputStream(new File(getClass.getResource("./auctionItems.json").getFile))
  val inputs = Json.parse(stream).validate[Seq[Either[AuctionItem, AuctionBidder]]]

  val items: Seq[AuctionItem] = inputs.get.filter(_.isLeft).map(_.left.get)
  val bidders: Seq[AuctionBidder] = inputs.get.filter(_.isRight).map(_.right.get)
  items.foreach(item=>{
    val bidderForItem = bidders.filter(x=> x.itemId.equals(item.itemId)).map(x=>AuctionAutoBidder(
      id = x.bidderId, startAmount = x.startingBid, maxAmount = x.maxBid, amountIncrement = x.bidIncrement, itemId = item.itemId
    ))
    Auction.submitAuction(NewAuctionItem(
      id = item.itemId, bidders = bidderForItem.toList, closingAt = item.timeOfAuction
    ))
  })

}