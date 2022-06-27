package autobid

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.adapter._
import akka.actor.{Actor, ActorSystem, Props}
import autobid.AuctionEntity.{Bid, Operation}
import autobid.inputs.AuctionInputs._

import java.time.Instant
import scala.collection.mutable


class AuctionRunner extends Actor {
  var auctionEntities: mutable.Map[String, ActorRef[Operation]] = mutable.Map.empty[String, ActorRef[Operation]]
  var autoBidderRunner: mutable.Map[String, ActorRef[Operation]] = mutable.Map.empty[String, ActorRef[Operation]]
  val system = ActorSystem("AuctionRunner")
  def receive: Receive = {
      case message: NewAuctionItem =>
        val closingAt = Instant.now().plusSeconds(message.closingAt)
        val auctionEntityRef: ActorRef[Any] = context.spawn(AuctionEntity(name = message.id, initialBid = Bid(
          bidId = message.initialBid.id, amount = message.initialBid.startAmount, timestamp = Instant.now()
        ), closingAt = closingAt), message.id)
        auctionEntities(message.id) =  auctionEntityRef

        val autoBidderRunnerRef = system.actorOf(Props(classOf[AutoBidderRunner], auctionEntityRef, message.bidders, closingAt, message.id), message.id)
        autoBidderRunner(message.id) =  autoBidderRunnerRef

        autoBidderRunnerRef ! "Offer"
    }
}