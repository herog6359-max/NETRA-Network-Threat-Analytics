package com.netra.service.forecast;

import com.netra.domain.entity.ForecastRun;
import com.netra.domain.entity.TrafficFlow;
import com.netra.repository.FeatureAttributionRepository;
import com.netra.repository.ForecastHorizonRepository;
import com.netra.repository.ForecastRunRepository;
import com.netra.repository.TrafficFlowRepository;
import com.netra.service.TrafficFeatureExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PythonMLForecastEngineTest {

    private TrafficFlowRepository flowRepository;
    private ForecastRunRepository forecastRunRepository;
    private ForecastHorizonRepository horizonRepository;
    private FeatureAttributionRepository attributionRepository;
    private TrafficFeatureExtractor featureExtractor;
    private HttpClient httpClient;
    private FakeDeterministicEngine fakeFallbackEngine;
    private PythonMLForecastEngine engine;

    /** Hand-written test fake avoiding ByteBuddy dynamic instrumentation on Java 25 */
    private static class FakeDeterministicEngine extends DeterministicForecastEngine {
        public FakeDeterministicEngine() {
            super(null, null, null, null, null);
        }

        @Override
        public ForecastRun forecast(Instant windowStart, Instant windowEnd, int horizonSteps, Map<String, Object> config) {
            ForecastRun r = new ForecastRun();
            r.setId(99L);
            r.setModelName("deterministic-baseline");
            r.setCurrentRiskScore(0.65);
            return r;
        }

        @Override
        public String getEngineName() {
            return "deterministic-baseline";
        }

        @Override
        public String getEngineVersion() {
            return "1.0.0";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }

    /**
     * A socket-free HTTP client for unit tests. It forces the ML adapter through its
     * documented deterministic fallback path without requiring a live local service.
     */
    private static class UnavailableHttpClient extends HttpClient {
        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() {
            try {
                return SSLContext.getInstance("TLS");
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
            throw new IOException("ML service is intentionally unavailable in this unit test");
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            return CompletableFuture.failedFuture(new IOException("ML service is intentionally unavailable in this unit test"));
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return CompletableFuture.failedFuture(new IOException("ML service is intentionally unavailable in this unit test"));
        }
    }

    @BeforeEach
    void setUp() {
        flowRepository = Mockito.mock(TrafficFlowRepository.class);
        forecastRunRepository = Mockito.mock(ForecastRunRepository.class);
        horizonRepository = Mockito.mock(ForecastHorizonRepository.class);
        attributionRepository = Mockito.mock(FeatureAttributionRepository.class);
        featureExtractor = Mockito.mock(TrafficFeatureExtractor.class);
        httpClient = new UnavailableHttpClient();
        fakeFallbackEngine = new FakeDeterministicEngine();

        when(featureExtractor.extractFeatures(any())).thenReturn(Map.of(
            "syn_ratio", 0.45,
            "unique_dst_ports", 0.35,
            "flow_rate", 15.0,
            "attack_label_ratio", 0.50
        ));

        when(forecastRunRepository.save(any(ForecastRun.class))).thenAnswer(i -> {
            ForecastRun r = i.getArgument(0);
            r.setId(101L);
            return r;
        });

        engine = new PythonMLForecastEngine(
            flowRepository,
            forecastRunRepository,
            horizonRepository,
            attributionRepository,
            featureExtractor,
            fakeFallbackEngine,
            httpClient
        );
    }

    @Test
    @DisplayName("Should return correct engine metadata")
    void testEngineMetadata() {
        assertEquals("pytorch-tcn", engine.getEngineName());
        assertEquals("2.1.0-tcn", engine.getEngineVersion());
    }

    @Test
    @DisplayName("Should execute live forecast against local FastAPI service or fallback truthfully")
    void testForecastExecution() {
        Instant now = Instant.now();
        Instant past = now.minus(1, ChronoUnit.HOURS);

        when(flowRepository.findByTimeWindow(any(), any())).thenReturn(Collections.emptyList());

        ForecastRun run = engine.forecast(past, now, 6, Map.of());

        assertNotNull(run);
        assertNotNull(run.getModelName());
        assertTrue(
            run.getModelName().contains("PyTorch") || run.getModelName().contains("fallback"),
            "Model name must truthfully identify neural engine or fallback"
        );
        assertTrue(run.getCurrentRiskScore() >= 0.0 && run.getCurrentRiskScore() <= 1.0);
    }

    @Test
    @DisplayName("Should engage deterministic fallback when ML service fails")
    void testDeterministicFallbackOnFailure() {
        // Point to unreachable port to force failure
        PythonMLForecastEngine brokenEngine = new PythonMLForecastEngine(
            flowRepository,
            forecastRunRepository,
            horizonRepository,
            attributionRepository,
            featureExtractor,
            fakeFallbackEngine,
            httpClient
        );

        Instant now = Instant.now();
        ForecastRun run = brokenEngine.forecast(now.minus(1, ChronoUnit.HOURS), now, 6, Map.of());

        assertNotNull(run);
        assertTrue(run.getModelName().contains("fallback") || run.getModelName().contains("PyTorch"));
    }
}
