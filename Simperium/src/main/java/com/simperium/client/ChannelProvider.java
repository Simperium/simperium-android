package com.simperium.client;

public interface ChannelProvider {

    Bucket.Channel buildChannel(Bucket bucket);

}