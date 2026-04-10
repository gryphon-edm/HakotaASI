/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.openai;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import uno.anahata.asi.agi.provider.AbstractAgiProvider;
import uno.anahata.asi.agi.provider.AbstractModel;
import uno.anahata.asi.agi.provider.TokenizerType;

/**
 * A universal AI provider for any API endpoint compatible with the OpenAI 
 * Chat Completion specification.
 * <p>
 * This provider allows the user to configure a custom {@code baseUrl}, enabling 
 * seamless integration with services like Groq, DeepSeek, or local inference 
 * servers like Ollama and vLLM.
 * </p>
 * 
 * @author anahata
 */
@Getter
@Setter
public class OpenAiCompatibleProvider extends AbstractAgiProvider {

    /** 
     * The base URL of the OpenAI-compatible API (e.g., 'http://localhost:11434/v1'). 
     */
    private String baseUrl;

    /**
     * No-arg constructor for Kryo.
     */
    public OpenAiCompatibleProvider() {
        super();
        setDisplayName("OpenAI Compatible (Universal)");
        setTokenizerType(TokenizerType.CL100K_BASE);
    }

    /**
     * Constructs a new universal provider.
     * 
     * @param uuid The unique ID.
     * @param displayName The display name.
     * @param baseUrl The API endpoint.
     */
    public OpenAiCompatibleProvider(String uuid, String displayName, String baseUrl) {
        this(uuid, displayName, baseUrl, null);
    }

    /**
     * Constructs a new universal provider with a custom folder name.
     * 
     * @param uuid The unique ID.
     * @param displayName The display name.
     * @param baseUrl The API endpoint.
     * @param folderName The custom folder name for configuration.
     */
    public OpenAiCompatibleProvider(String uuid, String displayName, String baseUrl, String folderName) {
        super(uuid);
        setDisplayName(displayName);
        this.baseUrl = baseUrl;
        setFolderName(folderName);
        setTokenizerType(TokenizerType.CL100K_BASE);
    }

    /** {@inheritDoc} */
    @Override
    public String getCurrentApiKey() {
        return getNextKey();
    }

    /** {@inheritDoc} */
    @Override
    public List<? extends AbstractModel> listModels() {
        // TODO: Implement /v1/models fetching if baseUrl is set.
        // For now, we return a small default set or allow manual entry in GUI.
        List<OpenAiModel> defaults = new ArrayList<>();
        // Example placeholders
        defaults.add(new OpenAiModel(this, "gpt-4o", "GPT-4o"));
        return defaults;
    }

    /** {@inheritDoc} */
    @Override
    public URI getKeysAcquisitionUri() {
        return URI.create("https://platform.openai.com/api-keys");
    }

    /** {@inheritDoc} */
    @Override
    public String getApiKeyHint() {
        return "# OpenAI-Compatible API Key Configuration\n"
                + "# Add your keys below (one per line).";
    }
}
