package com.simperium.client;

public interface ChannelProvider {

    public Bucket.Channel buildChannel(Bucket bucket);

}