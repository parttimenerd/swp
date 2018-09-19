package nildumu;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class ResponsiveTimer {

    private final Timer timer = new Timer();

    private final Runnable task;

    private final int checkInterval;
    private final int initialDelay = 10;
    private long counter = initialDelay;
    private long delay = initialDelay;
    private boolean requestedAction = false;
    private boolean autoRun = true;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Future<?> future;
    private final Runnable startHandler;
    private final Consumer<Duration> finishedHandler;

    public ResponsiveTimer(Runnable task){
        this(task, () -> {}, d -> {});
    }

    public ResponsiveTimer(Runnable task, Runnable startHandler, Consumer<Duration> finishedHandler) {
        this(task, startHandler, finishedHandler, 20);
    }

    public ResponsiveTimer(Runnable task, Runnable startHandler, Consumer<Duration> finishedHandler, int checkInterval) {
        this.task = task;
        this.startHandler = startHandler;
        this.finishedHandler = finishedHandler;
        this.checkInterval = checkInterval;
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
                    startHandler.run();
                    long start = System.currentTimeMillis();
                    future = executor.submit(task);
                    try {
                        future.get();
                        delay = System.currentTimeMillis() - start;
                    } catch (InterruptedException e) {
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }
                    finishedHandler.accept(Duration.ofMillis(delay));
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
        startHandler.run();
        long start = System.currentTimeMillis();
        future = executor.submit(task);
        try {
            future.get();
        } catch (InterruptedException e) {
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        finishedHandler.accept(Duration.ofMillis(System.currentTimeMillis() - start));
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
            this.restart();
            this.request();
        } else {
            this.stop();
        }
    }
}
