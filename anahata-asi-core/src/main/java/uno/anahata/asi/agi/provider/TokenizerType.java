/*
 * Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça!
 */
package uno.anahata.asi.agi.provider;

import lombok.Getter;

/**
 * Defines the supported tokenizer strategies for the Anahata ASI.
 * <p>
 * This enum allows providers to specify which encoding should be used for 
 * accurate token counting in the Context Window Garbage Collector.
 * </p>
 * 
 * @author anahata
 */
@Getter
public enum TokenizerType {
    /** OpenAI's common encoding (GPT-4, GPT-3.5, DeepSeek, Groq). */
    CL100K_BASE("OpenAI V3/V4 (Common)"),
    
    /** OpenAI's newest encoding (GPT-4o). */
    O200K_BASE("OpenAI V5 (GPT-4o)"),
    
    /** Gemini-specific tokenization (delegates to Gemini SDK if available). */
    GEMINI("Google Gemini"),
    
    /** Fallback estimation (approx. 4 characters per token). */
    ESTIMATE("Estimate (Naive)");

    private final String displayName;

    TokenizerType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
