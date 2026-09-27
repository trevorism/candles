package com.trevorism.service

import com.trevorism.https.SecureHttpClient
import com.trevorism.model.TrackedPair
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertThrows

class WakingDatastoreRepositoryTest {

    private List<String> pingedUrls = []
    private List<Long> sleeps = []

    private WakingDatastoreRepository<TrackedPair> createRepository(List responses) {
        Iterator responseIterator = responses.iterator()
        SecureHttpClient httpClient = [get: { String url ->
            pingedUrls << url
            def response = responseIterator.next()
            if (response instanceof Exception) {
                throw response
            }
            return response
        }] as SecureHttpClient
        WakingDatastoreRepository<TrackedPair> repository = new WakingDatastoreRepository<>(TrackedPair, httpClient)
        repository.@sleeper = { long millis -> sleeps << millis }
        return repository
    }

    @Test
    void testStopsAtFirstPong() {
        createRepository(["pong"]).ping()

        assert pingedUrls == [WakingDatastoreRepository.PING_URL]
        assert sleeps.isEmpty()
    }

    @Test
    void testRetriesWithBackoffUntilPong() {
        createRepository([new RuntimeException("cold start"), "not ready", "pong"]).ping()

        assert pingedUrls.size() == 3
        assert sleeps == [1000L, 5000L]
    }

    @Test
    void testThrowsAfterFinalAttemptFails() {
        WakingDatastoreRepository<TrackedPair> repository = createRepository((1..5).collect { new RuntimeException("down ${it}") })

        IllegalStateException failure = assertThrows(IllegalStateException, { repository.ping() })

        assert pingedUrls.size() == 5
        assert sleeps == WakingDatastoreRepository.RETRY_WAITS_MILLIS
        assert failure.cause.message == "down 5"
    }
}
