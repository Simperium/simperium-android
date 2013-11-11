package com.simperium.test;

import java.util.concurrent.Executor;

public class MockExecutor implements Executor {

    /**
     * Just runs the on the same thread.
     */
    @Override
    public void execute(Runnable runnable){
        runnable.run();
    }

    public static MockExecutor service(){
        return new MockExecutor();
    }

}