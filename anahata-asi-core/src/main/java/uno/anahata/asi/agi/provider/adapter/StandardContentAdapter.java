/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.provider.adapter;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.asi.agi.message.AbstractMessage;
import uno.anahata.asi.agi.message.AbstractModelMessage;
import uno.anahata.asi.agi.message.AbstractPart;
import uno.anahata.asi.agi.message.BlobPart;
import uno.anahata.asi.agi.message.ModelTextPart;
import uno.anahata.asi.agi.message.Role;
import uno.anahata.asi.agi.message.TextPart;
import uno.anahata.asi.agi.tool.schema.SchemaProvider;
import uno.anahata.asi.agi.tool.spi.AbstractToolCall;
import uno.anahata.asi.agi.tool.spi.AbstractToolResponse;

/**
 * A universal content adapter that translates Anahata's domain model into 
 * standard OpenAI-compatible JSON objects.
 * <p>
 * This adapter is the definitive "Core" implementation for all OpenAI-compatible 
 * providers. It strictly follows the Anahata V2 pruning and metadata strategies, 
 * ensuring that models remain context-aware even as the history is garbage-collected.
 * </p>
 * <p>
 * For {@link AbstractModelMessage}s, it performs a 1-to-N synthesis, creating 
 * an 'assistant' message for content and tool calls, followed by multiple 
 * 'tool' messages for responses.
 * </p>
 * 
 * @author anahata
 */
@Slf4j
@RequiredArgsConstructor
public class StandardContentAdapter {

    /** The Anahata message to be translated. */
    private final AbstractMessage anahataMessage;
    
    /** Whether to include parts that have been effectively pruned by the GC. */
    private final boolean includePruned;

    /**
     * Translates the Anahata message into a list of OpenAI-style message objects.
     * 
     * @return A list of ObjectNodes representing the OpenAI messages.
     */
    public List<ObjectNode> toOpenAi() {
        Role role = anahataMessage.getRole();
        List<ObjectNode> results = new ArrayList<>();
        
        if (role == Role.MODEL && anahataMessage instanceof AbstractModelMessage<?> modelMsg) {
            results.addAll(toOpenAiModel(modelMsg));
        } else {
            ObjectNode userNode = toOpenAiUser();
            if (userNode != null) {
                results.add(userNode);
            }
        }
        
        return results;
    }

    /**
     * Synthesizes a MODEL role message into multiple OpenAI API messages:
     * 1. An 'assistant' role content containing text and tool calls.
     * 2. One or more 'tool' role contents for each executed response.
     */
    private List<ObjectNode> toOpenAiModel(AbstractModelMessage<?> modelMsg) {
        List<ObjectNode> synthesized = new ArrayList<>();
        
        ObjectNode assistantNode = SchemaProvider.OBJECT_MAPPER.createObjectNode();
        assistantNode.put("role", "assistant");
        
        StringBuilder textContent = new StringBuilder();
        
        // 1. Message Metadata Header
        if (shouldInjectInbandMetadata()) {
            textContent.append(anahataMessage.createMetadataHeader()).append("\n");
        }
        
        ArrayNode toolCalls = SchemaProvider.OBJECT_MAPPER.createArrayNode();
        
        // 2. Interleave Parts with Metadata Headers
        for (AbstractPart part : anahataMessage.getParts(true)) {
            addPartWithMetadata(textContent, toolCalls, part);
        }
        
        // OpenAI requirement: content can be null but not an empty string if tool_calls is present.
        if (textContent.length() > 0) {
            assistantNode.put("content", textContent.toString());
        } else {
            assistantNode.putNull("content");
        }
        
        if (toolCalls.size() > 0) {
            assistantNode.set("tool_calls", toolCalls);
        }
        
        // Only add the assistant message if it has content or tool calls.
        if (textContent.length() > 0 || toolCalls.size() > 0) {
            synthesized.add(assistantNode);
        }
        
        // 3. Synthesize individual 'tool' messages for responses
        // We only include responses if the corresponding call is not effectively pruned.
        List<AbstractToolResponse<?>> executedResponses = modelMsg.getToolResponses().stream()
                .filter(tr -> includePruned || !tr.getCall().isEffectivelyPruned())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        for (AbstractToolResponse<?> tr : executedResponses) {
            ObjectNode responseNode = SchemaProvider.OBJECT_MAPPER.createObjectNode();
            responseNode.put("role", "tool");
            responseNode.put("tool_call_id", tr.getCall().getId());
            
            // Standard JSON serialization for the result content.
            responseNode.put("content", SchemaProvider.OBJECT_MAPPER.valueToTree(tr.getResult()).toString());
            synthesized.add(responseNode);
        }
        
        return synthesized;
    }

