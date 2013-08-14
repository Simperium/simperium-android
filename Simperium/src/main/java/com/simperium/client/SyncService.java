package com.simperium.client;

public interface SyncService {
    void submit(Runnable runnable);
}