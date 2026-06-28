package com.bank.domain;

import com.bank.domain.Exceptions.AccountFrozenException;
import com.bank.domain.Exceptions.InsufficientFundsException;
import com.bank.domain.Exceptions.DailyLimitExceededException;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.time.LocalDate;

public abstract class Account {
    private final String accountNumber;
    private Money balance;
    private AccountStatus status;
    private final List<Customer> owners;
    //private static final String ALPHABET_ONLY_REGEX = "^[a-zA-Z]+$";

    //Withdrawal limit fields
    private Money dailyWithdrawalLimit;
    private LocalDate lastWithdrawalDate;

    //Concurrency primitive
    protected final ReentrantLock lock;


    //Constructors
    public Account(String accountNumber, Money initialbalance, List<Customer> owners) {
        this.balance = Objects.requireNonNull(initialbalance, "amount cannot be null");
        this.accountNumber = Objects.requireNonNull(accountNumber, "account number cannot be null");
//        if(!accountNumber.matches(ALPHABET_ONLY_REGEX)) {
//            throw new IllegalArgumentException("Invalid account number '" + accountNumber + "'. It must contain letters only!");
//        };

        //Account should be positive balance
        if(initialbalance.isNegative()){
            throw new InsufficientFundsException("Initial balance cannot be negative");
        }

        this.status = AccountStatus.ACTIVE;

        // Excellent: Defensive copy to prevent external mutation
        this.owners = new ArrayList<>(Objects.requireNonNull(owners, "owners cannot be null"));
        this.lock = new ReentrantLock();


        this.dailyWithdrawalLimit = Money.of(new BigDecimal("0.00"),Currency.getInstance("INR"));
        this.lastWithdrawalDate = null;
    }

    //Abstract method for subclass to provide their daily limit
    protected abstract Money getDailyLimit();


    // --- Core Business Logic (Thread-Safe) ---
    public void debit(Money amount) {
        validatePositiveAmount(amount);

        lock.lock();
        try {
            checkAccountIsActive();

            LocalDate today = LocalDate.now();
            if(lastWithdrawalDate == null || !lastWithdrawalDate.equals(today)){
                dailyWithdrawalLimit=Money.of(new BigDecimal("0.00"),Currency.getInstance("INR"));
                lastWithdrawalDate=today;
            }

            Money newDailyTotal=dailyWithdrawalLimit.add(amount);
            Money dailyLimit=getDailyLimit();

            if(newDailyTotal.compareTo(dailyLimit) >0 ){
                throw new DailyLimitExceededException(
                        "Daily withdrawal limit of "+dailyLimit+" exceeded "+"Current balance is "+dailyWithdrawalLimit
                );
            }

            //Insufficient balance check
            if (this.balance.subtract(amount).isNegative()) {
                throw new InsufficientFundsException(
                        "Insufficient balance"
                );
            }

            this.balance = this.balance.subtract(amount);
            this.dailyWithdrawalLimit=newDailyTotal;
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
            if(this.status != AccountStatus.ACTIVE){
                throw new AccountFrozenException("Only active accounts can be closed account.");
            }
            if(this.balance.isNegative()){
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

    public Money getDailyWithdrawalLimit() {
        lock.lock();
        try {
            return this.dailyWithdrawalLimit;
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

    private void checkAccountIsActive() {
        if(this.status==AccountStatus.FROZEN){
            throw new AccountFrozenException("Transaction failed: Your "+ accountNumber +" is frozen.");
        }
        if(this.status==AccountStatus.CLOSED){
            throw new AccountFrozenException("Transaction failed: Your "+ accountNumber+" is closed.");
        }
    }

    /**
     * Acquires the lock for this account.
     * MUST be paired with unlock() in a finally block.
     */
    public void lock() {
        this.lock.lock();
    }

    /**
     * Releases the lock for this account.
     */
    public void unlock() {
        this.lock.unlock();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if(!(o instanceof Account account)) return false;
        return accountNumber.equals(account.getAccountNumber());
    }

}

