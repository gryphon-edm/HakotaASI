/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.huggingface;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import uno.anahata.asi.openai.OpenAiModel;

/**
 * A model instance that carries additional metadata discovered from the Hugging Face Hub.
 */
@Getter
@Setter
public class HuggingFaceModel extends OpenAiModel {

    private JsonNode hubConfig;
    private JsonNode tokenizerConfig;
    private JsonNode generationConfig;
    
    private boolean supportsFunctionCalling = false;

    public HuggingFaceModel(HuggingFaceProvider provider, String modelId, String displayName) {
        super(provider, modelId, displayName);
    }

    @Override
    public boolean isSupportsFunctionCalling() {
        return supportsFunctionCalling;
    }

    @Override
    public String getDescription() {
        if (hubConfig != null && hubConfig.has("model_type")) {
            return "HF [" + hubConfig.get("model_type").asText() + "] " + getModelId();
        }
        return super.getDescription();
    }
    
    @Override
    public String getRawDescription() {
        StringBuilder sb = new StringBuilder("<html>");
        sb.append("<b>Hugging Face Model:</b> ").append(getModelId()).append("<br>");
        if (hubConfig != null) {
            sb.append("<b>Architecture:</b> ").append(hubConfig.path("model_type").asText("unknown")).append("<br>");
        }
        sb.append("<b>Function Calling:</b> ").append(isSupportsFunctionCalling()).append("<br>");
        sb.append("<b>Reasoning Style:</b> ").append(getReasoningStyle()).append("<br>");
        sb.append("</html>");
        return sb.toString();
    }
}
