/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.openai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.asi.agi.Agi;
import uno.anahata.asi.agi.message.AbstractModelMessage;
import uno.anahata.asi.agi.message.AbstractPart;
import uno.anahata.asi.agi.message.ModelTextPart;
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
     * Buffers for accumulating streaming tool call arguments, keyed by their index in the 'choices' array.
     */
    private final transient Map<Integer, StringBuilder> callArgsBuffers;
    private final transient Map<Integer, String> callIds;
    private final transient Map<Integer, String> callNames;

    /**
     * Constructs a new OpenAI model message by parsing a specific choice
     * from the API response.
     * @param agi The parent session.
     * @param modelId The ID of the model that generated the message.
     * @param choiceNode The JSON node containing the choice data.
     * @param response The parent response object.
     */
    public OpenAiModelMessage(Agi agi, String modelId, JsonNode choiceNode, OpenAiResponse response) {
        super(agi, modelId);
        this.callArgsBuffers = new HashMap<>();
        this.callIds = new HashMap<>();
        this.callNames = new HashMap<>();
        setResponse(response);
        parseChoice(choiceNode);
    }

    /**
     * Package-private constructor used for incremental streaming.
     * 
     * @param agi The parent session.
     * @param modelId The ID of the model.
     */
    OpenAiModelMessage(Agi agi, String modelId) {
        super(agi, modelId);
        this.callArgsBuffers = new HashMap<>();
        this.callIds = new HashMap<>();
        this.callNames = new HashMap<>();
    }

    /**
     * Extracts text content and tool calls from the OpenAI 'message' node.
     * <p>
     * Implementation details: Maps the 'content' field to a text part and
     * recursively converts each 'tool_calls' entry into a native Anahata
     * tool call via the {@link uno.anahata.asi.agi.tool.ToolManager}.
     * </p>
     * @param choice The choices array element node.
     */
    void parseChoice(JsonNode choice) {
        JsonNode messageNode = choice.get("message");
        if (messageNode == null) {
            messageNode = choice.get("delta");
        }
        
        if (messageNode == null) return;
        
        // 1. Text Content
        if (messageNode.has("content") && !messageNode.get("content").isNull()) {
            String text = messageNode.get("content").asText();
            if (!text.isEmpty()) {
                appendContent(text);
            }
        }
        
        // 2. Tool Calls
        if (messageNode.has("tool_calls")) {
            for (JsonNode callNode : messageNode.get("tool_calls")) {
                updateToolCall(callNode);
            }
        }
        
        // 3. Finish Reason
        if (choice.has("finish_reason") && !choice.get("finish_reason").isNull()) {
            String fr = choice.get("finish_reason").asText();
            setFinishReason(mapFinishReason(fr));
            // If the model is finished, flush any buffered tool calls
            if ("stop".equals(fr) || "tool_calls".equals(fr)) {
                flushToolCalls();
            }
        }
    }

    /**
     * Maps OpenAI's finish reason strings to our internal enum.
     * @param reason The raw reason string from the API.
     * @return The corresponding {@link FinishReason}.
     */
    private FinishReason mapFinishReason(String reason) {
        return switch (reason) {
            case "stop" -> FinishReason.STOP;
            case "length" -> FinishReason.MAX_TOKENS;
            case "tool_calls" -> FinishReason.STOP;
            case "content_filter" -> FinishReason.SAFETY;
            default -> FinishReason.OTHER;
        };
    }

    void appendContent(String text) {
        List<AbstractPart> parts = getParts();
        if (!parts.isEmpty() && parts.get(parts.size() - 1) instanceof ModelTextPart mtp) {
            mtp.appendText(text);
        } else {
            addTextPart(text);
        }
    }

    void updateToolCall(JsonNode callNode) {
        String callId = callNode.path("id").asText(null);
        int index = callNode.path("index").asInt(-1);
        JsonNode funcNode = callNode.get("function");
        
        if (callId != null) {
            callIds.put(index, callId);
            if (funcNode != null && funcNode.has("name")) {
                callNames.put(index, funcNode.get("name").asText());
            }
        }
        
        if (index != -1 && funcNode != null && funcNode.has("arguments")) {
            String argsFragment = funcNode.get("arguments").asText("");
            if (!argsFragment.isEmpty()) {
                callArgsBuffers.computeIfAbsent(index, k -> new StringBuilder()).append(argsFragment);
            }
        }
    }

    void flushToolCalls() {
        for (Integer index : callArgsBuffers.keySet()) {
            String id = callIds.get(index);
            String name = callNames.get(index);
            String fullJson = callArgsBuffers.get(index).toString();
            
            if (id != null && name != null && !fullJson.isEmpty()) {
                try {
                    Map<String, Object> args = JacksonUtils.parse(fullJson, Map.class);
                    getAgi().getToolManager().createToolCall(this, id, name, args);
                } catch (Exception e) {
                    log.error("Failed to parse buffered tool call arguments for index {}: {}", index, fullJson, e);
                }
            }
        }
        // Clear buffers after flushing
        callArgsBuffers.clear();
        callIds.clear();
        callNames.clear();
    }
}