    /**
     * Translates a USER or SYSTEM role message into a single OpenAI message node.
     */
    private ObjectNode toOpenAiUser() {
        ObjectNode msgNode = SchemaProvider.OBJECT_MAPPER.createObjectNode();
        msgNode.put("role", anahataMessage.getRole().name().toLowerCase());
        
        // Multimodal support: content can be an array of objects
        ArrayNode contentArray = SchemaProvider.OBJECT_MAPPER.createArrayNode();
        
        // 1. Message Metadata Header
        if (shouldInjectInbandMetadata()) {
            contentArray.addObject()
                    .put("type", "text")
                    .put("text", anahataMessage.createMetadataHeader() + "\n");
        }
        
        // 2. Iterate over parts with metadata interleaving
        for (AbstractPart part : anahataMessage.getParts(true)) {
            boolean isEffectivelyPruned = part.isEffectivelyPruned();
            boolean shouldIncludeContent = !isEffectivelyPruned || includePruned;

            if (shouldInjectInbandMetadata()) {
                contentArray.addObject()
                        .put("type", "text")
                        .put("text", part.createMetadataHeader() + "\n");
            }

            if (shouldIncludeContent) {
                if (part instanceof TextPart tp) {
                    contentArray.addObject()
                            .put("type", "text")
                            .put("text", tp.getText());
                } else if (part instanceof BlobPart bp) {
                    ObjectNode imageNode = contentArray.addObject();
                    imageNode.put("type", "image_url");
                    ObjectNode urlNode = imageNode.putObject("image_url");
                    urlNode.put("url", "data:" + bp.getMimeType() + ";base64," + 
                                Base64.getEncoder().encodeToString(bp.getData()));
                }
            }
        }
        
        if (contentArray.isEmpty()) {
            return null;
        }

        // Simplification: If only one text part, use 'content' as a string
        if (contentArray.size() == 1 && "text".equals(contentArray.get(0).get("type").asText())) {
            msgNode.put("content", contentArray.get(0).get("text").asText());
        } else {
            msgNode.set("content", contentArray);
        }
        
        return msgNode;
    }

    /**
     * Internal helper to add a part and its metadata to the assistant's buffers.
     */
    private void addPartWithMetadata(StringBuilder textContent, ArrayNode toolCalls, AbstractPart part) {
        boolean isEffectivelyPruned = part.isEffectivelyPruned();
        boolean shouldIncludeContent = !isEffectivelyPruned || includePruned;

        if (shouldInjectInbandMetadata()) {
            textContent.append(part.createMetadataHeader()).append("\n");
        }

        if (shouldIncludeContent) {
            if (part instanceof ModelTextPart mtp) {
                textContent.append(mtp.getText());
            } else if (part instanceof AbstractToolCall<?, ?> tc) {
                ObjectNode callNode = toolCalls.addObject();
                callNode.put("id", tc.getId());
                callNode.put("type", "function");
                ObjectNode funcNode = callNode.putObject("function");
                funcNode.put("name", tc.getToolName());
                funcNode.set("arguments", SchemaProvider.OBJECT_MAPPER.valueToTree(tc.getArgs()));
            }
        }
    }

    /**
     * Checks if in-band metadata should be injected based on the AGI's current request configuration.
     */
    private boolean shouldInjectInbandMetadata() {
        return anahataMessage.getAgi().getRequestConfig().isInjectInbandMetadata() && anahataMessage.shouldCreateMetadata();
    }
}
