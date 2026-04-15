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
    
    private boolean insideReasoningTags = false;

    /**
     * Constructs a new OpenAI model message.
     * @param agi The parent session.
     * @param modelId The ID of the model that generated the message.
     * @param choiceNode The JSON node containing the choice data.
     * @param response The parent response object.
     * @param reasoningStyle Strategy used by the model.
     * @param reasoningFieldName Field name for reasoning.
     * @param reasoningTags Tags for reasoning.
     */
    public OpenAiModelMessage(Agi agi, String modelId, JsonNode choiceNode, OpenAiResponse response,
            ReasoningStyle reasoningStyle, String reasoningFieldName, List<String> reasoningTags) {
        super(agi, modelId);
        this.callArgsBuffers = new HashMap<>();
        this.callIds = new HashMap<>();
        this.callNames = new HashMap<>();
        setResponse(response);
        // We still need to handle non-streaming initial parse if model calls it
        if (choiceNode != null) {
            parseChoice(choiceNode, reasoningStyle, reasoningFieldName, reasoningTags);
        }
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

    private void parseChoice(JsonNode choice, ReasoningStyle reasoningStyle, String reasoningFieldName, List<String> reasoningTags) {
        JsonNode messageNode = choice.get("message");
        if (messageNode == null) messageNode = choice.get("delta");
        if (messageNode == null) return;
        
        // 1. Text Content
        if (messageNode.has("content") && !messageNode.get("content").isNull()) {
            String text = messageNode.get("content").asText();
            if (reasoningStyle == ReasoningStyle.TAGS && reasoningTags != null && reasoningTags.size() >= 2) {
                appendTaggedContent(text, reasoningTags.get(0), reasoningTags.get(1));
            } else {
                appendContent(text);
            }
        }
        
        // 1.1 Reasoning Content (FIELD style)
        if (reasoningStyle == ReasoningStyle.FIELD && reasoningFieldName != null 
                && messageNode.has(reasoningFieldName) && !messageNode.get(reasoningFieldName).isNull()) {
            appendThoughts(messageNode.get(reasoningFieldName).asText());
        }
        
        // 2. Tool Calls
        if (messageNode.has("tool_calls")) {
            for (JsonNode callNode : messageNode.get("tool_calls")) {
                updateToolCall(callNode);
            }
        }
        
        // 3. Finish Reason
        if (choice.has("finish_reason") && !choice.get("finish_reason").isNull()) {
            setFinishReasonFromOpenAi(choice.get("finish_reason").asText());
        }
    }

    public void setFinishReasonFromOpenAi(String fr) {
        setFinishReason(mapFinishReason(fr));
        if ("stop".equals(fr) || "tool_calls".equals(fr)) {
            flushToolCalls();
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

    public void appendContent(String text) {
        List<AbstractPart> parts = getParts();
        if (!parts.isEmpty() && parts.get(parts.size() - 1) instanceof ModelTextPart mtp && !mtp.isThought()) {
            mtp.appendText(text);
        } else {
            addTextPart(text);
        }
    }

    public void appendThoughts(String text) {
        List<AbstractPart> parts = getParts();
        if (!parts.isEmpty() && parts.get(parts.size() - 1) instanceof ModelTextPart mtp && mtp.isThought()) {
            mtp.appendText(text);
        } else {
            addTextPart(text, null, true);
        }
    }

    public void appendTaggedContent(String text, String startTag, String endTag) {
        // Simplified tag handling: if start tag in text, start thoughts. 
        // If end tag, end thoughts.
        if (!insideReasoningTags && text.contains(startTag)) {
            int idx = text.indexOf(startTag);
            String before = text.substring(0, idx);
            if (!before.isEmpty()) appendContent(before);
            insideReasoningTags = true;
            appendTaggedContent(text.substring(idx + startTag.length()), startTag, endTag);
        } else if (insideReasoningTags && text.contains(endTag)) {
            int idx = text.indexOf(endTag);
            String thoughts = text.substring(0, idx);
            if (!thoughts.isEmpty()) appendThoughts(thoughts);
            insideReasoningTags = false;
            appendTaggedContent(text.substring(idx + endTag.length()), startTag, endTag);
        } else {
            if (insideReasoningTags) appendThoughts(text);
            else appendContent(text);
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
