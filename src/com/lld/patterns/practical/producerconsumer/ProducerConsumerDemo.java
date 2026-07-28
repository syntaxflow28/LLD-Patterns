package com.lld.patterns.practical.producerconsumer;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/*
 * PRODUCER–CONSUMER — decouple work creation from work processing via a bounded buffer.
 * The buffer applies back-pressure: producers block when full, consumers block when empty.
 *
 * This is the concurrency pattern most likely to appear in a senior LLD round ("design a task
 * scheduler / message queue / rate-limited worker pool / logging framework").
 *
 * Two versions below:
 *   1. Hand-rolled with wait/notify — what interviewers often ask you to write.
 *   2. BlockingQueue — what you'd actually ship.
 *
 * Talking points: bounded vs unbounded (memory safety), why `while` not `if` around wait()
 * (spurious wakeups), notifyAll vs notify, and graceful shutdown via a poison pill.
 */

/** 1) Hand-rolled bounded buffer using intrinsic locks. */
class BoundedBuffer<T> {
    private final Queue<T> queue = new ArrayDeque<>();
    private final int capacity;

    BoundedBuffer(int capacity) { this.capacity = capacity; }

    synchronized void put(T item) throws InterruptedException {
        while (queue.size() == capacity) wait();   // `while`, not `if`: guard against spurious wakeups
        queue.add(item);
        notifyAll();                                // a consumer may be waiting
    }

    synchronized T take() throws InterruptedException {
        while (queue.isEmpty()) wait();
        T item = queue.poll();
        notifyAll();                                // a producer may be waiting for space
        return item;
    }
}

public class ProducerConsumerDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("== Hand-rolled wait/notify ==");
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(3);

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    buffer.put(i);
                    System.out.println("produced " + i);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) System.out.println("   consumed " + buffer.take());
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        System.out.println("== BlockingQueue (production choice) ==");
        runWithBlockingQueue();
    }

    private static void runWithBlockingQueue() throws InterruptedException {
        BlockingQueue<String> tasks = new ArrayBlockingQueue<>(2);
        final String POISON = "__STOP__";                  // sentinel for graceful shutdown

        Thread worker = new Thread(() -> {
            try {
                while (true) {
                    String task = tasks.take();            // blocks until work arrives
                    if (POISON.equals(task)) break;
                    System.out.println("   handled " + task);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        worker.start();

        for (int i = 1; i <= 4; i++) tasks.put("task-" + i); // blocks when the queue is full
        tasks.put(POISON);
        worker.join();
        System.out.println("worker shut down cleanly");
    }
}
