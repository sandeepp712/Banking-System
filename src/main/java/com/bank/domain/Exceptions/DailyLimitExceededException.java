package com.bank.domain.Exceptions;

public class DailyLimitExceededException extends RuntimeException{
    public DailyLimitExceededException(String message){
        super(message);
    }
}