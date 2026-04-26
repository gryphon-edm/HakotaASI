package uno.anahata.asi.toolkit.java.classpath;

import java.io.File;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class MavenJarHandler extends AbstractJarHandler {
    @Override
    public boolean canHandle(File jarFile) {
        try (JarFile jf = new JarFile(jarFile)) {
            return jf.stream().anyMatch(e -> e.getName().startsWith("META-INF/maven/") && e.getName().endsWith("pom.properties"));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public JarMetadata extractMetadata(File jarFile) {
        try (JarFile jf = new JarFile(jarFile)) {
            JarEntry propEntry = jf.stream()
                .filter(e -> e.getName().startsWith("META-INF/maven/") && e.getName().endsWith("pom.properties"))
                .findFirst().orElse(null);

            if (propEntry != null) {
                Properties p = new Properties();
                p.load(jf.getInputStream(propEntry));
                return JarMetadata.builder()
                    .id(p.getProperty("artifactId"))
                    .version(p.getProperty("version"))
                    .vendor(p.getProperty("groupId"))
                    .build();
            }
        } catch (Exception e) {}
        return null;
    }
}
