package com.simperium.test;

import java.util.concurrent.Executor;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;

public class MockExecutor {

    public static final String TAG = "Simperium.Test";

    public static class Immediate implements Executor {
        /**
         * Just runs the on the same thread.
         */
        @Override
        public void execute(Runnable runnable){
            runnable.run();
        }

    }

    public static class Playable implements Executor {

        final List<Runnable> pending = new ArrayList<Runnable>();
        boolean mPaused = true;

        @Override
        public String toString(){
            String status = mPaused ? "paused" : "playing";
            return String.format("MockExecutor.Playable %s (pending: %d)", status, pending.size());
        }

        @Override
        public void execute(Runnable runnable){
            if (!mPaused) {
                runnable.run();
            } else {
                Log.d(TAG, String.format("Queuing runnable %s", runnable));
                pending.add(runnable);
            }
        }

        public void clear(){
            pending.clear();
        }

        public void play(){
            mPaused = false;
            run();
        }

        public void pause(){
            mPaused = true;
        }

        public void run(){
            List<Runnable> tasks = new ArrayList<Runnable>(pending);
            pending.clear();
            Log.d(TAG, String.format("Performing %d tasks", tasks.size()));
            for(Runnable task : tasks){
                Log.d(TAG, String.format("Starting %s", task));
                task.run();
                Log.d(TAG, String.format("Completed %s", task));
            }

            if (pending.size() > 0) {
                Log.d(TAG, String.format("%d Tasks were added, running again", pending.size()));
                run();
            }

        }


    }

    public static MockExecutor.Immediate immediate(){
        return new MockExecutor.Immediate();
    }

}