package uno.anahata.asi.toolkit.java.classpath;

import java.io.File;
import java.io.InputStream;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.Scanner;

public abstract class AbstractJarHandler implements JarHandler {
    protected Manifest readManifest(File jarFile) {
        try (JarFile jf = new JarFile(jarFile)) {
            return jf.getManifest();
        } catch (Exception e) {
            return null;
        }
    }

    protected String readEntry(File jarFile, String path) {
        try (JarFile jf = new JarFile(jarFile)) {
            var entry = jf.getJarEntry(path);
            if (entry == null) return null;
            try (InputStream is = jf.getInputStream(entry);
                 Scanner s = new Scanner(is).useDelimiter("\\A")) {
                return s.hasNext() ? s.next() : "";
            }
        } catch (Exception e) {
            return null;
        }
    }

    protected String getBaseName(File jarFile) {
        String name = jarFile.getName();
        return name.endsWith(".jar") ? name.substring(0, name.length() - 4) : name;
    }
}
