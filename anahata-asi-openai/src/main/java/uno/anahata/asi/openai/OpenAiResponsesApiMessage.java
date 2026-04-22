/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.openai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.asi.agi.Agi;
import uno.anahata.asi.agi.provider.FinishReason;
import uno.anahata.asi.internal.JacksonUtils;

/**
 * Implementation of {@link OpenAiModelMessage} for the newer OpenAI Responses API (/v1/responses).
 * Handles the "Items" architecture (message, reasoning, tool_call) and provides persistent reasoning support.
 * 
 * @author anahata
 */
@Slf4j
public class OpenAiResponsesApiMessage extends OpenAiModelMessage {

    /**
     * Stores the raw items from the Responses API to support persistent
     * reasoning in future turns.
     */
    @Getter
    private final List<JsonNode> persistentItems = new ArrayList<>();

    private transient Map<Integer, StringBuilder> callArgsBuffers;
    private transient Map<Integer, String> callIds;
    private transient Map<Integer, String> callNames;

    public OpenAiResponsesApiMessage(Agi agi, String modelId) {
        super(agi, modelId);
        this.callArgsBuffers = new HashMap<>();
        this.callIds = new HashMap<>();
        this.callNames = new HashMap<>();
    }

    /**
     * {@inheritDoc}
     * <p>Handles streaming events (response.*), authorative response objects, and static items.</p>
     */
    @Override
    public void updateFromNode(JsonNode node, ReasoningStyle reasoningStyle, String reasoningFieldName, List<String> reasoningTags) {
        String type = node.path("type").asText();
        String object = node.path("object").asText();

        // 1. Handle Response Object (Root or inside Event)
        if ("response".equals(object)) {
            JsonNode output = node.get("output");
            if (output != null && output.isArray()) {
                // IMPORTANT: When receiving a full response object, we clear existing persistent items
                // to avoid duplication if we were previously streaming.
                persistentItems.clear();
                for (JsonNode item : output) {
                    updateFromNode(item, reasoningStyle, reasoningFieldName, reasoningTags);
                }
            }
            return;
        }

        // 2. Handle Responses API Streaming Events (response.*)
        if (type.startsWith("response.")) {
            if ("response.output_text.delta".equals(type)) {
                appendContent(node.path("delta").asText());
            } else if ("response.refusal.delta".equals(type)) {
                appendThoughts("[Refusal] " + node.path("delta").asText());
            } else if ("response.reasoning_summary_text.delta".equals(type)) {
                appendThoughts(node.path("delta").asText());
            } else if ("response.reasoning_text.delta".equals(type)) {
                appendThoughts(node.path("delta").asText());
            } else if ("response.function_call_arguments.delta".equals(type)) {
                String delta = node.path("delta").asText();
                callArgsBuffers.computeIfAbsent(0, k -> new StringBuilder()).append(delta);
                callIds.put(0, node.path("item_id").asText());
            } else if ("response.output_item.done".equals(type)) {
                JsonNode item = node.get("item");
                if (item != null) {
                    addItemIfMissing(item);
                    if ("function_call".equals(item.path("type").asText())) {
                        JsonNode fc = item.get("function_call");
                        if (fc != null) {
                            callNames.put(0, fc.path("name").asText());
                        }
                    }
                }
            } else if ("response.done".equals(type)) {
                JsonNode resp = node.get("response");
                if (resp != null) {
                    if ("completed".equals(resp.path("status").asText())) {
                        setFinishReason(FinishReason.STOP);
                    } else if ("incomplete".equals(resp.path("status").asText())) {
                        setFinishReasonFromOpenAi(resp.path("incomplete_details").path("reason").asText());
                    }
                }
                flushToolCalls();
            } else if ("response.completed".equals(type)) {
                JsonNode resp = node.get("response");
                if (resp != null) {
                    updateFromNode(resp, reasoningStyle, reasoningFieldName, reasoningTags);
                }
            }
            return;
        }

        // 3. Handle Responses API Static Items (non-streaming or full response)
        if ("message".equals(type) || "reasoning".equals(type) || "tool_call".equals(type)) {
            addItemIfMissing(node);
        }

        if ("message".equals(type)) {
            JsonNode content = node.get("content");
            if (content != null && content.isArray()) {
                for (JsonNode part : content) {
                    String partType = part.path("type").asText();
                    if ("output_text".equals(partType)) {
                        appendContent(part.path("text").asText());
                    } else if ("refusal".equals(partType)) {
                        appendThoughts("[Refusal] " + part.path("refusal").asText());
                    }
                }
            }
            if (node.has("tool_calls")) {
                for (JsonNode callNode : node.get("tool_calls")) {
                    updateToolCall(callNode);
                }
            }
        } else if ("reasoning".equals(type)) {
            JsonNode summary = node.get("summary");
            if (summary != null && summary.isArray()) {
                for (JsonNode part : summary) {
                    if ("summary_text".equals(part.path("type").asText())) {
                        appendThoughts(part.path("text").asText());
                    }
                }
            }
        } else if ("tool_call".equals(type)) {
            updateToolCall(node.get("tool_call"));
        }

        // 4. Finish Reason / Status
        if (node.has("status") && "completed".equals(node.path("status").asText())) {
            setFinishReason(FinishReason.STOP);
            flushToolCalls();
        } else if (node.has("finish_reason") && !node.get("finish_reason").isNull()) {
            setFinishReasonFromOpenAi(node.get("finish_reason").asText());
        }
    }

    private void addItemIfMissing(JsonNode item) {
        String id = item.path("id").asText(null);
        if (id != null) {
            boolean exists = persistentItems.stream()
                    .anyMatch(n -> id.equals(n.path("id").asText()));
            if (!exists) {
                persistentItems.add(item);
            }
        } else {
            persistentItems.add(item);
        }
    }

    @Override
    public void updateToolCall(JsonNode callNode) {
        if (callArgsBuffers == null) callArgsBuffers = new HashMap<>();
        if (callIds == null) callIds = new HashMap<>();
        if (callNames == null) callNames = new HashMap<>();

        String callId = callNode.path("id").asText(null);
        int index = callNode.path("index").asInt(0); 
        JsonNode funcNode = callNode.get("function");

        if (callId != null) {
            callIds.put(index, callId);
            if (funcNode != null && funcNode.has("name")) {
                callNames.put(index, funcNode.get("name").asText());
            }
        }

        if (funcNode != null && funcNode.has("arguments")) {
            String argsFragment = funcNode.get("arguments").asText("");
            if (!argsFragment.isEmpty()) {
                callArgsBuffers.computeIfAbsent(index, k -> new StringBuilder()).append(argsFragment);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flushToolCalls() {
        if (callArgsBuffers == null) return;
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
        callArgsBuffers = null;
        callIds = null;
        callNames = null;
    }
}
