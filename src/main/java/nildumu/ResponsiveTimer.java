package nildumu;

import java.util.Timer;
import java.util.TimerTask;

public class ResponsiveTimer {

    private final Timer timer = new Timer();

    private final Runnable task;

    private final int checkInterval = 10;
    private final int initialDelay = 10;
    private long counter = initialDelay;
    private long delay = initialDelay;
    private boolean requestedAction = false;

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
                    task.run();
                    delay = System.currentTimeMillis() - start;
                    System.out.println(String.format("Duration: %d", delay));
                }
                counter = checkInterval;
            }
        }, initialDelay, checkInterval);
    }

    public void request(){
        requestedAction = true;
    }

    public void stop(){
        counter = Long.MAX_VALUE;
    }

    public void restart(){
        counter = initialDelay;
        delay = initialDelay;
    }
}
