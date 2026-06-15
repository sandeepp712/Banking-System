package com.bank.domain;


public enum TransactionStatus {
    COMMITTED,  //Successfully applied to accounts
    PENDING,    //Created but not yet applied
    FAILED    //Rolled back due to error
}
