package com.bank.persistence;

import com.bank.domain.Transaction;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;


public class TransactionLogger {
    private final BlockingQueue<Transaction> queue;
    private final Path logFilePath;
    private final ExecutorService writerExecutor;
    private volatile boolean running;
    private AtomicLong counter = new AtomicLong();
    private volatile long lastFlushSequenceNumber;

    public TransactionLogger(String logFilePath) throws IOException {
        this.queue = new LinkedBlockingQueue<>();
        this.logFilePath = Paths.get(logFilePath).toAbsolutePath();
        this.running = true;

        if (Files.exists(this.logFilePath)) {
            long maxSequenceNumber = findMaxSequenceNumberinFile(this.logFilePath);
            this.counter.set(maxSequenceNumber);
            this.lastFlushSequenceNumber = maxSequenceNumber;
            System.out.println("Resumed WAL counter at sequence number " + maxSequenceNumber);
        } else {
            this.counter.set(0L);
        }

        //Start the dedicated writer thread
        this.writerExecutor = Executors.newSingleThreadExecutor();

        this.writerExecutor.submit(this::writeLoop);
    }


    //service to submit transactions
    public void logTransaction(Transaction transaction) throws InterruptedException {
        queue.put(transaction);
    }

    private void writeLoop() {
        try (BufferedWriter writer = Files.newBufferedWriter(
                logFilePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            while (running || !queue.isEmpty()) {
                Transaction tx = queue.poll(100, TimeUnit.MILLISECONDS);
                if (tx != null) {
                    // adding and capture the count to add in log transaction
                    long seq = counter.incrementAndGet();

                    writer.write(toJson(tx, seq));
                    writer.newLine();
                    writer.flush();   //Ensure written to disk

                    lastFlushSequenceNumber = seq;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Transaction logger failed", e);
        }
    }

    public void shutdown() throws InterruptedException {
        running = false;
        writerExecutor.shutdown();
        writerExecutor.awaitTermination(10, TimeUnit.SECONDS);
    }

    // Simple JSON serialization (in production, use Jackson/Gson)
    private String toJson(Transaction tx, long seq) {
        return String.format(
                "{\"sequence_No\":\"%s\",\"transaction_id\":\"%s\",\"from\":\"%s\",\"to\":\"%s\",\"amount\":\"%s\",\"status\":\"%s\",\"key\":\"%s\",\"timestamp\":\"%s\"}",
                seq,
                tx.getTransactionId(),
                tx.getFromAccountId(),
                tx.getToAccountId(),
                tx.getAmount().getAmount().toPlainString(),
                tx.getStatus(),
                tx.getIdempotencyKey(),
                tx.getTimestamp()
        );
    }

    public long getLastFlushSequenceNumber() {
        return this.lastFlushSequenceNumber;
    }

    public long findMaxSequenceNumberinFile(Path path) throws IOException {
        if (!Files.exists(path) || Files.size(path) == 0) {
            return 0L;
        }

        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            long fileLength = raf.length();
            if (fileLength == 0) return 0L;

            StringBuilder lastLine = new StringBuilder();
            long pointer = fileLength - 1;

            // 1. Skip any trailing newlines at the very end of the file
            while (pointer >= 0) {
                raf.seek(pointer);
                byte b = raf.readByte();
                if (b == '\n' || b == '\r') {
                    pointer--;
                } else {
                    break; // Found the last character of the actual data
                }
            }

            // 2. Read backwards until we hit the previous newline (or start of file)
            while (pointer >= 0) {
                raf.seek(pointer);
                byte b = raf.readByte();
                if (b == '\n' || b == '\r') {
                    break; // We found the start of the last line!
                }
                lastLine.append((char) b);
                pointer--;
            }

            if (lastLine.length() == 0) {
                return 0L;
            }

            // 3. Because we read backwards, the string is reversed. Fix it.
            String actualLastLine = lastLine.reverse().toString();

            // 4. Parse the JSON to extract the sequence number
            // Format: {"sequence_No":"10","transaction_id":"...
            String searchKey = "\"sequence_No\":\"";
            int startIndex = actualLastLine.indexOf(searchKey);
            if (startIndex == -1) return 0L;

            startIndex += searchKey.length();
            int endIndex = actualLastLine.indexOf("\"", startIndex);
            if (endIndex == -1) return 0L;

            String seqStr = actualLastLine.substring(startIndex, endIndex);
            return Long.parseLong(seqStr);
        }
    }
}