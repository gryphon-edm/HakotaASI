package uno.anahata.asi.toolkit.java.classpath;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;

/**
 * Represents the extracted metadata of a JAR file.
 * <p>
 * This DTO is used by the {@link uno.anahata.asi.toolkit.java.classpath.VeryPrettyClassPathPrinter} 
 * to represent libraries in a token-efficient manner.
 * </p>
 * 
 * @author anahata
 */
@Data
@Builder
public class JarMetadata {
    
    /**
     * The logical ID of the JAR (usually the artifactId).
     */
    private String id;
    
    /**
     * The version of the library, recovered from the filename, Manifest, or Maven properties.
     */
    private String version;
    
    /**
     * The vendor or provider of the library (e.g., "Apache NetBeans", "Google").
     */
    private String vendor;
    
    /**
     * Additional properties discovered during inspection.
     */
    @Builder.Default
    private Map<String, String> properties = new TreeMap<>();
  
    /**
     * Sanitizes a version string by removing build metadata, timestamps, and trailing spaces.
     * <p>
     * For example: "3.6.2 1423725 - rmuir - 2012-12-18" becomes "3.6.2".
     * </p>
     * 
     * @param version The raw version string.
     * @return The sanitized version string.
     */
    public static String sanitizeVersion(String version) {
        if (version == null) return null;
        int spaceIdx = version.indexOf(' ');
        if (spaceIdx != -1) {
            version = version.substring(0, spaceIdx);
        }
        return version.trim();
    }

    /**
     * Determines if this metadata is "better" than another (more informative).
     * 
     * @param other The other metadata to compare with.
     * @return true if this is more informative.
     */
    public boolean isBetterThan(JarMetadata other) {
        if (other == null) return true;
        if (this.version != null && other.version == null) return true;
        if (this.vendor != null && other.vendor == null) return true;
        return false;
    }

    /**
     * Converts the metadata into a TOON (Token Optimized Object Notation) string.
     * 
     * @return A compact string representation.
     */
    public String toToon() {
        StringBuilder sb = new StringBuilder();
        sb.append(id);
        if (version != null) {
            sb.append(" v").append(version);
        }
        if (!properties.isEmpty()) {
            sb.append(": ").append(properties.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ")));
        }
        return sb.toString();
    }
}
