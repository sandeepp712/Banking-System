package com.bank.domain;

import java.util.Objects;
import java.time.Instant;
import java.util.UUID;

public final class Transaction {
    private final String fromAccountId;
    private final String toAccountId;
    private final Money amount;
    private final String transactionId;
    private final Instant timestamp;
    private final TransactionStatus status;
    private final String idempotencyKey;

    private Transaction(String fromAccountId, String toAccountId, Money amount, String transactionId, Instant timestamp, TransactionStatus status,String idempotencyKey) {
        this.fromAccountId=Objects.requireNonNull(fromAccountId,"fromAccountId must not be null");
        this.toAccountId=Objects.requireNonNull(toAccountId,"toAccountId must not be null");
        this.amount=Objects.requireNonNull(amount,"amount must not be null");
        this.transactionId=Objects.requireNonNull(transactionId,"transactionId must not be null");
        this.timestamp=Objects.requireNonNull(timestamp,"timestamp must not be null");
        this.status=Objects.requireNonNull(status,"status must not be null");
        this.idempotencyKey=Objects.requireNonNull(idempotencyKey,"idempotencyKey must not be null");

        //Business invariant: from and to cannot be the same account
        if(fromAccountId.equals(toAccountId)){
            throw new IllegalArgumentException("fromAccountId and toAccountId must not be the same");
        }

        //Amount must be positive
        if(!amount.isPositive() && !amount.isZero()){
            throw new IllegalArgumentException("amount must be positive"+amount);
        }
    }

    // Getters
    public String getFromAccountId() {return this.fromAccountId;}
    public String getToAccountId() {return this.toAccountId;}
    public Money getAmount() {return this.amount;}
    public String getTransactionId() {return this.transactionId;}
    public Instant getTimestamp() {return this.timestamp;}
    public TransactionStatus getStatus() {return this.status;}
    public String getIdempotencyKey() {return this.idempotencyKey;}


    public Transaction withStatus(TransactionStatus newStatus) {
        //1 Optimization: if status is already the same, return the same object
        if(this.status == newStatus){
            return this;
        }

        //2 Create a brand-new Transaction with all original values + the new status
        return new Transaction(
                this.fromAccountId, this.toAccountId,
                this.amount, this.transactionId,
                this.timestamp, newStatus,this.idempotencyKey
        );
    }

    //Convenience: commit and fail
    public Transaction commited(){
        return withStatus(TransactionStatus.COMMITTED);
    }

    public Transaction failed(){
        return withStatus(TransactionStatus.FAILED);
    }


    //Builder pattern for readable creation
    public static Builder builder(){
        return new Builder();
    }

    public static class Builder{
        private String fromAccountId;
        private String toAccountId;
        private Money amount;
        private String transactionId;
        private Instant timestamp;
        public String idempotencyKey;
        private TransactionStatus status= TransactionStatus.PENDING;

        public Builder fromAccountId(String fromAccountId){this.fromAccountId=fromAccountId;return this;}
        public Builder toAccountId(String toAccountId){this.toAccountId=toAccountId;return this;}
        public Builder amount(Money amount){this.amount=amount;return this;}
        public Builder transactionId(String transactionId){this.transactionId=transactionId;return this;}
        public Builder timestamp(Instant timestamp){this.timestamp=timestamp;return this;}
        public Builder status(TransactionStatus status){this.status=status;return this;}
        public Builder idempotencyKey(String key){this.idempotencyKey=key;return this;}


        //Auto generated transactionID and idempotencyKey if not provided
        public Transaction build(){
            if(transactionId==null) transactionId=UUID.randomUUID().toString();
            if(timestamp==null) timestamp=Instant.now();
            return new Transaction(fromAccountId,toAccountId,amount,transactionId,timestamp,status,idempotencyKey);
        }
    }

    // equals & hashCode based on transactionId (unique) – but also include idempotencyKey for safety
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if(!(o instanceof Transaction trans)) return false;
        return transactionId.equals(trans.transactionId) && idempotencyKey.equals(trans.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId, idempotencyKey);
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "transactionId='" + transactionId + '\'' +
                ", from=" + fromAccountId +
                " -> to=" + toAccountId +
                ", amount=" + amount +
                ", status=" + status +
                ", idempotencyKey='" + idempotencyKey +
                '}';
    }

}
