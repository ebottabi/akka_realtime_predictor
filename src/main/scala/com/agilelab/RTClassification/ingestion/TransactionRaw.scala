package com.agilelab.RTClassification.ingestion

/**
 * Normalized transaction.
 * @param id unique transaction id
 * @param senderNdg ndg of the transaction sender
 * @param receiverNdg ndg of the transaction receiver
 * @param description description of the transaction
 * @param amount amount of money sent
 */
case class TransactionRaw (
                            id: String,
                            senderNdg: String,
                            receiverNdg: String,
                            description: String,
                            amount: Double
                            )
