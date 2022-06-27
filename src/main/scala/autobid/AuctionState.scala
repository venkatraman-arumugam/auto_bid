package autobid

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import java.util.concurrent.atomic.AtomicInteger
import autobid.AuctionEntity._

case class AuctionState(phase: AuctionPhase, highestBid: Bid, highestCounterOffer: MoneyAmount) {
  def applyEvent(event: Event): AuctionState =
    event match {
      case BidRegistered(b) =>
        if (isHigherBid(b, highestBid))
          withNewHighestBid(b)
        else
          withTooLowBid(b)
      case _: WinnerDecided =>
        copy(phase = Closed)
    }

  def withNewHighestBid(bid: Bid): AuctionState = {
    require(phase != Closed)
    require(isHigherBid(bid, highestBid))
    copy(highestBid = bid, highestCounterOffer = highestBid.amount // keep last highest bid around
    )
  }

  def withTooLowBid(bid: Bid): AuctionState = {
    require(phase != Closed)
    require(isHigherBid(highestBid, bid))
    copy(highestCounterOffer = highestCounterOffer.max(bid.amount)) // update highest counter offer
  }

  def isHigherBid(first: Bid, second: Bid): Boolean =
    first.amount > second.amount ||
      (first.amount == second.amount && first.timestamp.isBefore(second.timestamp)) || // if equal, first one wins
      // If timestamps are equal, choose by dc where the offer was submitted
      // In real auctions, this last comparison should be deterministic but unpredictable, so that submitting to a
      // particular DC would not be an advantage.
      (first.amount == second.amount && first.timestamp.equals(second.timestamp))


//  def apply(initialBid: Bid): Behavior[Operation] = Behaviors.setup[Operation] { context =>
//    Behaviors.withTimers { timers =>
//      val pidCounter = new AtomicInteger(0)
//      Behaviors.receiveSignal(
//        Runner(context).apply(initialBid))
//
//    }
//  }


  //    Behaviors.setup[Operation] { context =>
  //    // define internal state
  //      val pidCounter = new AtomicInteger(0)
  ////      Behaviors.receiveMessage {
  //        Runner(context).apply(initialBid)
  ////      }
  //


}
