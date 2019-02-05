package com.wavesplatform.transaction.transfer

import com.google.common.primitives.Bytes
import com.wavesplatform.account.{AddressOrAlias, PrivateKeyAccount, PublicKeyAccount}
import com.wavesplatform.common.state.ByteStr
import com.wavesplatform.common.utils.EitherExt2
import com.wavesplatform.crypto
import com.wavesplatform.transaction._
import com.wavesplatform.transaction.description._
import monix.eval.Coeval

import scala.util.Try

case class TransferTransactionV1 private (assetId: Option[AssetId],
                                          sender: PublicKeyAccount,
                                          recipient: AddressOrAlias,
                                          amount: Long,
                                          timestamp: Long,
                                          feeAssetId: Option[AssetId],
                                          fee: Long,
                                          attachment: Array[Byte],
                                          signature: ByteStr)
    extends TransferTransaction
    with SignedTransaction
    with FastHashId {

  override val builder: TransactionParser     = TransferTransactionV1
  override val bodyBytes: Coeval[Array[Byte]] = Coeval.evalOnce(Array(builder.typeId) ++ bytesBase())
  override val bytes: Coeval[Array[Byte]]     = Coeval.evalOnce(Bytes.concat(Array(builder.typeId), signature.arr, bodyBytes()))
  override val version: Byte                  = 1: Byte
}

object TransferTransactionV1 extends TransactionParserFor[TransferTransactionV1] with TransactionParser.HardcodedVersion1 {

  override val typeId: Byte = TransferTransaction.typeId

  override protected def parseTail(bytes: Array[Byte]): Try[TransactionT] = {
    byteTailDescription.deserializeFromByteArray(bytes).flatMap { tx =>
      TransferTransaction
        .validate(tx)
        .map(_ => tx)
        .foldToTry
    }
  }

  def create(assetId: Option[AssetId],
             sender: PublicKeyAccount,
             recipient: AddressOrAlias,
             amount: Long,
             timestamp: Long,
             feeAssetId: Option[AssetId],
             feeAmount: Long,
             attachment: Array[Byte],
             signature: ByteStr): Either[ValidationError, TransactionT] = {
    TransferTransaction
      .validate(amount, feeAmount, attachment)
      .map(_ => TransferTransactionV1(assetId, sender, recipient, amount, timestamp, feeAssetId, feeAmount, attachment, signature))
  }

  def signed(assetId: Option[AssetId],
             sender: PublicKeyAccount,
             recipient: AddressOrAlias,
             amount: Long,
             timestamp: Long,
             feeAssetId: Option[AssetId],
             feeAmount: Long,
             attachment: Array[Byte],
             signer: PrivateKeyAccount): Either[ValidationError, TransactionT] = {
    create(assetId, sender, recipient, amount, timestamp, feeAssetId, feeAmount, attachment, ByteStr.empty).right.map { unsigned =>
      unsigned.copy(signature = ByteStr(crypto.sign(signer, unsigned.bodyBytes())))
    }
  }

  def selfSigned(assetId: Option[AssetId],
                 sender: PrivateKeyAccount,
                 recipient: AddressOrAlias,
                 amount: Long,
                 timestamp: Long,
                 feeAssetId: Option[AssetId],
                 feeAmount: Long,
                 attachment: Array[Byte]): Either[ValidationError, TransactionT] = {
    signed(assetId, sender, recipient, amount, timestamp, feeAssetId, feeAmount, attachment, sender)
  }

  val byteTailDescription: ByteEntity[TransferTransactionV1] = {
    (SignatureBytes(tailIndex(1), "Signature") ~
      ConstantByte(tailIndex(2), value = typeId, name = "Transaction type") ~
      PublicKeyAccountBytes(tailIndex(3), "Sender's public key") ~
      OptionAssetIdBytes(tailIndex(4), "Asset ID") ~
      OptionAssetIdBytes(tailIndex(5), "Fee's asset ID") ~
      LongBytes(tailIndex(6), "Timestamp") ~
      LongBytes(tailIndex(7), "Amount") ~
      LongBytes(tailIndex(8), "Fee") ~
      AddressOrAliasBytes(tailIndex(9), "Recipient") ~
      BytesArrayUndefinedLength(tailIndex(10), "Attachment")).map {
      case (((((((((signature, txId), senderPublicKey), assetId), feeAssetId), timestamp), amount), fee), recipient), attachments) =>
        require(txId == typeId, s"Signed tx id is not match")
        TransferTransactionV1(
          assetId = assetId,
          sender = senderPublicKey,
          recipient = recipient,
          amount = amount,
          timestamp = timestamp,
          feeAssetId = feeAssetId,
          fee = fee,
          attachment = attachments,
          signature = signature
        )
    }
  }
}
