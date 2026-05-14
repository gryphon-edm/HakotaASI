/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.anthropic;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.asi.agi.provider.AbstractAiProvider;
import uno.anahata.asi.agi.provider.AbstractModel;
import uno.anahata.asi.agi.provider.TokenizerType;
import uno.anahata.asi.internal.JacksonUtils;

/**
 * Provider implementation for the official Anthropic API.
 * 
 * @author anahata
 */
@Slf4j
@Getter
@Setter
public class AnthropicProvider extends AbstractAiProvider {

    private String baseUrl;
    private String anthropicVersion;
    private transient HttpClient httpClient;

    public AnthropicProvider() {
        this("Anthropic", "Anthropic (Claude)", "https://api.anthropic.com/v1", "2023-06-01", "https://console.anthropic.com/settings/keys");
    }

    public AnthropicProvider(String uuid, String displayName, String baseUrl, String anthropicVersion, String keysAcquisitionUri) {
        super(uuid);
        setDisplayName(displayName);
        this.baseUrl = baseUrl;
        this.anthropicVersion = anthropicVersion;
        setTokenizerType(TokenizerType.CL100K_BASE); // Change when Claude tokenizer is available
        setKeysAcquisitionUri(keysAcquisitionUri);
    }

    public synchronized HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();
        }
        return httpClient;
    }

    public HttpRequest.Builder createRequestBuilder(String endpoint) {
        String fullUrl = baseUrl.endsWith("/") ? baseUrl + endpoint : baseUrl + "/" + endpoint;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header("content-type", "application/json")
                .timeout(Duration.ofSeconds(120));
                
        String apiKey = getCurrentApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("x-api-key", apiKey);
        }
        if (anthropicVersion != null && !anthropicVersion.isBlank()) {
            builder.header("anthropic-version", anthropicVersion);
        }
        return builder;
    }

    @Override
    public List<? extends AbstractModel> listModels() {
        log.info("Fetching Anthropic-compatible models from {}", baseUrl);
        try {
            String apiKey = getCurrentApiKey();
            if (apiKey != null) {
                HttpRequest request = createRequestBuilder("models").GET().build();
                java.net.http.HttpResponse<String> response = getHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    com.fasterxml.jackson.databind.JsonNode root = JacksonUtils.parse(response.body(), com.fasterxml.jackson.databind.JsonNode.class);
                    com.fasterxml.jackson.databind.JsonNode data = root.get("data");
                    if (data != null && data.isArray()) {
                        List<AnthropicModel> models = new ArrayList<>();
                        for (com.fasterxml.jackson.databind.JsonNode modelNode : data) {
                            String id = modelNode.path("id").asText();
                            String name = modelNode.path("display_name").asText(id);
                            models.add(new AnthropicModel(this, id, name));
                        }
                        return models;
                    }
                } else {
                    log.error("Failed to list models: {} - {}", response.statusCode(), response.body());
                }
            }
        } catch (Exception e) {
            log.error("Error listing Anthropic models", e);
        }
        
        // Fallback for Anthropic if the models endpoint fails or isn't available
        if ("Anthropic".equals(getUuid())) {
            List<AnthropicModel> models = new ArrayList<>();
            models.add(new AnthropicModel(this, "claude-3-5-sonnet-latest", "Claude 3.5 Sonnet"));
            models.add(new AnthropicModel(this, "claude-3-5-haiku-latest", "Claude 3.5 Haiku"));
            models.add(new AnthropicModel(this, "claude-3-opus-latest", "Claude 3 Opus"));
            return models;
        }
        
        return List.of();
    }

    @Override
    public String getApiKeyHint() {
        return "# Anthropic API Key Configuration\n"
                + "sk-ant-...\n";
    }
    
    /**
     * Determines if an HTTP status code represents a retryable error.
     * 
     * @param statusCode The HTTP status code.
     * @param responseBody The response body.
     * @return true if the request should be retried.
     */
    public boolean isRetryable(int statusCode, String responseBody) {
        return statusCode == 429 || statusCode == 529 || statusCode == 503 || statusCode == 500 || statusCode == 499 || statusCode == 408;
    }
    
    @Override
    public synchronized void hokusPocus() {
        super.hokusPocus();
    }
}