package com.bank.concurrency;


import com.bank.domain.Account;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Comparator;

public final class LockOrderingHelper{

    public static List<Account> getOrderedAccounts(Account... account){

        Objects.requireNonNull(account,"Accounts array can't be null");
        for(Account a : account){
            Objects.requireNonNull(a,"Individual Account can't be null");
        }

        /**
         * Takes a variable number of accounts and returns them sorted by accountNumber.
         *
         * @param accounts The accounts to be locked.
         * @return A new List of accounts sorted alphabetically by accountNumber.
         */
        List<Account> orderList = new ArrayList<>(List.of(account));
        orderList.sort(Comparator.comparing(Account::getAccountNumber));

        return orderList;
    }
}