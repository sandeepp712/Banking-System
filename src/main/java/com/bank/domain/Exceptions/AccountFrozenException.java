package com.bank.domain.Exceptions;

public class AccountFrozenException extends RuntimeException{
    public AccountFrozenException(String message){
        super(message);
    }
}