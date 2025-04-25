package com.simiacryptus.cognotik;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OutputInterceptorThreadedTest {

    @Test
    public void testThreadedInterceptor() throws InterruptedException {
        OutputInterceptor.INSTANCE.setupInterceptor();
        AtomicInteger successCounter = new AtomicInteger(0);
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        Object lock = new Object();
        Runnable task = () -> {
            OutputInterceptor.INSTANCE.clearThreadOutput();
            String threadName = Thread.currentThread().getName();
            System.out.println("Thread: " + threadName + " output");
            System.err.println("Thread: " + threadName + " error");
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String expectedOutput = ("Thread: " + threadName + " output\nThread: " + threadName + " error\n").trim();
            String threadOutput = OutputInterceptor.INSTANCE.getThreadOutput().replace("\r", "").trim();
            if (threadOutput.trim().equals(expectedOutput.trim())) {
                successCounter.incrementAndGet();
            } else {
                synchronized (lock) {
                    System.out.println("Expected:\n  " + expectedOutput.replaceAll("\n", "\n  "));
                    System.out.println("Actual:\n  " + threadOutput.replaceAll("\n", "\n  "));
                    System.out.flush();
                }
            }
        };

        for (int i = 0; i < 10; i++) {
            executorService.submit(task);
        }

        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(10, successCounter.get());
    }
}
