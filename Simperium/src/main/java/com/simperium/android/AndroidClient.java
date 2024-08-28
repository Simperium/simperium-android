package com.simperium.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;

import com.koushikdutta.async.http.AsyncHttpClient;

import org.thoughtcrime.ssl.pinning.PinningTrustManager;
import org.thoughtcrime.ssl.pinning.SystemKeyStore;

import com.simperium.BuildConfig;
import com.simperium.R;
import com.simperium.Version;
import com.simperium.client.ClientFactory;
import com.simperium.util.Uuid;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import android.os.Build;
import android.util.Log;

/**
 * Refactoring as much of the android specific components of the client
 * and decoupling different parts of the API.
 */
public class AndroidClient implements ClientFactory {

    public static final String TAG = "Simperium.AndroidClient";
    public static final String SHARED_PREFERENCES_NAME = "simperium";
    public static final String DEFAULT_DATABASE_NAME = "simperium-store";
    public static final String SESSION_ID_PREFERENCE = "simperium-session-id";

    public static final String WEBSOCKET_URL = "https://api.simperium.com/sock/1/%s/websocket";
    public static final String USER_AGENT_HEADER = "User-Agent";

    protected Context mContext;
    protected SQLiteDatabase mDatabase;
    protected final String mSessionId;

    protected ExecutorService mExecutor;
    protected AsyncHttpClient mHttpClient = AsyncHttpClient.getDefaultInstance();

    public AndroidClient(Context context){
        int threads = Runtime.getRuntime().availableProcessors();
        if (threads > 1) {
            threads -= 1;
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, String.format("Using %d cores for executors", threads));
        }
        mExecutor = Executors.newFixedThreadPool(threads);
        mContext = context;
        mDatabase = mContext.openOrCreateDatabase(DEFAULT_DATABASE_NAME, 0, null);

        SharedPreferences preferences = sharedPreferences(mContext);
        String sessionToken = null;

        if (preferences.contains(SESSION_ID_PREFERENCE)) {
            try {
                sessionToken = preferences.getString(SESSION_ID_PREFERENCE, null);
            } catch (ClassCastException e) {
                sessionToken = null;
            }
        }

        if (sessionToken == null) {
            sessionToken = Uuid.uuid(6);
            preferences.edit().putString(SESSION_ID_PREFERENCE, sessionToken).commit();
        }

        mSessionId = String.format("%s-%s", Version.LIBRARY_NAME, sessionToken);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            // This code manually adds two trusted certificates for SSL.
            // See this for more info: https://letsencrypt.org/2023/07/10/cross-sign-expiration.
            // I got the certificates directly from letsencrypt here: https://letsencrypt.org/certificates/.
            try {
                final SSLContext sslContext = SSLContext.getInstance("TLS");
                final TrustManager[] customTrustManagers = new TrustManager[]{
                        loadCertificate(context, R.raw.isrgrootx1),
                        loadCertificate(context, R.raw.isrgrootx2)
                };
                sslContext.init(null, customTrustManagers, null);
                mHttpClient.getSSLSocketMiddleware().setSSLContext(sslContext);
            } catch (NoSuchAlgorithmException e) {
                Log.e(TAG, "Problem getting instance of SSLContext");
            } catch (KeyManagementException e) {
                Log.e(TAG, "Problem trying to init SSLContext");
            }
        }

        TrustManager[] trustManagers = new TrustManager[] { buildPinnedTrustManager(context) };
        mHttpClient.getSSLSocketMiddleware().setTrustManagers(trustManagers);

    }

    public static TrustManager buildPinnedTrustManager(Context context) {
        // Pin SSL to Simperium.com SPKI
        return new PinningTrustManager(SystemKeyStore.getInstance(context),
                                       new String[] { BuildConfig.SIMPERIUM_COM_SPKI }, 0);
    }

    private static TrustManager loadCertificate(Context context, final int resource) {
        try {
            // Load PEM file
            InputStream inputStream = context.getResources().openRawResource(resource);
            // Create CertificateFactory Instance
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");

            // Generate the keystore instance.
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);

            // Iterate over the certificates in the pem file and add them to the keystore
            while (inputStream.available() > 0) {
                java.security.cert.Certificate cert = certificateFactory.generateCertificate(inputStream);
                String alias = cert.toString();
                keyStore.setCertificateEntry(alias, cert);
            }

            // Create a TrustedManagerFactory instance
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            return trustManagers[0];
        } catch (IOException e) {
            Log.e(TAG, "Problem opening pem cert file", e);
        } catch (CertificateException e) {
            Log.e(TAG, "Problem getting instance of CertificateFactory", e);
        } catch (KeyStoreException e) {
            Log.e(TAG, "Problem getting a keystore instance", e);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Problem loading the keystore", e);
        }
        return null;
    }

    public static SharedPreferences sharedPreferences(Context context){
        return context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public AsyncAuthClient buildAuthProvider(String appId, String appSecret){
        return new AsyncAuthClient(mContext, appId, appSecret, mHttpClient);
    }

    @Override
    public WebSocketManager buildChannelProvider(String appId){
        // Simperium Bucket API
        WebSocketManager.ConnectionProvider provider = new AsyncWebSocketProvider(appId, mSessionId, mHttpClient);
        return new WebSocketManager(mExecutor, appId, mSessionId, new QueueSerializer(mDatabase), provider, mContext);
    }

    @Override
    public PersistentStore buildStorageProvider(){
        return new PersistentStore(mDatabase);
    }

    @Override
    public GhostStore buildGhostStorageProvider(){
        return new GhostStore(mDatabase);
    }

    @Override
    public Executor buildExecutor(){
        return mExecutor;
    }

}