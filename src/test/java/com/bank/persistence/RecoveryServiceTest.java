package com.bank.persistence;


import com.bank.domain.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Currency;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RecoveryServiceTest{
    private RecoveryService recoveryService;
    private final String testLogFile="target/data/test_transaction_log.log";
    private TransactionLogger logger;

    @BeforeEach
    void setUp() throws Exception {
        Files.deleteIfExists(Paths.get(testLogFile));
        Files.createDirectories(Paths.get(testLogFile).getParent());
        logger = new TransactionLogger(testLogFile);
        recoveryService = new RecoveryService();
    }

    @AfterEach
    void tearDown() throws Exception {
        logger.shutdown();
    }

    @Test
    @DisplayName("Recovery restores account balance from WAL")
    void testRecoveryRestoreBalanceFromWAL() throws Exception {
        Currency currency = Currency.getInstance("INR");
        Account account1=new Account("Acc-1",Money.of(new BigDecimal("0.00"),Currency.getInstance("INR")),List.of(new Customer("Amar")));
        Account account2=new Account("Acc-2",Money.of(new BigDecimal("0.00"),Currency.getInstance("INR")),List.of(new Customer("Deep")));


        // Transaction 1: Deposit $1000 to Acc-1 (from SYSTEM)
        Transaction tx1 = Transaction.builder()
                .fromAccountId("SYSTEM")
                .toAccountId("Acc-1")
                .amount(Money.of(new BigDecimal("1000.00"), currency))
                .status(TransactionStatus.COMMITTED)
                .build();

        // Transaction 2: Deposit $1000 to Acc-2 (from SYSTEM)
        Transaction tx2 = Transaction.builder()
                .fromAccountId("SYSTEM")
                .toAccountId("Acc-2")
                .amount(Money.of(new BigDecimal("1000.00"), currency))
                .status(TransactionStatus.COMMITTED)
                .build();

        // Transaction 3: Transfer $100 from Acc-1 to Acc-2
        Transaction tx3 = Transaction.builder()
                .fromAccountId("Acc-1")
                .toAccountId("Acc-2")
                .amount(Money.of(new BigDecimal("100.00"), currency))
                .status(TransactionStatus.COMMITTED)
                .build();


        //Write to WAL
        logger.logTransaction(tx1);
        logger.logTransaction(tx2);
        logger.logTransaction(tx3);


        //Simulate crash
        logger.shutdown();


        //Recovery
        AccountRepository recoveredRepo= recoveryService.recover(testLogFile);

        Account acc1=recoveredRepo.findByAccountNumber("Acc-1").get();
        Account acc2=recoveredRepo.findByAccountNumber("Acc-2").get();

//        System.out.println("Acc-1 balance: " + acc1.getBalance());
//        System.out.println("Acc-2 balance: " + acc2.getBalance());

        assertEquals(Money.of(new BigDecimal("900.00"),currency),acc1.getBalance());
        assertEquals(Money.of(new BigDecimal("1100.00"),currency),acc2.getBalance());
    }

}