package autobid.inputs

import java.time.Instant

object AuctionInputs {
  trait Command
  type Amount = Int
  final case class NewAuctionItem(id: String, bidders: List[AuctionAutoBidder], closingAt: Long, initialBid: AuctionAutoBidder = null) extends Command {
    def apply(): NewAuctionItem = {
      copy(initialBid = Some(bidders).map(_.maxBy(_.startAmount)).get)
    }
  }
  final case class AuctionAutoBidder(id: String, startAmount: Amount, maxAmount: Amount, amountIncrement: Int, itemId: String)
//  final case class InitialBid(id: String, startAmount: Amount, maxAmount: Amount)
}