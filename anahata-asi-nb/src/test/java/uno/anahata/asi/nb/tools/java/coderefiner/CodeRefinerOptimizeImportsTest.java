/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.java.coderefiner;

import uno.anahata.asi.nb.tools.java.CodeRefiner;
import uno.anahata.asi.nb.resources.handle.NbHandle;
import uno.anahata.asi.agi.Agi;
import uno.anahata.asi.agi.resource.Resource;
import uno.anahata.asi.agi.tool.spi.java.JavaMethodToolResponse;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Robust test suite for CodeRefiner.optimizeImports corner cases.
 * Handles: Lombok positions, implicit sub-packages, namespace clashes, and planning clashes.
 *
 * @author anahata
 */
public class CodeRefinerOptimizeImportsTest {

    private static void logToToolContext(String s) {
        JavaMethodToolResponse.getCurrent().addLog(s);
    }

    private static void errorToToolContext(String s) {
        JavaMethodToolResponse.getCurrent().addError(s);
    }

    public static void runAllTests(Agi agi) throws Exception {
        logToToolContext("==================================================");
        logToToolContext("STARTING CODE REFINER OPTIMIZE IMPORTS TEST SUITE");
        logToToolContext("==================================================");

        // We will write our test subject dynamically to a temporary test file in the project
        String testSubjectPath = "/home/pablo/NetBeansProjects/anahata-asi-parent/anahata-asi-nb/src/test/java/uno/anahata/asi/nb/tools/java/coderefiner/CodeRefinerOptimizeTestSubject.java";
        File testFile = new File(testSubjectPath);
        FileObject fo = FileUtil.toFileObject(testFile);
        if (fo == null) {
            // Create if it doesn't exist
            testFile.getParentFile().mkdirs();
            testFile.createNewFile();
            fo = FileUtil.toFileObject(testFile);
        }

        final FileObject targetFo = fo;
        CodeRefiner refiner = new CodeRefiner();

        // Helper to write content
        java.util.function.Consumer<String> writeContent = (content) -> {
            try {
                try (OutputStream os = targetFo.getOutputStream()) {
                    os.write(content.getBytes(StandardCharsets.UTF_8));
                }
                targetFo.refresh();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        // Helper to run optimization
        java.util.function.Supplier<String> optimizeAndRead = () -> {
            try {
                refiner.optimizeImports(testSubjectPath, true, true);
                targetFo.refresh();
                return targetFo.asText("UTF-8");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        // =====================================================================
        // TEST 1: Lombok Position Guard (SneakyThrows synthetic nodes)
        // =====================================================================
        logToToolContext("Running Test 1: Lombok Position Guard & Synthetic Nodes...");
        String t1Input = "package uno.anahata.asi.nb.tools.java.coderefiner;\n" +
                "import lombok.SneakyThrows;\n" +
                "public class CodeRefinerOptimizeTestSubject {\n" +
                "    @SneakyThrows\n" +
                "    public void riskyMethod() {\n" +
                "        System.out.println(\"A\");\n" +
                "        System.out.println(\"B\");\n" +
                "    }\n" +
                "    public void otherMethod(java.util.List<String> list) {}\n" +
                "}\n";
        writeContent.accept(t1Input);
        String t1Output = optimizeAndRead.get();

        boolean t1Uncorrupted = t1Output.contains("public void riskyMethod()") && 
                t1Output.contains("System.out.println(\"A\");") && 
                t1Output.contains("System.out.println(\"B\");") &&
                !t1Output.contains("Throwablepackage");
        boolean t1Shortened = t1Output.contains("import java.util.List;") && t1Output.contains("otherMethod(List<String> list)");

        if (!t1Uncorrupted) {
            errorToToolContext("FAIL: Test 1 - Lombok SneakyThrows corrupted the method body!");
        } else if (!t1Shortened) {
            errorToToolContext("FAIL: Test 1 - List was not successfully imported and shortened!");
        } else {
            logToToolContext("SUCCESS: Test 1 - Lombok position guard worked flawlessly!");
        }

        // =====================================================================
        // TEST 2: Implicit Import Sub-Package Trap (java.lang vs java.lang.reflect)
        // =====================================================================
        logToToolContext("Running Test 2: Implicit Import Sub-Package Trap...");
        String t2Input = "package uno.anahata.asi.nb.tools.java.coderefiner;\n" +
                "public class CodeRefinerOptimizeTestSubject {\n" +
                "    public void reflectMethod(java.lang.reflect.Parameter param) {}\n" +
                "    public void standardMethod(java.lang.String str) {}\n" +
                "}\n";
        writeContent.accept(t2Input);
        String t2Output = optimizeAndRead.get();

        boolean t2ParameterImported = t2Output.contains("import java.lang.reflect.Parameter;");
        boolean t2ParameterShortened = t2Output.contains("reflectMethod(Parameter param)");
        boolean t2StringNoImport = !t2Output.contains("import java.lang.String;");
        boolean t2StringShortened = t2Output.contains("standardMethod(String str)");

        if (!t2ParameterImported || !t2ParameterShortened) {
            errorToToolContext("FAIL: Test 2 - java.lang.reflect.Parameter was not imported or shortened!");
        } else if (!t2StringNoImport || !t2StringShortened) {
            errorToToolContext("FAIL: Test 2 - java.lang.String import was incorrectly added or not shortened!");
        } else {
            logToToolContext("SUCCESS: Test 2 - Implicit import sub-package trap handled correctly!");
        }

        // =====================================================================
        // TEST 3: Namespace Clash Guard (Taken Names)
        // =====================================================================
        logToToolContext("Running Test 3: Namespace Clash Guard...");
        String t3Input = "package uno.anahata.asi.nb.tools.java.coderefiner;\n" +
                "import com.google.genai.types.FinishReason;\n" +
                "public class CodeRefinerOptimizeTestSubject {\n" +
                "    private FinishReason googleReason;\n" +
                "    private uno.anahata.asi.agi.provider.FinishReason anahataReason;\n" +
                "}\n";
        writeContent.accept(t3Input);
        String t3Output = optimizeAndRead.get();

        boolean t3AnahataReasonFqnPreserved = t3Output.contains("private uno.anahata.asi.agi.provider.FinishReason anahataReason;");
        boolean t3NoDuplicateImport = !t3Output.contains("import uno.anahata.asi.agi.provider.FinishReason;");

        if (!t3AnahataReasonFqnPreserved) {
            errorToToolContext("FAIL: Test 3 - Clashing FQN was incorrectly shortened!");
        } else if (!t3NoDuplicateImport) {
            errorToToolContext("FAIL: Test 3 - Duplicate single-type import for clashing class was incorrectly added!");
        } else {
            logToToolContext("SUCCESS: Test 3 - Namespace clash guard successfully preserved FQNs!");
        }

        // =====================================================================
        // TEST 4: Duplicate Import Planning Clash (AudioDevice)
        // =====================================================================
        logToToolContext("Running Test 4: Duplicate Import Planning Clash...");
        String t4Input = "package uno.anahata.asi.nb.tools.java.coderefiner;\n" +
                "public class CodeRefinerOptimizeTestSubject {\n" +
                "    private uno.anahata.asi.toolkit.audio.AudioDevice hardwareDevice;\n" +
                "    private javazoom.jl.player.AudioDevice jlayerDevice;\n" +
                "}\n";
        writeContent.accept(t4Input);
        String t4Output = optimizeAndRead.get();

        boolean t4ImportedOnlyOne = (t4Output.contains("import uno.anahata.asi.toolkit.audio.AudioDevice;") && !t4Output.contains("import javazoom.jl.player.AudioDevice;")) ||
                (t4Output.contains("import javazoom.jl.player.AudioDevice;") && !t4Output.contains("import uno.anahata.asi.toolkit.audio.AudioDevice;"));
        
        boolean t4CorrectRepresentation = (t4Output.contains("private AudioDevice hardwareDevice;") && t4Output.contains("private javazoom.jl.player.AudioDevice jlayerDevice;")) ||
                (t4Output.contains("private uno.anahata.asi.toolkit.audio.AudioDevice hardwareDevice;") && t4Output.contains("private AudioDevice jlayerDevice;"));

        if (!t4ImportedOnlyOne) {
            errorToToolContext("FAIL: Test 4 - Duplicate single-type imports were planned and written!");
        } else if (!t4CorrectRepresentation) {
            errorToToolContext("FAIL: Test 4 - Both FQNs were incorrectly modified or left unaligned!");
        } else {
            logToToolContext("SUCCESS: Test 4 - Duplicate import planning clash successfully guarded!");
        }

        logToToolContext("==================================================");
        logToToolContext("ALL CODE REFINER OPTIMIZE IMPORTS TESTS COMPLETED");
        logToToolContext("==================================================");

        // Clean up the temporary test file
        testFile.delete();
    }
}
