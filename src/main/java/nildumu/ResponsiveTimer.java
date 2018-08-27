package nildumu;

import java.util.*;
import java.util.concurrent.*;

public class ResponsiveTimer {

    private final Timer timer = new Timer();

    private final Runnable task;

    private final int checkInterval = 10;
    private final int initialDelay = 10;
    private long counter = initialDelay;
    private long delay = initialDelay;
    private boolean requestedAction = false;
    private boolean autoRun = true;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> future;

    public ResponsiveTimer(Runnable task) {
        this.task = task;
    }

    public void start(){
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (counter > 0) {
                    counter -= 10;
                    return;
                }
                if (requestedAction) {
                    requestedAction = false;
                    long start = System.currentTimeMillis();
                    future = executor.submit(task);
                    try {
                        future.get();
                        delay = System.currentTimeMillis() - start;
                    } catch (InterruptedException e) {
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }
                    System.out.println(String.format("Duration: %d", delay));
                }
                counter = checkInterval;
            }
        }, initialDelay, checkInterval);
    }

    public void request(){
        //assert autoRun;
        if (autoRun) {
            requestedAction = true;
        }
    }

    public void run(){
        if (autoRun){
            stop();
        }
        long start = System.currentTimeMillis();
        future = executor.submit(task);
        try {
            future.get();
        } catch (InterruptedException e) {
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        if (autoRun){
            restart();
        }
    }

    public synchronized void abort(){
        executor.shutdownNow();
        executor = Executors.newSingleThreadExecutor();
       /* if (future != null){
            future.cancel(true);
        }*/
    }

    public void stop(){
        counter = Long.MAX_VALUE;
    }

    public void restart(){
        counter = initialDelay;
        delay = initialDelay;
    }

    public void setAutoRun(boolean autoRun){
        this.autoRun = autoRun;
        if (autoRun){
            this.request();
        } else {
            this.stop();
        }
    }
}
