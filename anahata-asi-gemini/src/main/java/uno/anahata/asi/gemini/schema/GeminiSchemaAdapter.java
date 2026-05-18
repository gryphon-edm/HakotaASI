/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.gemini.schema;

import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import uno.anahata.asi.internal.JacksonUtils;
import uno.anahata.asi.agi.tool.schema.SchemaProvider;

/**
 * Adapts the core framework's rich JSON schema generation into the simpler,
 * native Google GenAI {@code Schema} objects required for function declarations.
 *
 * @author anahata-gemini-pro-2.5
 */
@Slf4j
public class GeminiSchemaAdapter {

    /**
     * Internal mapping of Java primitive and wrapper types to native 
     * Google GenAI schema types.
     */
    private static final Map<Class<?>, Type.Known> PRIMITIVE_MAP = new HashMap<>();
    /**
     * A reusable schema instance representing the {@code void} type.
     */
    private static final Schema VOID_SCHEMA = Schema.builder().build();

    static {
        PRIMITIVE_MAP.put(String.class, Type.Known.STRING);
        PRIMITIVE_MAP.put(Integer.class, Type.Known.INTEGER);
        PRIMITIVE_MAP.put(int.class, Type.Known.INTEGER);
        PRIMITIVE_MAP.put(Long.class, Type.Known.INTEGER);
        PRIMITIVE_MAP.put(long.class, Type.Known.INTEGER);
        PRIMITIVE_MAP.put(Float.class, Type.Known.NUMBER);
        PRIMITIVE_MAP.put(float.class, Type.Known.NUMBER);
        PRIMITIVE_MAP.put(Double.class, Type.Known.NUMBER);
        PRIMITIVE_MAP.put(double.class, Type.Known.NUMBER);
        PRIMITIVE_MAP.put(Boolean.class, Type.Known.BOOLEAN);
        PRIMITIVE_MAP.put(boolean.class, Type.Known.BOOLEAN);
    }

    /**
     * Resolves a Java {@link java.lang.reflect.Type} into a native Google 
     * GenAI {@link Schema} object.
     * @param type The Java type to resolve.
     * @return The corresponding Schema.
     * @throws Exception if schema generation fails.
     */
    public static Schema getGeminiSchema(java.lang.reflect.Type type) throws Exception {
        return getGeminiSchema(type, false);
    }
    
    /**
     * Resolves a Java {@link java.lang.reflect.Type} into a native Google 
     * GenAI {@link Schema} object, with optional inclusion of the JSON Schema ID.
     * @param type               The Java type to resolve.
     * @param includeJsonSchemaId Whether to include the {@code $id} field.
     * @return The corresponding Schema.
     * @throws Exception if schema generation fails.
     */
    public static Schema getGeminiSchema(java.lang.reflect.Type type, boolean includeJsonSchemaId) throws Exception {
        if (type == null || type.equals(void.class) || type.equals(Void.class)) {
            return VOID_SCHEMA;
        }

        String inlinedSchema = SchemaProvider.generateInlinedSchemaString(type);
        return getGeminiSchema(inlinedSchema);
    }
    
    /**
     * Converts a raw JSON Schema string into a native Google GenAI {@link Schema}.
     * <p>Implementation details: Parses the JSON string into a map and performs 
     * a recursive walk to build the nested Google Schema hierarchy, including 
     * support for {@code oneOf} and {@code anyOf} polymorphic structures.</p>
     * @param jsonSchema The raw JSON Schema string.
     * @return The corresponding Schema, or null if empty/void.
     */
    public static Schema getGeminiSchema(String jsonSchema) {
        if (jsonSchema == null || jsonSchema.trim().isEmpty() || jsonSchema.trim().equals("{}")) {
            return null; // Return null for void/empty schemas
        }
        Map<String, Object> schemaMap = JacksonUtils.parse(jsonSchema, Map.class);
        return buildSchemaFromMap(schemaMap);
    }
    
    /**
     * Resolves a Java {@link Class} into a native Google GenAI {@link Schema}.
     * @param clazz The class to resolve.
     * @return The corresponding Schema.
     * @throws Exception if schema generation fails.
     */
    public static Schema getGeminiSchema(Class<?> clazz) throws Exception {
        return getGeminiSchema((java.lang.reflect.Type) clazz, false);
    }

    /**
     * Recursively constructs a native Google GenAI {@link Schema} from a 
     * standard JSON Schema map.
     * @param map The source schema map.
     * @return The finalized native Schema.
     */
    private static Schema buildSchemaFromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;

        Schema.Builder builder = Schema.builder();

        String fqn = (String) map.get("title");
        if (fqn == null) {
            fqn = "N/A";
        }
        
        builder.title(fqn);

        if (map.containsKey("description")) {
            builder.description((String) map.get("description"));
        }

        if (map.containsKey("type")) {
            String typeStr = (String) map.get("type");
            switch (typeStr.toUpperCase()) {
                case "STRING":  builder.type(Type.Known.STRING);  break;
                case "NUMBER":  builder.type(Type.Known.NUMBER);  break;
                case "INTEGER": builder.type(Type.Known.INTEGER); break;
                case "BOOLEAN": builder.type(Type.Known.BOOLEAN); break;
                case "ARRAY":   builder.type(Type.Known.ARRAY);   break;
                case "OBJECT":  builder.type(Type.Known.OBJECT);  break;
            }
        }

        if (map.containsKey("enum")) {
            List<?> rawList = (List<?>) map.get("enum");
            List<String> enumValues = rawList.stream().map(Object::toString).collect(Collectors.toList());
            builder.enum_(enumValues);
        }

        if (map.containsKey("properties")) {
            Map<String, Map<String, Object>> propertiesMap = (Map<String, Map<String, Object>>) map.get("properties");
            Map<String, Schema> schemaProperties = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Object>> entry : propertiesMap.entrySet()) {
                schemaProperties.put(entry.getKey(), buildSchemaFromMap(entry.getValue()));
            }
            builder.properties(schemaProperties);
        }

        if (map.containsKey("required")) {
            builder.required((List<String>) map.get("required"));
        }

        if (map.containsKey("items")) {
            Schema itemsSchema = buildSchemaFromMap((Map<String, Object>) map.get("items"));
            builder.items(itemsSchema);
        }

        if (map.containsKey("oneOf") || map.containsKey("anyOf")) {
            List<Map<String, Object>> branches = (List<Map<String, Object>>) (map.containsKey("oneOf") ? map.get("oneOf") : map.get("anyOf"));
            List<Schema> anyOf = branches.stream()
                    .map(GeminiSchemaAdapter::buildSchemaFromMap)
                    .collect(Collectors.toList());
            builder.anyOf(anyOf);
        }

        return builder.build();
    }

}
