/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.gemini.schema;

import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@link GeminiSchemaAdapter}, verifying the structural
 * translation of Java classes into Google GenAI native schema objects.
 * <p>
 * This suite ensures that both primitive types and complex reflected POJOs
 * are correctly mapped to their corresponding schema representations.
 * </p>
 * @author anahata-ai
 */
public class GeminiSchemaAdapterTest {

    /**
     * Verifies that primitive Java types (like {@code String}) are correctly
     * mapped to the native Google {@code STRING} type with proper titles.
     * @throws Exception If schema generation fails.
     */
    @Test
    public void testSimpleType() throws Exception {
        Schema schema = GeminiSchemaAdapter.getGeminiSchema(String.class);
        assertNotNull(schema);
        assertEquals(Type.Known.STRING, schema.type().get().knownEnum());
        assertEquals("java.lang.String", schema.title().get());
    }

    /**
     * Validates the reflection-based extraction of properties from a complex
     * POJO into a nested Google {@code OBJECT} schema.
     * @throws Exception If schema generation fails.
     */
    @Test
    public void testComplexType() throws Exception {
        Schema schema = GeminiSchemaAdapter.getGeminiSchema(TestObject.class);
        assertNotNull(schema);
        assertEquals(Type.Known.OBJECT, schema.type().get().knownEnum());
        assertEquals("uno.anahata.asi.gemini.schema.GeminiSchemaAdapterTest.TestObject", schema.title().get());
        
        assertTrue(schema.properties().isPresent());
        assertTrue(schema.properties().get().containsKey("name"));
        assertEquals(Type.Known.STRING, schema.properties().get().get("name").type().get().knownEnum());
        
        assertTrue(schema.properties().get().containsKey("age"));
        assertEquals(Type.Known.INTEGER, schema.properties().get().get("age").type().get().knownEnum());
    }

    /**
     * A simple DTO used as a reflection target for complex schema
     * verification.
     */
    public static class TestObject {
        /**
         * The name of the test object.
         */
        private String name;
        /**
         * The age of the test object.
         */
        private int age;
    }
}
