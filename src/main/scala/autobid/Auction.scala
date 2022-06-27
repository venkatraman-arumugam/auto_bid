package autobid

import akka.actor.{ActorSystem, Props}
import autobid.inputs.AuctionInputs.NewAuctionItem

object Auction {
  val system = ActorSystem("AuctionSystem")
  val auctionRunner = system.actorOf(Props[AuctionRunner], name = "AuctionRunner")

  def submitAuction(newAuctionItem: NewAuctionItem): Unit = {
    auctionRunner ! newAuctionItem
  }
}
