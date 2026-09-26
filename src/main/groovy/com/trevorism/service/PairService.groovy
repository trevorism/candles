package com.trevorism.service

import com.trevorism.data.PingingDatastoreRepository
import com.trevorism.data.Repository
import com.trevorism.https.AppClientSecureHttpClient
import com.trevorism.model.AvailablePair
import com.trevorism.model.TrackedPair
import jakarta.inject.Inject

@jakarta.inject.Singleton
class PairService {

    private Repository<TrackedPair> repository = new PingingDatastoreRepository<>(TrackedPair, new AppClientSecureHttpClient())
    private final MarketDataClient marketDataClient

    @Inject
    PairService(MarketDataClient marketDataClient) {
        this.marketDataClient = marketDataClient
    }

    List<TrackedPair> listTracked() {
        return (repository.list() ?: []).sort { it.pair }
    }

    List<AvailablePair> listAvailable(String quoteName) {
        String quote = quoteName?.trim()?.toUpperCase()
        return marketDataClient.availablePairs
                .findAll { AvailablePair available -> !quote || available.quoteName == quote }
                .sort { it.pair }
    }

    TrackedPair findTracked(String pair) {
        String normalized = CandleService.normalizePair(pair)
        return listTracked().find { it.pair == normalized }
    }

    TrackedPair track(String pair) {
        String normalized = CandleService.normalizePair(pair)
        TrackedPair existing = findTracked(normalized)
        if (existing) {
            return existing
        }
        AvailablePair available = marketDataClient.availablePairs.find { it.pair == normalized }
        if (!available) {
            throw new IllegalArgumentException("Kraken does not list pair '${normalized}'; see GET /pair/available for valid names")
        }
        return repository.create(new TrackedPair(pair: available.pair, baseName: available.baseName,
                quoteName: available.quoteName, createdDate: new Date()))
    }

    TrackedPair untrack(String pair) {
        TrackedPair existing = findTracked(pair)
        if (!existing) {
            throw new IllegalArgumentException("Pair '${pair}' is not tracked")
        }
        return repository.delete(existing.id)
    }
}
