package com.trevorism.service

import com.trevorism.data.FastDatastoreRepository
import com.trevorism.https.SecureHttpClient

class WakingDatastoreRepository<T> extends FastDatastoreRepository<T> {

    static final String PING_URL = "https://datastore.data.trevorism.com/ping"
    static final List<Long> RETRY_WAITS_MILLIS = [1000L, 5000L, 10000L, 15000L]

    private final SecureHttpClient httpClient
    private Closure sleeper = { long millis -> Thread.sleep(millis) }

    WakingDatastoreRepository(Class<T> clazz, SecureHttpClient httpClient) {
        super(clazz, httpClient)
        this.httpClient = httpClient
    }

    @Override
    void ping() {
        Exception lastFailure = null
        int attempts = RETRY_WAITS_MILLIS.size() + 1
        for (int attempt = 0; attempt < attempts; attempt++) {
            if (attempt > 0) {
                sleeper.call(RETRY_WAITS_MILLIS[attempt - 1])
            }
            try {
                String response = httpClient.get(PING_URL)
                if (response == "pong") {
                    return
                }
                lastFailure = new IllegalStateException("Datastore ping returned '${response}'")
            } catch (Exception e) {
                lastFailure = e
            }
        }
        throw new IllegalStateException("Datastore did not answer ping after ${attempts} attempts", lastFailure)
    }
}
