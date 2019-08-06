package com.simperium.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import androidx.annotation.NonNull;

public class NetworkUtil {
    /**
     * Get the active network connection information.  If no network is currently available, null is
     * returned.
     *
     * @param context {@link Context} in which network connection is determined.
     *
     * @return {@link NetworkInfo} with information on active network connection.
     */
    private static NetworkInfo getActiveNetworkInfo(@NonNull Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return connectivityManager != null ? connectivityManager.getActiveNetworkInfo() : null;
    }

    /**
     * Get the availability of the current network connection.
     *
     * @param context {@link Context} in which network connection is determined.
     *
     * @return True if network connection is available. False otherwise.
     */
    public static boolean isNetworkAvailable(@NonNull Context context) {
        NetworkInfo networkInfo = getActiveNetworkInfo(context);
        return networkInfo != null && networkInfo.isConnected();
    }
}
