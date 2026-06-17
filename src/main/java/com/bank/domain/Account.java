package com.bank.domain;

import com.bank.domain.Exceptions.AccountFrozenException;
import com.bank.domain.Exceptions.InsufficientFundsException;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

final class Account {
    private final String accountNumber;
    private Money balance;
    private AccountStatus status;
    private final List<Customer> owners;

    //Concurrency primitive
    private final ReentrantLock lock;


    public Account(String accountNumber, Money initialbalance, List<Customer> owners) {
        this.accountNumber = Objects.requireNonNull(accountNumber, "account number cannot be null");
        this.balance = Objects.requireNonNull(initialbalance, "amount cannot be null");

        this.status = AccountStatus.ACTIVE;

        // Excellent: Defensive copy to prevent external mutation
        this.owners = new ArrayList<>(Objects.requireNonNull(owners, "owners cannot be null"));
        this.lock = new ReentrantLock();
    }


    // --- Core Business Logic (Thread-Safe) ---
    public void debit(Money amount) {
        validatePositiveAmount(amount);

        lock.lock();
        try {
            checkAccountIsActive();

            if (this.balance.subtract(amount).isNegative()) {
                throw new InsufficientFundsException(
                        "Cannot debit " + amount + ". Current balance: " + this.balance
                );
            }

            this.balance = this.balance.subtract(amount);
        } finally {
            lock.unlock();  // Guaranteed to execute, even if exception is thrown
        }
    }

    public void credit(Money amount) {
        validatePositiveAmount(amount);

        lock.lock();
        try {
            checkAccountIsActive();

            this.balance = this.balance.add(amount);
        }finally {
            lock.unlock(); // Guaranteed to execute, even if exception is thrown
        }
    }


    public void freeze(){
        lock.lock();
        try{
            if(AccountStatus.CLOSED==this.status){
                throw new AccountFrozenException("Can't froze the closed account.");
            }
            if(this.status==AccountStatus.FROZEN){
                return;
            }
            this.status = AccountStatus.FROZEN;
        }finally {
            lock.unlock();
        }
    }

    public void unfreeze(){
        lock.lock();
        try{
            if(AccountStatus.CLOSED==this.status){
                throw new AccountFrozenException("Can't unfroze the closed account.");
            }
            this.status = AccountStatus.ACTIVE;
        }finally {
            lock.unlock();
        }
    }

    public void close(){
        lock.lock();
        try {
            if(!this.balance.isZero()){
                throw new AccountFrozenException("Can't close the account.");
            }
            this.status = AccountStatus.CLOSED;
        }finally {
            lock.unlock();
        }
    }


    //Getter for balance and account number
    public String getAccountNumber() {
        return accountNumber;
    }

    public Money getBalance() {
        lock.lock();
        try {
            return this.balance;
        }finally {
            lock.unlock();
        }
    }

    public AccountStatus getStatus() {
        lock.lock();
        try {
            return this.status;
        }finally {
            lock.unlock();
        }
    }

    public List<Customer> getOwners(){
        lock.lock();
        try{
            return List.copyOf(owners);
        }finally {
            lock.unlock();
        }
    }



    // --- Private Helpers ---

    private void validatePositiveAmount(Money amount) {
        if (amount==null || amount.isNegative() || amount.isZero()) {
            throw new IllegalArgumentException("amount must be strictly be positive");
        }
    }

    public void checkAccountIsActive() {
        if(this.status==AccountStatus.FROZEN){
            throw new AccountFrozenException("Transaction failed: Your "+ accountNumber +" is frozen.");
        }
        if(this.status==AccountStatus.CLOSED){
            throw new AccountFrozenException("Transaction failed: Your "+ accountNumber+" is closed.");
        }
    }

}

