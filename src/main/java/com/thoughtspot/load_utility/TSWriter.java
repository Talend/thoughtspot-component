package com.thoughtspot.load_utility;


import java.util.Hashtable;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TSWriter {
    private boolean isCompleted = false;
    private final int _DEFAULT_COMMIMT = 500000;
    private LinkedBlockingQueue<String> records = null;
    private static TSWriter instance = null;
    private Hashtable<String, ThreadStatus> threadPool = new Hashtable<String, ThreadStatus>();
    private int threadCount = 1;
    public static synchronized TSWriter newInstance()
    {
        if (instance == null)
                instance = new TSWriter();

        return instance;
    }

    private TSWriter() {
        records = new LinkedBlockingQueue<String>(_DEFAULT_COMMIMT);
    }

    public boolean done() {
        boolean done = false;
        synchronized (this) {
            done = threadPool.size() == 0 ? true : false;
        }
        return done;
    }

    public void update(String name, ThreadStatus status)
    {
        synchronized (this) {
            if (status == ThreadStatus.DONE)
                threadPool.remove(name);
        }
    }

    public String register(String name, ThreadStatus status)
    {
        String threadName = null;
        synchronized (this) {
             threadName = name + threadCount++;
            if (!threadPool.containsKey(threadName))
                threadPool.put(threadName, status);
        }
        return threadName;
    }

    public void setIsCompleted(boolean isCompleted)
    {
        synchronized (this) {
            this.isCompleted = isCompleted;
        }
    }

    public boolean getIsCompleted()
    {
        synchronized (this) {
            return this.isCompleted;
        }
    }

    public void add(String record)
    {
        //synchronized (this) {
        try {
            records.put(record);
        } catch (InterruptedException e) {
            System.out.println(e.getMessage());
        }
        //}
    }

    public String poll()
    {
        //synchronized (this) {
        try {
            return records.take();
        } catch (InterruptedException e) {
            System.out.println(e.getMessage());
        }
        //}
        return null;
    }

    public int size() {
        synchronized (this) {
            return records.size();
        }
    }
}
