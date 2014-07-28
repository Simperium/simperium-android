package com.simperium.test;

import com.simperium.android.Simperium.Client;

import com.simperium.storage.MemoryStore;

import java.util.concurrent.Executor;

public class MockClient implements Client {

    public MockAuthProvider authProvider = new MockAuthProvider();
    public MockChannelProvider channelProvider = new MockChannelProvider();

    @Override
    public Executor buildExecutor() {
        return MockExecutor.immediate();
    }

    @Override
    public MockAuthProvider buildAuthProvider() {
        return authProvider;
    }

    @Override
    public MockChannelProvider buildChannelProvider() {
        return channelProvider;
    }

    @Override
    public MemoryStore buildStorageProvider() {
        return new MemoryStore();
    }

    @Override
    public MockGhostStore buildGhostStorageProvider() {
        return new MockGhostStore();
    }

}