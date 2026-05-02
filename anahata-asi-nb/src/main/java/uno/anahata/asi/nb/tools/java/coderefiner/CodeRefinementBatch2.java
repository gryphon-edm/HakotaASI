/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.java.coderefiner;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.netbeans.api.java.source.*;
import com.sun.source.tree.*;
import org.openide.filesystems.FileObject;
import uno.anahata.asi.agi.Agi;
import uno.anahata.asi.agi.tool.AgiToolException;
import uno.anahata.asi.toolkit.resources.text.AbstractTextResourceWrite;

/**
 * Version 2.0 of the refinement batch, using the flattened {@link CodeRefinementIntent2}.
 * <p>
 * This class is designed to be indestructible for LLMs that struggle with polymorphic 
 * JSON schemas, providing a linear, stable list of structural intents.
 * </p>
 * 
 * @author anahata
 */
@Data
@Slf4j
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "A robust, agent-friendly batch of structural AST modifications for a single Java file.")
public class CodeRefinementBatch2 extends AbstractTextResourceWrite {

    @Schema(description = "The linear list of structural changes to apply. Uses a flattened DTO to ensure schema compatibility.", required = true)
    private List<CodeRefinementIntent2> intents = new ArrayList<>();

    @Schema(description = "Whether to optimize imports after applying all changes. Defaults to true.")
    private boolean optimize = true;

    @Schema(description = "Whether to save the file to disk after refinement. Defaults to true.")
    private boolean save = true;

    /** {@inheritDoc} */
    @Override
    protected String doCalculateResultingContent(Agi agi) throws Exception {
        if (originalContent == null) {
             captureOriginalContent(agi);
        }
        
        uno.anahata.asi.agi.resource.Resource res = agi.getResourceManager().get(resourceUuid);
        if (res == null) {
            throw new AgiToolException("Resource not found for uuid: " + resourceUuid);
        }
        
        if (!(res.getHandle() instanceof uno.anahata.asi.nb.resources.handle.NbHandle nbh)) {
            throw new AgiToolException("Resource handle is not an IDE-capable NbHandle.");
        }

        FileObject fo = nbh.getFileObject();
        log.info("[V2-FLATTENED] Replaying surgery on: {}", fo.getNameExt());

        JavaSource js = JavaSource.forFileObject(fo);
        ModificationResult mRes = js.runModificationTask(wc -> {
            wc.toPhase(JavaSource.Phase.RESOLVED);
            applyTo(wc);
        });

        return mRes.getResultingSource(fo);
    }

    /**
     * Authoritatively applies all intents in this batch to the provided working copy.
     */
    public void applyTo(WorkingCopy wc) throws Exception {
        log.info("[V2-STRATEGY] Starting application of {} intents.", intents.size());
        Map<Tree, List<Tree>> modifiedMembers = new LinkedHashMap<>();

        for (CodeRefinementIntent2 intent : intents) {
            intent.apply(wc, modifiedMembers, optimize);
        }

        TreeMaker make = wc.getTreeMaker();
        for (Map.Entry<Tree, List<Tree>> entry : modifiedMembers.entrySet()) {
            Tree parent = entry.getKey();
            List<Tree> members = entry.getValue();

            if (parent instanceof ClassTree ct) {
                wc.rewrite(ct, CodeRefinementBatch.rebuildClassTree(make, ct, members));
            } else if (parent instanceof CompilationUnitTree cut) {
                CompilationUnitTree updated = make.CompilationUnit(cut.getPackage(), cut.getImports(), (List<? extends Tree>) members, cut.getSourceFile());
                wc.rewrite(cut, updated);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void validate(Agi agi) throws Exception {
        validateStructuralState(agi);
        if (intents == null || intents.isEmpty()) {
            throw new AgiToolException("Refinement batch must contain at least one intent.");
        }
        validateIdenticalContent(agi);
    }
}
