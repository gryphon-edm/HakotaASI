/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */

/**
 * Provides the provider-agnostic LLM connectivity and generation interfaces.
 * <p>
 * This package defines the core adapter contracts for connecting external AI models 
 * to the Anahata ecosystem. It orchestrates provider-level lifecycles via 
 * {@link uno.anahata.asi.agi.provider.AbstractAiProvider} and manages active inference 
 * runs, payload synthesis, streaming observers, and API retry handling through 
 * {@link uno.anahata.asi.agi.provider.AbstractModel}.
 * </p>
 * 
 * @author anahata
 */
package uno.anahata.asi.agi.provider;
