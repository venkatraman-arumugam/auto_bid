package autobid

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.persistence.typed.{PersistenceId, RecoveryCompleted}

import java.time.Instant
import scala.concurrent.duration.DurationLong
import autobid.AuctionEntity._

object AuctionEntity {

  type MoneyAmount = Int
  case class Bid(bidId: String, amount: MoneyAmount, timestamp: Instant)

  sealed trait Event extends CborSerializable
  final case class BidRegistered(bid: Bid) extends Event
  final case class AuctionFinished() extends Event
  final case class WinnerDecided(winningBid: Bid, highestCounterOffer: MoneyAmount) extends Event

  sealed trait AuctionPhase
  case object Running extends AuctionPhase
  case object Closing extends AuctionPhase
  case object Closed extends AuctionPhase

  sealed trait Operation
  case object Finish extends Operation
  final case class OfferBid(bidder: String, offer: Int) extends Operation
  final case class GetHighestBid(replyTo: ActorRef[Bid]) extends Operation
  final case class IsClosed(replyTo: ActorRef[Boolean]) extends Operation
  private case object Close extends Operation

  case class AuctionItemState(phase: AuctionPhase, highestBid: Bid, highestCounterOffer: MoneyAmount) extends CborSerializable {

    def applyEvent(event: Event): AuctionItemState =
      event match {
        case BidRegistered(b: Bid) =>
          if (isHigherBid(b, highestBid))
            withNewHighestBid(b)
          else
            withTooLowBid(b)
        case AuctionFinished() =>
          copy(phase = Closed)
        case _ =>
          copy(phase = Closed)
      }

    def withNewHighestBid(bid: Bid): AuctionItemState = {
      require(phase != Closed)
      require(isHigherBid(bid, highestBid))
      copy(highestBid = bid, highestCounterOffer = highestBid.amount // keep last highest bid around
      )
    }

    def withTooLowBid(bid: Bid): AuctionItemState = {
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
  }


  def apply(name: String,
            initialBid: Bid,
            closingAt: Instant): Behavior[Operation] = Behaviors.setup[Operation] { context =>
              Behaviors.withTimers { timers =>
                new AuctionEntity(context, timers, closingAt).behavior(name, initialBid)
              }
            }
}
class AuctionEntity(context: ActorContext[Operation],
                    timers: TimerScheduler[AuctionEntity.Operation],
                    closingAt: Instant) extends CborSerializable {
  private def behavior(name: String, initialBid: Bid):
  EventSourcedBehavior[Operation, Event, AuctionItemState] = EventSourcedBehavior[Operation, Event, AuctionItemState](
    PersistenceId.ofUniqueId(name),
    AuctionItemState(phase = Running, highestBid = initialBid, highestCounterOffer = initialBid.amount),
    operationHandler,
    eventHandler).receiveSignal {
    case (state, RecoveryCompleted) => recoveryCompleted(state)
  }


  private def recoveryCompleted(state: AuctionItemState): Unit = {
    if (shouldClose(state))
      context.self ! Close
    val millisUntilClosing = closingAt.toEpochMilli - System.currentTimeMillis()
    timers.startSingleTimer(Finish, millisUntilClosing.millis)
  }

  private def shouldClose(state: AuctionItemState): Boolean = {
    state.phase match {
      case Closed =>
        true
      case _ =>
        false
    }
  }
  def eventHandler(auctionState: AuctionItemState, event: Event): AuctionItemState = {

    val newState = auctionState.applyEvent(event)
    //        context.log.infoN("Applying event {}. New start {}", event, newState)
    //        if (!replicationContext.recoveryRunning) {
    //          eventTriggers(event, newState)
    //        }
    newState

  }


  def operationHandler(auctionState: AuctionItemState, operation: Operation): Effect[Event, AuctionItemState] = {
    auctionState.phase match {
      case Closing | Closed =>
        operation match {
          case GetHighestBid(replyTo) =>
            replyTo ! auctionState.highestBid.copy(amount = auctionState.highestCounterOffer) // TODO this is not as described
            Effect.none
          case IsClosed(replyTo) =>
            replyTo ! (auctionState.phase == Closed)
            Effect.none
          case Finish =>
            context.log.info("Finish")
            Effect.persist(AuctionFinished())
          case Close =>
            context.log.info("Close")
            require(shouldClose(auctionState))
            // TODO send email (before or after persisting)
            Effect.persist(WinnerDecided(auctionState.highestBid, auctionState.highestCounterOffer))
          case _: OfferBid =>
            // auction finished, no more bids accepted
            Effect.unhandled
        }
      case Running =>
        operation match {
          case OfferBid(bidder, offer) =>
            Effect.persist(
              BidRegistered(
                Bid(
                  bidder,
                  offer,
                  Instant.ofEpochMilli(System.currentTimeMillis()))))
          case GetHighestBid(replyTo) =>
            replyTo ! auctionState.highestBid
            Effect.none
          case Finish =>
            Effect.persist(AuctionFinished())
          case Close =>
            context.log.warn("Premature close")
            // Close should only be triggered when we have already finished
            Effect.unhandled
          case IsClosed(replyTo) =>
            replyTo ! false
            Effect.none
        }
    }
  }
}

//class AuctionEntity(context: ActorContext[AuctionEntity.Operation],
//                     timers: TimerScheduler[AuctionEntity.Operation],
//                     closingAt: Instant) {
//  import AuctionEntity._
//  private def behavior(initialBid: AuctionEntity.Bid): EventSourcedBehavior[Command, Event, AuctionState] =
//    EventSourcedBehavior(
//      replicationContext.persistenceId,
//      AuctionState(phase = Running, highestBid = initialBid, highestCounterOffer = initialBid.offer),
//      commandHandler,
//      eventHandler).receiveSignal {
//      case (state, RecoveryCompleted) => recoveryCompleted(state)
//    }
//
//  private def recoveryCompleted(state: AuctionState): Unit = {
//    if (shouldClose(state))
//      context.self ! Close
//
//    val millisUntilClosing = closingAt.toEpochMilli - replicationContext.currentTimeMillis()
//    timers.startSingleTimer(Finish, millisUntilClosing.millis)
//  }
//  //#setup
//
//  //#command-handler
//  def commandHandler(state: AuctionState, command: Command): Effect[Event, AuctionState] = {
//    state.phase match {
//      case Closing(_) | Closed =>
//        command match {
//          case GetHighestBid(replyTo) =>
//            replyTo ! state.highestBid.copy(offer = state.highestCounterOffer) // TODO this is not as described
//            Effect.none
//          case IsClosed(replyTo) =>
//            replyTo ! (state.phase == Closed)
//            Effect.none
//          case Finish =>
//            context.log.info("Finish")
//            Effect.persist(AuctionFinished(replicationContext.replicaId))
//          case Close =>
//            context.log.info("Close")
//            require(shouldClose(state))
//            // TODO send email (before or after persisting)
//            Effect.persist(WinnerDecided(replicationContext.replicaId, state.highestBid, state.highestCounterOffer))
//          case _: OfferBid =>
//            // auction finished, no more bids accepted
//            Effect.unhandled
//        }
//      case Running =>
//        command match {
//          case OfferBid(bidder, offer) =>
//            Effect.persist(
//              BidRegistered(
//                Bid(
//                  bidder,
//                  offer,
//                  Instant.ofEpochMilli(replicationContext.currentTimeMillis()),
//                  replicationContext.replicaId)))
//          case GetHighestBid(replyTo) =>
//            replyTo ! state.highestBid
//            Effect.none
//          case Finish =>
//            Effect.persist(AuctionFinished(replicationContext.replicaId))
//          case Close =>
//            context.log.warn("Premature close")
//            // Close should only be triggered when we have already finished
//            Effect.unhandled
//          case IsClosed(replyTo) =>
//            replyTo ! false
//            Effect.none
//        }
//    }
//  }
//  //#command-handler
//
//  //#event-handler
//  def eventHandler(state: AuctionState, event: Event): AuctionState = {
//
//    val newState = state.applyEvent(event)
//    context.log.infoN("Applying event {}. New start {}", event, newState)
//    if (!replicationContext.recoveryRunning) {
//      eventTriggers(event, newState)
//    }
//    newState
//
//  }
//
//  //#event-handler
//
//  //#event-triggers
//  private def eventTriggers(event: Event, newState: AuctionState): Unit = {
//    event match {
//      case finished: AuctionFinished =>
//        newState.phase match {
//          case Closing(alreadyFinishedAtDc) =>
//            context.log.infoN(
//              "AuctionFinished at {}, already finished at [{}]",
//              finished.atReplica,
//              alreadyFinishedAtDc.mkString(", "))
//            if (alreadyFinishedAtDc(replicationContext.replicaId)) {
//              if (shouldClose(newState)) context.self ! Close
//            } else {
//              context.log.info("Sending finish to self")
//              context.self ! Finish
//            }
//
//          case _ => // no trigger for this state
//        }
//      case _ => // no trigger for this event
//    }
//  }
//
//  private def shouldClose(state: AuctionState): Boolean = {
//    responsibleForClosing && (state.phase match {
//      case Closing(alreadyFinishedAtDc) =>
//        val allDone = allReplicas.diff(alreadyFinishedAtDc).isEmpty
//        if (!allDone) {
//          context.log.info2(
//            s"Not closing auction as not all DCs have reported finished. All DCs: {}. Reported finished {}",
//            allReplicas,
//            alreadyFinishedAtDc)
//        }
//        allDone
//      case _ =>
//        false
//    })
//  }
//  //#event-triggers
//
//  //#setup
//}

//class AuctionEntity{
//
//  import AuctionEntity._
//  import context._
//
//  private var state = AuctionItemState()
//
//  override def persistenceId: String = "blog"
//
//  override def receiveCommand: Receive = {
//    case GetHighestBid(replyTo) =>
//      replyTo ! state.highestBid
//    case AddPost(content) =>
//      handleEvent(PostAdded(PostId(), content)) pipeTo sender()
//      ()
//    case UpdatePost(id, content) =>
//      state(id) match {
//        case response @ Left(_) => sender() ! response
//        case Right(_) =>
//          handleEvent(PostUpdated(id, content)) pipeTo sender()
//          ()
//      }
//  }
//
//  private def handleEvent[E <: Event](e: => E): Future[E] = {
//    val p = Promise[E]
//    persist(e) { event =>
//      p.success(event)
//      state += event
//      system.eventStream.publish(event)
//      if (lastSequenceNr != 0 && lastSequenceNr % 1000 == 0)
//        saveSnapshot(state)
//    }
//    p.future
//  }
//
//  override def receiveRecover: Receive = {
//    case event: BlogEvent =>
//      state += event
//    case SnapshotOffer(_, snapshot: BlogState) =>
//      state = snapshot
//  }
//
//}
