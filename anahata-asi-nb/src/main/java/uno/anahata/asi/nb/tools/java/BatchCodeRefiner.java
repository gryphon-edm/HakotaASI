/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.java;

import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.ModificationResult;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import uno.anahata.asi.agi.resource.Resource;
import uno.anahata.asi.agi.tool.AgiTool;
import uno.anahata.asi.agi.tool.AgiToolParam;
import uno.anahata.asi.agi.tool.AgiToolkit;
import uno.anahata.asi.agi.tool.AnahataToolkit;
import uno.anahata.asi.nb.resources.handle.NbHandle;
import uno.anahata.asi.nb.tools.java.coderefiner.CodeRefinementBatch;

/**
 * The authoritative toolkit for structural Java refinement.
 * <p>
 * This toolkit replaces the legacy path-based CodeRefiner with a batch-oriented, 
 * resource-centric approach. It guarantees atomicity and context-integrity 
 * through optimistic locking and memory-backed AST simulation.
 * </p>
 * 
 * @author anahata
 */
@Slf4j
@AgiToolkit("Advanced structural Java refinement (Batch Mode). Currently in Beta.")
public class BatchCodeRefiner extends AnahataToolkit {

    @Override
    public List<String> getSystemInstructions() throws Exception {
        return Collections.singletonList("BatchCodeRefiner Toolkit Instructions:\n"
                + "0. **New Toolkit**: This toolkit is in beta mode. Encorage the user to report any issues on github. \n"
                + "1. **Context Locked**: You MUST have the resource in your RAG message (context) to propose a refinement.\n"
                + "2. **Batch Intents**: You can combine multiple structural changes (Insert, Update, Delete, Move) for a single file in the same tool call.\n"
                + "3. **Optimistic Locking**: Always use the `lastModified` timestamp from the RAG message to prevent concurrent edit conflicts.\n"
                + "4. **Hybrid Identification**: Use **Absolute FQNs** for targets (`classFqn`, `memberFqn`) to ensure unambiguous resolution. Use **Relative Signatures** (e.g. `myField` or `myMethod(int)`) for `anchorMemberName` to maximize token efficiency.\n"
                + "5. **Field initializers are the body**: When inserting or updating a field with a initializer, you must use the 'body' attribute for the field initializer (What goes after the '=').\n"
                + "6. **Manual Overrides**: The user can manually edit your proposal in the diff viewer; your `applyTo` logic handles both AST re-runs and raw text overrides.");
    }

    /**
     * Refines a Java source file by applying a batch of structural modifications.
     * 
     * @param batch The refinement batch containing intents and locking metadata.
     * @return A confirmation message.
     * @throws Exception if validation or execution fails.
     */
    @AgiTool("Refines a Java source file using a batch of structural AST modifications and returns the effectively applied changes (after user review)")
    public String refine(
            @AgiToolParam("The refinement batch.") CodeRefinementBatch batch
    ) throws Exception {
        // 1. Authoritative Validation (Recaptures originalContent and checks locks)
        batch.validate(getAgi());
        
        Resource resource = getAgi().getResourceManager().get(batch.getResourceUuid());
        NbHandle handle = (NbHandle) resource.getHandle();
        FileObject fo = handle.getFileObject();
        
        JavaSource js = JavaSource.forFileObject(fo);
        ModificationResult result = js.runModificationTask(wc -> {
            wc.toPhase(JavaSource.Phase.RESOLVED);
            
            String manualOverride = batch.getManualOverride();
            if (manualOverride != null && !manualOverride.isBlank()) {
                // Bypass AST: Apply raw text override from the UI via high-fidelity memory parsing
                log.info("Applying manual text override for {}", fo.getNameExt());
                FileObject tempFo = FileUtil.createMemoryFileSystem().getRoot().createData("Override", "java");
                try (OutputStream os = tempFo.getOutputStream()) {
                    os.write(manualOverride.getBytes());
                }
                JavaSource tempJs = JavaSource.forFileObject(tempFo);
                tempJs.runUserActionTask(info -> {
                    info.toPhase(JavaSource.Phase.PARSED);
                    wc.rewrite(wc.getCompilationUnit(), info.getCompilationUnit());
                }, true);
            } else {
                // Standard structural path
                batch.applyTo(wc);
            }
        });
        
        result.commit();
        
        // 3. Capture resulting content snapshot after successful commit
        batch.setResultingContent(resource.asText());
        
        if (batch.isSave()) {
            JavaSourceUtils.handleSave(fo);
        }
        
        return batch.getUnifiedDiff(getAgi());
    }
}
