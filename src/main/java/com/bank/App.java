//package com.bank;
//
//import com.bank.domain.Account;
//import com.bank.domain.AccountRepository;
//import com.bank.domain.Customer;
//import com.bank.domain.Money;
//import com.bank.persistence.RecoveryService;
//import com.bank.persistence.TransactionLogger;
//import com.bank.service.AccountService;
//import com.bank.service.TransferService;
//
//import java.io.IOException;
//import java.nio.file.Paths;
//import java.util.*;
//import java.util.stream.Collectors;
//
//public class App {
//    public static void main(String[] args) {
//        try {
//            //Initialize WAL
//            TransactionLogger logger = new TransactionLogger("data/transactions.log");
//            System.out.println("Logger file : " + Paths.get("data/transactions.log").toAbsolutePath());
//
//            //Recover state from WAl
//            RecoveryService recovery = new RecoveryService();
//            AccountRepository repository = recovery.recover("data/transactions.log");
//            System.out.println("Recovered transactions file : " + Paths.get("data/transactions.log").toAbsolutePath());
//
//            //Create Service
//            AccountService accountService = new AccountService(repository, logger);
//            TransferService transferService = new TransferService(repository, logger);
//
//            //Runtime shutdown hook to close WAL gracefully
//            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//                try {
//                    logger.shutdown();
//                } catch (Exception ex) {
//                    System.err.println("[CRITICAL] Failed to flush and close WAL logger during JVM shutdown!");
//                    ex.printStackTrace();
//                }
//            }));
//
//            //Run CLI
//            runCLI(accountService, transferService, logger);
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
//    }
//
//    public static void printMenu() {
//        System.out.println("\n---Menu---\n");
//        System.out.println("1. Create Account");
//        System.out.println("2. Deposit Money");
//        System.out.println("3. Withdraw Money");
//        System.out.println("4. Transfer Money");
//        System.out.println("5. Check Balance");
//        System.out.println("6. Show Accounts");
//        System.out.println("0. Exit");
//        System.out.print("Choice : ");
//    }
//
//
//    public static void runCLI(AccountService accountService, TransferService transferService, TransactionLogger logger) {
//        Scanner input = new Scanner(System.in);
//        boolean running = true;
//
//        System.out.println("🏛️ Welcome to Bank Account");
//
//
//        while (running) {
//            try {
//                printMenu();
//
//                if (!input.hasNextInt()) {
//                    System.err.println("Please input a valid option!");
//                    input.nextLine();
//                    continue;
//                }
//
//                int choice = input.nextInt();
//                input.nextLine();
//
//                switch (choice) {
//                    case 1 -> createAccount(accountService, input);
//                    case 2 -> deposit(accountService, input, logger);
//                    case 3 -> withdraw(accountService, input, logger);
//                    case 4 -> transfer(transferService, input, logger);
//                    case 5 -> checkBalance(accountService, input);
//                    case 6 -> AllAccounts(accountService);
//                    case 0 -> {
//                        System.out.println("Thanks for Visiting!");
//                        running = false;
//                        try {
//                            logger.shutdown();
//                        }catch (Exception ex) {
//                            Thread.currentThread().interrupt();
//                            System.err.println("Logger shutdown failed due to system interruption.");
//                        }
//                    }
//                    default -> System.out.println("Invalid choice! Please enter a valid choice!");
//                }
//            }catch (IllegalArgumentException e) {
//                System.out.println(e.getMessage());
//            }catch (Exception e) {
//                System.out.println(e.getMessage());
//            }
//        }
//        input.close();
//    }
//
//    public static void createAccount(AccountService accountService, Scanner input) {
//        System.out.println("Enter Account Number");
//        String accountNo = input.nextLine();
//
//        System.out.println("Enter Initial Balance");
//        Money initialBalance = Money.of(input.nextBigDecimal(), Currency.getInstance("INR"));
//        input.nextLine();
//
//        List<Customer> owners = new ArrayList<>();
//        System.out.println("Enter Owner name or done to finish");
//        while (input.hasNextLine()) {
//            String owner = input.nextLine();
//            if (owner.equalsIgnoreCase("done")) break;
//            owners.add(new Customer(owner));
//        }
//
//        try {
//            accountService.createAccount(accountNo, initialBalance, owners);
//        } catch (Exception ex) {
//            System.err.println(" "+ex.getMessage());
//        }
//    }
//
//
//    public static void deposit(AccountService accountService, Scanner input, TransactionLogger logger) throws Exception {
//        System.out.println("Enter Account Number");
//        String accountNo = input.nextLine();
//        System.out.println("Enter amount to deposit");
//        Money amount = Money.of(input.nextBigDecimal(), Currency.getInstance("INR"));
//
//        input.nextLine();
//        try {
//            accountService.deposit(accountNo, amount, UUID.randomUUID().toString());
//            System.out.println("Deposited Successfully");
//        } catch (Exception ex) {
//            System.out.println(ex.getMessage());
//        }
//    }
//
//    public static void withdraw(AccountService accountService, Scanner input, TransactionLogger logger) {
//        System.out.println("Enter Account Number");
//        String accountNo = input.nextLine();
//        System.out.println("Enter amount to withdraw");
//        Money amount = Money.of(input.nextBigDecimal(), Currency.getInstance("INR"));
//        input.nextLine();
//        try {
//            accountService.withdraw(accountNo, amount, UUID.randomUUID().toString());
//            System.out.println("Withdraw Successfully");
//        } catch (Exception ex) {
//            System.out.println("Withdraw Failed" + ex.getMessage());
//        }
//    }
//
//    public static void transfer(TransferService transferService, Scanner input, TransactionLogger logger) throws Exception {
//        System.out.println("Enter source Number");
//        String accountNo = input.nextLine();
//        System.out.println("Enter destination Number");
//        String destinationNumber = input.nextLine();
//        System.out.println("Enter amount to transfer");
//        Money amount = Money.of(input.nextBigDecimal(), Currency.getInstance("INR"));
//        input.nextLine();
//        try {
//            transferService.transfer(accountNo, destinationNumber, amount, UUID.randomUUID().toString());
//            System.out.println("Transfer Successfully");
//        } catch (Exception ex) {
//            System.out.println(ex.getMessage());
//        }
//    }
//
//    public static void checkBalance(AccountService accountService, Scanner input) {
//        System.out.println("Enter Account Number");
//        String accountNo = input.nextLine();
//        try {
//            Money balance = accountService.getAccount(accountNo).getBalance();
//            System.out.println("Balance: " + balance);
//        } catch (Exception ex) {
//            System.out.println("Check Balance Failed " + ex.getMessage());
//        }
//    }
//
//    public static void AllAccounts(AccountService accountService) {
//        try {
//            Collection<Account> accounts = accountService.getAllAccounts();
//            if(accounts.isEmpty()) {
//                System.out.println("No Account Found.");
//                return;
//            }
//
//            // Fixed widths: adjust numbers as needed
//            String format = "%-4s %-15s %-12s %-20s %-10s%n";
//            System.out.printf(format, "#", "Account Number", "Balance", "Owners", "Status");
//            System.out.println("---- --------------- ------------ -------------------- ----------");
//
//            int index=1;
//            for(Account acc:accounts){
//                String owners=acc.getOwners().stream().map(Customer::getId).collect(Collectors.joining(", "));
//                System.out.printf(
//                        format,
//                        index++,
//                        acc.getAccountNumber(),
//                        acc.getBalance(),
//                        owners,
//                        acc.getStatus()
//                );
//            }
//        }catch (Exception ex) {
//            System.out.println("Internet Failed");
//        }
//    }
//}
//
