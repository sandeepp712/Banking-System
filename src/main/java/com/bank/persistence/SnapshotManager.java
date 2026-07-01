package com.bank.persistence;

import com.bank.domain.Account;
import com.bank.domain.Money;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class SnapshotManager {
    private final InMemoryAccountRepository repository;
    private final TransactionLogger logger;
    private final Path snapshotDir;

    public SnapshotManager(InMemoryAccountRepository repository, TransactionLogger logger, String snapshotDir) throws IOException {
        this.repository = repository;
        this.logger = logger;

        this.snapshotDir = Paths.get(snapshotDir);
        Files.createDirectories(this.snapshotDir);
    }

    public void takeSnapshot() throws IOException {
        System.out.println("Starting Snapshot...");
        long checkpointSeq;
        Collection<Account> accountReferences;

        // ==========================================
        // PHASE 1: The Micro-Pause (Microseconds)
        // ==========================================
        repository.acquiredSnapshotLock();  //Block new account creation
        try {
            //1 Get the exact sequence number of the last flushed WAL transaction
            checkpointSeq = logger.getLastFlushSequenceNumber();

            //2 Get a stable list of account references
            accountReferences = repository.getAllAccounts();
        } finally {
            repository.releasedSnapshotLock();      //Bank resumes immediately
        }


        // ==========================================
        // PHASE 2: The Scan (Milliseconds)
        // ==========================================
        // We now read balances one by one. The bank is OPEN, but we lock individual accounts.
        Map<String, String> snapshotData = new LinkedHashMap<>();
        for (Account account : accountReferences) {
            account.lock();
            try {
                Money balance = account.getBalance();
                snapshotData.put(account.getAccountNumber(), balance.getAmount().toPlainString() + "|" + balance.getCurrency().getCurrencyCode());
            } finally {
                account.unlock();
            }
        }

        // ==========================================
        // PHASE 3: Write to Disk (Bypassing OS Cache)
        // ==========================================
        String filename = "snapshot_" + checkpointSeq + ".json";
        Path file = snapshotDir.resolve(filename);

        StringBuilder json=new StringBuilder();
        json.append("{\"checkpointSeq\":").append(checkpointSeq).append(",\"accounts\":{");
        boolean first=true;
        for(Map.Entry<String, String> entry : snapshotData.entrySet()) {
            if(!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\",\n");
            first=false;
        }
        json.append("}}");

        try(FileChannel channel=FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(json.toString().getBytes()));
            channel.force(true);
        }

        System.out.println("✅ Snapshot saved: " + filename + " at sequence " + checkpointSeq);
    }

}
