package com.simperium.client;

public interface ChannelProvider {

    static public final int LOG_DISABLED = 0;
    static public final int LOG_DEBUG = 1;
    static public final int LOG_VERBOSE = 2;

    public Bucket.Channel buildChannel(Bucket bucket);

    /**
     * Send a log message to Simperium
     */
    public void log(int level, CharSequence message);


    /**
     * Get the current log level set from the service
     */
    public int getLogLevel();


    public interface HeartbeatListener {
        public void onBeat();
    }

    public void addHeartbeatListener(HeartbeatListener listener);
}