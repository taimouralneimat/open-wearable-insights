package com.openwearableinsights.api.insights.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openwearableinsights.api.insights.application.DeterministicInsightEngine.CitedMetric;
import com.openwearableinsights.api.insights.application.DeterministicInsightEngine.FactorSummary;
import com.openwearableinsights.api.insights.application.DeterministicInsightEngine.HabitCue;
import com.openwearableinsights.api.insights.application.DeterministicInsightEngine.Insight;
import com.openwearableinsights.api.insights.application.DeterministicInsightEngine.WhyAnswer;
import com.openwearableinsights.api.readiness.domain.FactorContribution;
import com.openwearableinsights.api.readiness.domain.FactorContribution.Direction;
import com.openwearableinsights.api.readiness.domain.ReadinessScore;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LlmInsightService} — focused on the property that
 * matters most (see the class's own javadoc): every failure mode (disabled,
 * malformed response, exception, oversized text) falls back cleanly to
 * {@code Optional.empty()} rather than throwing, and a well-formed response
 * is parsed correctly, across all three coach touchpoints (daily insight,
 * why-answer, habit-cue).
 *
 * <p>Uses {@code spy()} to stub {@link LlmInsightService#postToOllama}
 * directly rather than mocking {@link RestClient}'s fluent interface —
 * mocking that interface's multi-hop, overloaded-method chain (three
 * separate {@code body(...)} overloads on {@code RequestBodySpec}) proved
 * unreliable with both {@code RETURNS_DEEP_STUBS} and explicit stepwise
 * mocks, so the HTTP call itself is isolated behind a package-private seam
 * instead (see that method's own javadoc).
 *
 * <p>Real end-to-end behavior against a genuine local Ollama server was
 * separately live-tested this session (see the class javadoc "Live-verified
 * this session") — not repeated here since that requires an actual running
 * Ollama instance this automated suite can't depend on.
 */
class LlmInsightServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MODEL = "qwen3:8b";
    private static final String BASE_URL = "http://127.0.0.1:11434";

    private static ReadinessScore score() {
        return new ReadinessScore(
                72, "0.2", false, "28-day rolling", "medium", "high",
                List.of(new FactorContribution("HRV deviation", 3.2, "ms", Direction.POSITIVE, 12.0, "raw")),
                "none", "Deterministic explanation.", "v0.2 weights are estimates.", Instant.now()
        );
    }

    private static Insight deterministicInsight() {
        return new Insight(
                "Good readiness: 72/100",
                "Deterministic summary text.",
                List.of(new FactorSummary("HRV deviation", "3.2 ms", "positive")),
                List.of("Consider a harder session today."),
                "medium",
                List.of("This is a wellness metric, not a medical assessment."),
                List.of("v0.2 weights are initial estimates, not empirically tuned."),
                false,
                false
        );
    }

    private static WhyAnswer deterministicWhyAnswer() {
        return new WhyAnswer(
                "Deterministic why-answer text.",
                List.of(new CitedMetric("HRV deviation", "3.2 ms", "12.0", "positive")),
                "medium",
                List.of("v0.2 weights are initial estimates, not empirically tuned."),
                false,
                false
        );
    }

    private static HabitCue deterministicHabitCue() {
        return new HabitCue(
                true, "HRV deviation", 12.0, "Mental wellbeing", "Meditation/mindfulness",
                "Deterministic reasoning text.", "medium", false, false
        );
    }

    private static JsonNode ollamaResponse(String assistantContent) throws Exception {
        return OBJECT_MAPPER.readTree(
                "{\"message\": {\"role\": \"assistant\", \"content\": " + OBJECT_MAPPER.writeValueAsString(assistantContent) + "}}");
    }

    /** A real instance (RestClient.Builder is a harmless placeholder, never actually invoked once spied). */
    private static LlmInsightService spyService(boolean enabled) {
        RestClient.Builder builder = mock(RestClient.Builder.class);
        when(builder.baseUrl(any(String.class))).thenReturn(builder);
        when(builder.build()).thenReturn(mock(RestClient.class));
        return spy(new LlmInsightService(builder, OBJECT_MAPPER, enabled, MODEL, BASE_URL));
    }

    @Test
    void disabled_isUnavailable_andNeverAttemptsACallOnAnyTouchpoint() {
        LlmInsightService service = spyService(false);

        assertThat(service.isAvailable()).isFalse();
        assertThat(service.tryRephraseSummary(score(), deterministicInsight())).isEmpty();
        assertThat(service.tryRephraseWhyAnswer(score(), deterministicWhyAnswer())).isEmpty();
        assertThat(service.tryRephraseHabitCue(deterministicHabitCue())).isEmpty();
    }

    @Test
    void wellFormedJsonResponse_returnsTheParsedSummary() throws Exception {
        LlmInsightService service = spyService(true);
        doReturn(ollamaResponse("{\"text\": \"You're recovering well today.\"}"))
                .when(service).postToOllama(any(ObjectNode.class));

        assertThat(service.isAvailable()).isTrue();
        Optional<String> result = service.tryRephraseSummary(score(), deterministicInsight());

        assertThat(result).contains("You're recovering well today.");
    }

    @Test
    void wellFormedJsonResponse_returnsTheParsedWhyAnswer() throws Exception {
        LlmInsightService service = spyService(true);
        doReturn(ollamaResponse("{\"text\": \"Your HRV is helping most today.\"}"))
                .when(service).postToOllama(any(ObjectNode.class));

        Optional<String> result = service.tryRephraseWhyAnswer(score(), deterministicWhyAnswer());

        assertThat(result).contains("Your HRV is helping most today.");
    }

    @Test
    void wellFormedJsonResponse_returnsTheParsedHabitCue() throws Exception {
        LlmInsightService service = spyService(true);
        doReturn(ollamaResponse("{\"text\": \"A few minutes of mindfulness could help today.\"}"))
                .when(service).postToOllama(any(ObjectNode.class));

        Optional<String> result = service.tryRephraseHabitCue(deterministicHabitCue());

        assertThat(result).contains("A few minutes of mindfulness could help today.");
    }

    @Test
    void jsonWrappedInProseOrMarkdownFences_stillExtractsTheObject() throws Exception {
        LlmInsightService service = spyService(true);
        doReturn(ollamaResponse("Sure, here you go:\n```json\n{\"text\": \"Great HRV trend today.\"}\n```"))
                .when(service).postToOllama(any(ObjectNode.class));

        Optional<String> result = service.tryRephraseSummary(score(), deterministicInsight());

        assertThat(result).contains("Great HRV trend today.");
    }

    @Test
    void malformedNonJsonResponse_fallsBackToEmpty() throws Exception {
        LlmInsightService service = spyService(true);
        doReturn(ollamaResponse("I cannot help with that right now."))
                .when(service).postToOllama(any(ObjectNode.class));

        assertThat(service.tryRephraseSummary(score(), deterministicInsight())).isEmpty();
    }

    @Test
    void blankTextField_fallsBackToEmpty() throws Exception {
        LlmInsightService service = spyService(true);
        doReturn(ollamaResponse("{\"text\": \"\"}")).when(service).postToOllama(any(ObjectNode.class));

        assertThat(service.tryRephraseSummary(score(), deterministicInsight())).isEmpty();
    }

    @Test
    void oversizedText_fallsBackToEmpty() throws Exception {
        String tooLong = "x".repeat(1000);
        LlmInsightService service = spyService(true);
        doReturn(ollamaResponse("{\"text\": \"" + tooLong + "\"}")).when(service).postToOllama(any(ObjectNode.class));

        assertThat(service.tryRephraseSummary(score(), deterministicInsight())).isEmpty();
    }

    @Test
    void missingMessageContent_fallsBackToEmpty() throws Exception {
        LlmInsightService service = spyService(true);
        doReturn(OBJECT_MAPPER.readTree("{}")).when(service).postToOllama(any(ObjectNode.class));

        assertThat(service.tryRephraseSummary(score(), deterministicInsight())).isEmpty();
    }

    @Test
    void nullResponse_fallsBackToEmpty() {
        LlmInsightService service = spyService(true);
        doReturn(null).when(service).postToOllama(any(ObjectNode.class));

        assertThat(service.tryRephraseSummary(score(), deterministicInsight())).isEmpty();
    }

    @Test
    void restCallThrows_fallsBackToEmptyRatherThanPropagating() {
        LlmInsightService service = spyService(true);
        doThrow(new RuntimeException("connection refused")).when(service).postToOllama(any(ObjectNode.class));

        assertThat(service.tryRephraseSummary(score(), deterministicInsight())).isEmpty();
    }
}
