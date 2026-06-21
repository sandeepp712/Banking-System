package com.bank.persistence;

import com.bank.domain.Transaction;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;


public class TransactionLogger{
    private final BlockingQueue<Transaction> queue;
    private final Path logFilePath;
    private final ExecutorService writerExecutor;
    private volatile boolean running;

    public TransactionLogger(String logFilePath) throws IOException{
        this.queue = new LinkedBlockingQueue<>();
        this.logFilePath = Paths.get(logFilePath).toAbsolutePath();
        this.running = true;

        //Start the dedicated writer thread
        this.writerExecutor = Executors.newSingleThreadExecutor();

        this.writerExecutor.submit(this::writeLoop);
    }


    //service to submit transactions
    public void logTransaction(Transaction transaction) throws InterruptedException{
        queue.put(transaction);
    }

    private void writeLoop() {
        try(BufferedWriter writer= Files.newBufferedWriter(
                    logFilePath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)){
            while(running || !queue.isEmpty()){
                Transaction tx=queue.poll(100,TimeUnit.MILLISECONDS);
                if(tx!=null){
                    writer.write(toJson(tx));
                    writer.newLine();
                    writer.flush();   //Ensure written to disk
                }
            }
        }catch (Exception e){
            throw new RuntimeException("Transaction logger failed",e);
        }
    }

    public void shutdown() throws InterruptedException{
        running = false;
        writerExecutor.shutdown();
        writerExecutor.awaitTermination(10, TimeUnit.SECONDS);
    }

    // Simple JSON serialization (in production, use Jackson/Gson)
    private String toJson(Transaction tx) {
        return String.format(
                "{\"id\":\"%s\",\"from\":\"%s\",\"to\":\"%s\",\"amount\":\"%s\",\"status\":\"%s\",\"timestamp\":\"%s\"}",
                tx.getTransactionId(),
                tx.getFromAccountId(),
                tx.getToAccountId(),
                tx.getAmount(),
                tx.getStatus(),
                tx.getTimestamp()
        );
    }
}