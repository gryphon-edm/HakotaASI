/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.openai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.asi.agi.Agi;
import uno.anahata.asi.agi.message.AbstractModelMessage;
import uno.anahata.asi.agi.provider.FinishReason;
import uno.anahata.asi.internal.JacksonUtils;

/**
 * An OpenAI-specific implementation of {@link AbstractModelMessage}.
 * It parses the choices and tool calls from the OpenAI API response JSON 
 * into the Anahata domain model.
 * 
 * @author anahata
 */
@Slf4j
public class OpenAiModelMessage extends AbstractModelMessage<OpenAiResponse> {

    /**
     * Constructs a message from a choices node.
     */
    public OpenAiModelMessage(Agi agi, String modelId, JsonNode choiceNode, OpenAiResponse response) {
        super(agi, modelId);
        setResponse(response);
        parseChoice(choiceNode);
    }

    /**
     * Parses the content and tool calls from a choice node.
     */
    private void parseChoice(JsonNode choice) {
        JsonNode messageNode = choice.get("message");
        if (messageNode == null) return;
        
        // 1. Parse Text Content
        if (messageNode.has("content") && !messageNode.get("content").isNull()) {
            addTextPart(messageNode.get("content").asText());
        }
        
        // 2. Parse Tool Calls
        if (messageNode.has("tool_calls")) {
            for (JsonNode call : messageNode.get("tool_calls")) {
                String id = call.get("id").asText();
                JsonNode func = call.get("function");
                String name = func.get("name").asText();
                String argsJson = func.get("arguments").asText();
                
                Map<String, Object> args = JacksonUtils.parse(argsJson, Map.class);
                getAgi().getToolManager().createToolCall(this, id, name, args);
            }
        }
        
        // 3. Finish Reason
        if (choice.has("finish_reason")) {
            setFinishReason(mapFinishReason(choice.get("finish_reason").asText()));
        }
    }

    private FinishReason mapFinishReason(String reason) {
        return switch (reason) {
            case "stop" -> FinishReason.STOP;
            case "length" -> FinishReason.MAX_TOKENS;
            case "tool_calls" -> FinishReason.STOP; // Logical continuation
            case "content_filter" -> FinishReason.SAFETY;
            default -> FinishReason.OTHER;
        };
    }
}
