package com.bank.persistence;

import com.bank.domain.*;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;


public class RecoveryService {

    private static final String SYSTEM_ACCOUNT = "SYSTEM";

    public AccountRepository recover(String logFilePath, String snapshotDirPath) throws Exception {
        InMemoryAccountRepository repo = new InMemoryAccountRepository();

        long minSequenceToReplay = 0;


        //1 Try to load the latest snapshot
        Path snapshotDir = Paths.get(snapshotDirPath);
        if (Files.exists(snapshotDir)) {
            Optional<Path> latestSnapshot = Files.list(snapshotDir)
                    .filter(p -> p.getFileName().toString().startsWith("snapshot"))
                    .max(Comparator.comparing(p -> {
                        String name = p.getParent().getFileName().toString();
                        return Long.parseLong(name.replace("snapshot", "").replace(".json", ""));
                    }));

            if (latestSnapshot.isPresent()) {
                System.out.println("Loading snapshot: " + latestSnapshot.get().getFileName());
                String json = Files.readString(latestSnapshot.get());

                //Extract checkpoint_seq
                int seqStart = json.indexOf("\"checkpoint\":") + 17;
                int seqEnd = json.indexOf(",", seqStart);
                minSequenceToReplay = Long.parseLong(json.substring(seqStart, seqEnd));

            }
        }


        //2 Replay the WAL(Filtering based on snapshot)
        Path walPath = Paths.get(logFilePath);
        if (!Files.exists(walPath)) {
            return repo;        //No log file, fresh start
        }

        List<String> lines = Files.readAllLines(walPath);
        int recoveredCount = 0;

        for (String line : lines) {

            if (line.trim().isEmpty()) continue;

            // Extract sequence_No from the JSON line
            int seqStart = line.indexOf("\"sequence_No\":\"") + 15;
            int seqEnd = line.indexOf("\"", seqStart);
            long currentSeq = Long.parseLong(line.substring(seqStart, seqEnd));

            // ✅ THE FILTER: Skip transactions already captured in the snapshot!
            if (currentSeq <= minSequenceToReplay) {
                continue;
            }


            Transaction tx = parseJson(line);

            if (tx.getStatus() != TransactionStatus.COMMITTED) {
                continue; // Skip PENDING transactions
            }

            try {
                String fromId = tx.getFromAccountId();
                String toId = tx.getToAccountId();
                Money amount = tx.getAmount();

                // Ensure non-SYSTEM accounts exist (create with zero balance if missing)
                if (!fromId.equals(SYSTEM_ACCOUNT)) {
                    ensureAccountExists(repo, fromId);
                }

                if (!toId.equals(SYSTEM_ACCOUNT)) {
                    ensureAccountExists(repo, toId);
                }

                // Apply debit (skip for SYSTEM — money comes from outside)
                if (!fromId.equals(SYSTEM_ACCOUNT)) {
                    Account fromAcc = repo.findByAccountNumber(fromId)
                            .orElseThrow(() -> new IllegalStateException("Account not found: " + fromId));
                    fromAcc.debit(amount);
                    repo.save(fromAcc);
                }

                // Apply credit (skip for SYSTEM — money goes to outside)
                if (!toId.equals(SYSTEM_ACCOUNT)) {
                    Account toAcc = repo.findByAccountNumber(toId)
                            .orElseThrow(() -> new IllegalStateException("Account not found: " + toId));
                    toAcc.credit(amount);
                    repo.save(toAcc);
                }

                recoveredCount++;
            } catch (Exception e) {
                System.out.println("Failed to parse/replay line: " + line);
                e.printStackTrace();
            }
        }

        System.out.println("Recovery complete. Skipped up to seq" + minSequenceToReplay + ". Replayed " + recoveredCount + " new transactions.");
        return repo;
    }

    private void ensureAccountExists(InMemoryAccountRepository repo, String accountId) {
        if (repo.findByAccountNumber(accountId).isEmpty()) {
            Account newAccount = new CheckingAccount(accountId, Money.of(new BigDecimal("0"), Currency.getInstance("INR")), new ArrayList<>(), ProductTier.BASIC_CHECKING);

            repo.save(newAccount);
        }
    }

    private Transaction parseJson(String line) {
        String id = extractField(line, "id");
        String fromAccountId = extractField(line, "from");
        String toAccountId = extractField(line, "to");
        String amountStr = extractField(line, "amount");
        String statusStr = extractField(line, "status");
        String date = extractField(line, "timestamp");

        Money amount = Money.of(new BigDecimal(amountStr), Currency.getInstance("INR"));
        TransactionStatus status = TransactionStatus.valueOf(statusStr);
        Instant timestamp = Instant.parse(date);

        return Transaction.builder()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(amount)
                .status(status)
                .timestamp(timestamp)
                .idempotencyKey(id)
                .build();
    }

    private String extractField(String json, String field) {
        // Simple extraction: "field":"value" → "value"
        String pattern = "\"" + field + "\":\"([^\"]+)\"";
        return json.replaceAll(".*" + pattern + ".*", "$1");
    }
}