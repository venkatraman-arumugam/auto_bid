package autobid

import akka.actor.{Actor, ActorSystem, Kill, Props, Timers}
import akka.actor.typed.ActorRef
import akka.event.Logging
import autobid.AuctionEntity.OfferBid
import autobid.inputs.AuctionInputs.AuctionAutoBidder

import java.time.Instant
import scala.concurrent.duration.DurationLong



case class AutoBidderRunner(auctionEntity: ActorRef[Any], autoBidders: List[AuctionAutoBidder], closingAt: Instant, itemId: String) extends Actor with Timers {

  val timeToClose: Long =  closingAt.toEpochMilli - System.currentTimeMillis()
  timers.startSingleTimer(itemId+"_AutoBidderRunner", "Stop", timeToClose.seconds)

  val system = ActorSystem(itemId)
  val runners = autoBidders.map(x=>{
    system.actorOf(Props(classOf[AuctionAutoBidderActor], auctionEntity, x), x.id)
  })

  override def receive: Receive = {
    case "Stop" => runners.map(x => x ! Kill)
    case "Offer" => runners.map(x => x ! "OfferBid")
  }
}


case class AuctionAutoBidderActor(auctionEntity: ActorRef[Any], autoBidder: AuctionAutoBidder) extends Actor {
  var currentAmt = autoBidder.startAmount
  val log = Logging(this)

  override def receive: Receive = {
    case "OfferBid" =>
      auctionEntity ! OfferBid(bidder = autoBidder.id, offer = currentAmt)
      self ! "Increment"
    case "Increment" =>
      if (currentAmt + autoBidder.amountIncrement <= autoBidder.maxAmount) {
        currentAmt += autoBidder.amountIncrement
        self ! "OfferBid"
      } else {
        log.info(s"${autoBidder.id} reached max amount")
      }
  }
}