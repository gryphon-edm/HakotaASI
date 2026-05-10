/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.java.coderefiner;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.netbeans.api.java.source.*;
import org.netbeans.api.java.source.ClasspathInfo;
import com.sun.source.tree.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.OutputStream;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import uno.anahata.asi.agi.Agi;
import uno.anahata.asi.agi.tool.AgiToolException;
import uno.anahata.asi.agi.tool.spi.java.JavaMethodToolResponse;
import uno.anahata.asi.nb.tools.java.BatchCodeRefiner;
import uno.anahata.asi.nb.tools.java.JavaSourceUtils;
import uno.anahata.asi.toolkit.resources.text.AbstractTextResourceWrite;
import uno.anahata.asi.toolkit.resources.text.LineComment;
import java.util.Collections;

/**
 * Version 2.0 of the refinement batch, using the flattened
 * {@link CodeRefinementIntent}.
 * <p>
 * This class is designed to be indestructible for LLMs that struggle with
 * polymorphic JSON schemas, providing a linear, stable list of structural
 * intents.
 * </p>
 *
 * @author anahata
 */
@Data
@Slf4j
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "A robust, agent-friendly batch of structural AST modifications for a single Java file.")
public class CodeRefinementBatch extends AbstractTextResourceWrite {

    @Schema(description = "The linear list of structural changes to apply.", required = true)
    private List<CodeRefinementIntent> intents = new ArrayList<>();

    @Schema(description = "Whether to optimize imports after applying all changes. Defaults to true.")
    private boolean optimize = true;

    @Schema(description = "Whether to save the file to disk after refinement. Defaults to true.")
    private boolean save = true;

    /**
     * Pre-calculated line comments for UI annotations, mapped during content
     * calculation.
     */
    @JsonIgnore
    @Schema(hidden = true)
    private List<LineComment> calculatedComments = new ArrayList<>();

    /**
     * {@inheritDoc}
     */
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

        FileObject originalFo = nbh.getFileObject();
        log.info("[V2-SINGULARITY] Replaying multi-stage simulation on: {}", originalFo.getNameExt());

        // 1. Capture original ClasspathInfo (the "DNA" of the project)
        ClasspathInfo cpInfo = ClasspathInfo.create(originalFo);

        // 2. Create a "Simulation File" in memory with the current production content
        FileObject simFo = FileUtil.createMemoryFileSystem().getRoot().createData("Simulation", "java");
        try (OutputStream os = simFo.getOutputStream()) {
            os.write(originalFo.asBytes());
        }

        // 3. Reconnaissance Pass: Resolve coordinates on the REAL file first.
        // This captures the "Type DNA" and stable indices while the project CP is active.
        JavaSource originalJs = JavaSource.forFileObject(originalFo);
        final Exception[] resolveError = new Exception[1];
        originalJs.runUserActionTask(wc -> {
            try {
                wc.toPhase(JavaSource.Phase.RESOLVED);
                for (CodeRefinementIntent intent : intents) {
                    intent.resolve(wc);
                }
            } catch (Exception e) {
                resolveError[0] = e;
            }
        }, true);
        if (resolveError[0] != null) {
            throw resolveError[0];
        }

        // 4. Create Classpath-Aware JavaSource for simulation
        JavaSource simJs = JavaSource.create(cpInfo, simFo);

        // --- STAGE 1: Clearance Pass ---
        simJs.runModificationTask(wc -> {
            wc.toPhase(JavaSource.Phase.RESOLVED);
            applyTo(wc, true, cpInfo);
        }).commit();

        // --- STAGE 2: Reconstruction Pass ---
        final List<LineComment> mapping = new ArrayList<>();
        ModificationResult mRes = simJs.runModificationTask(wc -> {
            wc.toPhase(JavaSource.Phase.RESOLVED);
            applyTo(wc, false, cpInfo);

            // --- Coordinate Reconnaissance Pass ---
            for (CodeRefinementIntent intent : intents) {
                String resultingFqn = intent.getResultingMemberFqn();
                if (resultingFqn != null && intent.getReason() != null && !intent.getReason().isBlank()) {
                    Tree found = JavaSourceUtils.findTree(wc, resultingFqn);
                    if (found != null) {
                        long pos = wc.getTrees().getSourcePositions().getStartPosition(wc.getCompilationUnit(), found);
                        long line = wc.getCompilationUnit().getLineMap().getLineNumber(pos);
                        mapping.add(new LineComment((int) line, intent.getReason()));
                    }
                }
            }
        });

        this.calculatedComments = mapping;
        String ret = mRes.getResultingSource(simFo);
        return ret;
    }

    /**
     * Authoritatively applies the batch to the provided working copy.
     *
     * @param wc The working copy.
     * @param clearance If true, only deletes targets. If false, only
     * inserts/updates replacements.
     */
    public void applyTo(WorkingCopy wc, boolean clearance, ClasspathInfo cpInfo) throws Exception {
        log.info("[V2-STRATEGY] Starting application of {} intents (Clearance={}).", intents.size(), clearance);
        Map<Tree, List<Tree>> modifiedMembers = new LinkedHashMap<>();

        // Sort intents to prevent index-shift corruption during simulation passes.
        List<CodeRefinementIntent> sortedIntents = new ArrayList<>(intents);
        if (clearance) {
            // Sort by resolved index descending: remove from bottom-to-top so indices remain stable.
            sortedIntents.sort((a, b) -> Integer.compare(b.getResolvedIndex(), a.getResolvedIndex()));
        }

        for (CodeRefinementIntent intent : sortedIntents) {
            intent.apply(wc, modifiedMembers, optimize, clearance, cpInfo);
        }

        TreeMaker make = wc.getTreeMaker();
        for (Map.Entry<Tree, List<Tree>> entry : modifiedMembers.entrySet()) {
            Tree parent = entry.getKey();
            List<Tree> members = entry.getValue();

            if (parent instanceof ClassTree ct) {
                wc.rewrite(ct, BatchCodeRefiner.rebuildClassTree(make, ct, members));
            } else if (parent instanceof CompilationUnitTree cut) {
                CompilationUnitTree updated = make.CompilationUnit(cut.getPackage(), cut.getImports(), (List<? extends Tree>) members, cut.getSourceFile());
                wc.rewrite(cut, updated);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validate(Agi agi) throws Exception {
        super.validate(agi);
        if (resourceUuid == null) {
            throw new AgiToolException("Resource uuid not provided");
        }
        captureOriginalContent(agi);

        if (intents == null || intents.isEmpty()) {
            throw new AgiToolException("Refinement batch must contain at least one intent.");
        }

        // Manual implementation of identity validation
        if (java.util.Objects.equals(originalContent, calculateResultingContent(agi))) {
            throw new AgiToolException("Update rejected: The resulting content is identical to the current file content on disk.");
        }
    }
}
