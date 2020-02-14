package com.wavesplatform.it.sync

import com.wavesplatform.dex.model.MatcherModel.Normalization

package object orders {
  val percentFee           = 25

  implicit class DoubleOps(value: Double) {
    val TN, eth, btc: Long = Normalization.normalizeAmountAndFee(value, 8)
    val usd: Long             = Normalization.normalizePrice(value, 8, 2)
  }
}
