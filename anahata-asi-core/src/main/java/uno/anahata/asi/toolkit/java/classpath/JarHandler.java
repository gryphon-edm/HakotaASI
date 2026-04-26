package uno.anahata.asi.toolkit.java.classpath;

import java.io.File;

public interface JarHandler {
    boolean canHandle(File jarFile);
    JarMetadata extractMetadata(File jarFile);
}
