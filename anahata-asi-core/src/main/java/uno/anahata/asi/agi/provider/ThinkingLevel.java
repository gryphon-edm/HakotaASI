/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.agi.provider;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Standardized, model-agnostic enum representing the level of "thinking" or 
 * reasoning tokens that the model should generate.
 * 
 * @author anahata-ai
 */
@Getter
@RequiredArgsConstructor
public enum ThinkingLevel {
    /** Unspecified thinking level (let the model decide). */
    THINKING_LEVEL_UNSPECIFIED("Unspecified"),
    /** No thinking effort (lowest latency). */
    NONE("None"),
    /** Minimal thinking effort. */
    MINIMAL("Minimal"),
    /** Low thinking effort. */
    LOW("Low"),
    /** Medium thinking effort. */
    MEDIUM("Medium"),
    /** High thinking effort. */
    HIGH("High"),
    /** Extra high thinking effort (highest intelligence, highest latency). */
    XHIGH("X-High");

    /** The human-readable display value. */
    private final String displayValue;
}
