/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
/**
 * Provides the provider implementation for Google's Gemini API using the 
 * official GenAI Java SDK.
 * <p>
 * This package acts as a high-fidelity adapter between Anahata's unified 
 * ASI domain model and Gemini's multimodal and agentic capabilities. 
 * It supports standard content generation, streaming, tool calling (function calling), 
 * and advanced features like "Thinking" blocks and grounding metadata.
 * </p>
 * <p>
 * Key Components:
 * </p>
 * <ul>
 *   <li><b>Provider</b>: {@link uno.anahata.asi.gemini.GeminiAiProvider} 
 *       manages the SDK client lifecycle and API key rotation.</li>
 *   <li><b>Model Logic</b>: {@link uno.anahata.asi.gemini.GeminiModel} 
 *       handles payload preparation and response orchestration.</li>
 *   <li><b>Message Mapping</b>: {@link uno.anahata.asi.gemini.GeminiModelMessage} 
 *       and {@link uno.anahata.asi.gemini.GeminiResponse} translate 
 *       multimodal "Parts" and "Candidates" into the unified Anahata model.</li>
 * </ul>
 * 
 * @author anahata
 */
package uno.anahata.asi.gemini;
