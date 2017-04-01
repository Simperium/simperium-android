package com.simperium.client;

import android.util.Log;

import java.security.Provider;
import java.security.Security;
import java.util.Set;
import java.util.TreeSet;

// This class is useful for checking which crypto providers are available.
public class AndroidCryptographyInspector {

    private static final String TAG = "AndroidCryptoInspector";

    public static void printSupportedAlgorithms() {
        // get all the providers
        Provider[] providers = Security.getProviders();

        for (int providerIndex = 0; providerIndex < providers.length; providerIndex++) {
            // get all service types for a specific provider
            Provider provider = providers[providerIndex];
            Set<Object> ks = provider.keySet();
            Set<String> servicetypes = new TreeSet<>();
            for (Object k2 : ks) {
                String k = k2.toString();
                k = k.split(" ")[0];
                if (k.startsWith("Alg.Alias."))
                    k = k.substring(10);

                servicetypes.add(k.substring(0, k.indexOf('.')));
            }

            // get all algorithms for a specific service type
            int serviceNumber = 1;
            for (String stype : servicetypes) {
                Set<String> algorithms = new TreeSet<>();
                for (Object k1 : ks) {
                    String k = k1.toString();
                    k = k.split(" ")[0];
                    if (k.startsWith(stype + "."))
                        algorithms.add(k.substring(stype.length() + 1));
                    else if (k.startsWith("Alg.Alias." + stype + "."))
                        algorithms.add(k.substring(stype.length() + 11));
                }

                int algorithmNumber = 1;
                for (String algorithm : algorithms) {
                    Log.e(TAG, "[P#" + (providerIndex + 1) + ":" + provider.getName() + "]" +
                            "[S#" + serviceNumber + ":" + stype + "]" +
                            "[A#" + algorithmNumber + ":" + algorithm + "]");
                    algorithmNumber++;
                }

                serviceNumber++;
            }
        }
    }
}
