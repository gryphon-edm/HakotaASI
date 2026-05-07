/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.java.coderefiner;

import com.sun.source.tree.*;
import com.sun.source.util.TreePath;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.netbeans.api.java.source.GeneratorUtilities;
import org.netbeans.api.java.source.TreeMaker;
import org.netbeans.api.java.source.WorkingCopy;
import uno.anahata.asi.agi.tool.AgiToolException;
import uno.anahata.asi.agi.tool.ToolContext;
import uno.anahata.asi.agi.tool.spi.java.JavaMethodToolResponse;
import uno.anahata.asi.nb.tools.java.BatchCodeRefiner;

/**
 * A robust, flattened version of structural Java refinement intents.
 * <p>
 * This "Tagged Union" DTO replaces the polymorphic hierarchy to maximize
 * compatibility with LLMs that struggle with 'oneOf' schemas.
 * </p>
 *
 * @author anahata
 */
@Data
@Slf4j
@NoArgsConstructor
@Schema(description = "Represents a single structural AST modification instruction in a flattened format.")
public class CodeRefinementIntent implements Serializable {

    /**
     * The type of structural operation to perform.
     */
    public enum Type {
        /**
         * Inserts a new member.
         */
        @Schema(description = "Inserts a new member (method, field, or inner type).")
        INSERT,
        /**
         * Updates an existing member's signature or body.
         */
        @Schema(description = "Updates an existing member's signature or body.")
        UPDATE,
        /**
         * Deletes an existing member.
         */
        @Schema(description = "Deletes an existing member.")
        DELETE,
        /**
         * Moves an existing member to a new position.
         */
        @Schema(description = "Moves an existing member to a new position within its class.")
        MOVE
    }

    @Schema(description = "The operation type.", required = true)
    private Type type;

    @Schema(description = "The FQN of the target class (e.g. 'com.foo.Bar'). Mandatory for 'INSERT' inside a class. Use '$' for nested types. Leave empty for file-level.")
    private String classFqn;

    @Schema(description = "The ABSOLUTE FQN of the member to operation on (e.g. 'com.foo.Bar.myMethod(java.util.List)'). FQNs are preferred for parameters. Generic brackets '<...>' are not required and will be ignored during matching.")
    private String memberFqn;

    @Schema(description = "The member signature or header (everything to the LEFT of the first '{' or '=')' without javadoc. (e.g. '@Override public void setItems(List<String> items)'). Mandatory for 'INSERT', optional for 'UPDATE' (only if you want to change the declaration). Do not provide Javadocs here. Will cause the tool to fail or corrupt the java source file.")
    private String declaration;

    @Schema(description = "For methods, The WHOLE body code, the logic inside the braces. For fields, the initializer expression (part after '=') or can be blank if there is no initializer expression. FOr use with 'INSERT' and 'UPDATE'. **Do not include the method signature or field declaration in this 'body' field. i.e. ths field cannot start with annotations like @Override or modifiers like 'public void '**")
    private String body;

    @Schema(description = "Position relative to the anchor member. **Mandatory for 'INSERT' and 'MOVE'**.")
    private RelativePosition position;

    @Schema(description = "Anchor member name relative to class (e.g. 'myMethod()'). Mandatory for BEFORE/AFTER positions in 'INSERT' and 'MOVE'.")
    private String anchorMemberName;

    @Schema(description = "The reason for this structural change. Will be displayed in the UI.")
    private String reason;

    @JsonIgnore
    private int calculatedIndex = -1;

    /** Internal index of the target member within its parent container. */
    @JsonIgnore
    private transient int resolvedIndex = -1;

    /** 
     * Internal coordinates for the parent container. 
     * If null, parent is the CompilationUnit. Otherwise, it is the FQN of the parent class.
     */
    @JsonIgnore
    private transient String inferredParentFqn;

    @JsonIgnore
    private transient String extractedDeclaration;

    @JsonIgnore
    private transient String extractedBody;

    @JsonIgnore
    private transient com.sun.source.tree.Tree savedTreeForMove;

    /**
     * Resolves the target member and container coordinates on the REAL project file.
     * <p>
     * This is the Reconnaissance Pass of the Singularity Fix. It ensures that
     * all resolution happens in a context where the project classpath is fully
     * active. The resulting indices are then used in the simulation passes.
     * </p>
     * 
     * @param info The compilation info of the ORIGINAL project file.
     */
    public void resolve(org.netbeans.api.java.source.CompilationInfo info) throws Exception {
        if (type == null) {
            return;
        }

        Tree member = null;
        if (type != Type.INSERT) {
            member = BatchCodeRefiner.findMemberInWorkingCopy(info, memberFqn);
            if (member == null) {
                BatchCodeRefiner.throwMemberNotFound(info, memberFqn);
            }
        }

        // 1. Resolve Parent Container
        if (classFqn != null && !classFqn.isBlank()) {
            Tree parent = BatchCodeRefiner.findMemberInWorkingCopy(info, classFqn);
            if (parent == null) {
                throw new AgiToolException("Target class not found: " + classFqn);
            }
            inferredParentFqn = classFqn;
        } else if (member != null) {
            // Parent Inference: Determine the container from the member's path
            TreePath path = TreePath.getPath(info.getCompilationUnit(), member);
            Tree parent = path.getParentPath().getLeaf();
            if (parent instanceof CompilationUnitTree) {
                inferredParentFqn = null;
            } else {
                javax.lang.model.element.Element parentElement = info.getTrees().getElement(path.getParentPath());
                inferredParentFqn = uno.anahata.asi.nb.tools.java.JavaSourceUtils.getCanonicalFqn(parentElement);
            }
        } else {
            inferredParentFqn = null; // Default to root CU for inserts without classFqn
        }

        // 2. Resolve Member Coordinates (for UPDATE, DELETE, MOVE)
        if (member != null) {
            List<? extends Tree> members = getContainerMembersByResolvedIndex(info);
            resolvedIndex = BatchCodeRefiner.findMemberIndex(info, members, member);
            if (resolvedIndex == -1) {
                throw new AgiToolException("Logic Error: Member found in CU but not in its inferred container: " + memberFqn);
            }
        }
    }

    private List<Tree> getContainerMembersByResolvedIndex(org.netbeans.api.java.source.CompilationInfo info) {
        Tree container = (inferredParentFqn == null) ? info.getCompilationUnit() : BatchCodeRefiner.findMemberInWorkingCopy(info, inferredParentFqn);
        if (container instanceof ClassTree ct) {
            return new ArrayList<>(ct.getMembers());
        } else {
            return new ArrayList<>(info.getCompilationUnit().getTypeDecls());
        }
    }

    /**
     * Authoritatively applies this intent to the provided working copy.
     * <p>
     * Implementation Logic Parity: This method merges the logic from all legacy
     * concrete intent classes into a single, switch-based dispatcher to ensure
     * 100% functional consistency.
     * </p>
     */
    public void apply(WorkingCopy wc, Map<Tree, List<Tree>> modifiedMembers, boolean optimize, boolean clearance, org.netbeans.api.java.source.ClasspathInfo cpInfo) throws Exception {
        if (type == null) {
            throw new AgiToolException("Intent type is mandatory.");
        }

        switch (type) {
            case INSERT ->
                applyInsert(wc, modifiedMembers, optimize, clearance, cpInfo);
            case UPDATE ->
                applyUpdate(wc, modifiedMembers, optimize, clearance, cpInfo);
            case DELETE ->
                applyDelete(wc, modifiedMembers, optimize, clearance);
            case MOVE ->
                applyMove(wc, modifiedMembers, optimize, clearance);
        }
    }

    private void applyInsert(WorkingCopy wc, Map<Tree, List<Tree>> modifiedMembers, boolean optimize, boolean clearance, org.netbeans.api.java.source.ClasspathInfo cpInfo) throws Exception {
        List<Tree> members = getContainerMembersByResolvedIndex(wc, modifiedMembers);

        if (clearance) {
            // Use the anchor name to calculate the relative index in the simulation.
            calculatedIndex = BatchCodeRefiner.getInsertIndex(wc, members, position, anchorMemberName);
        } else {
            GeneratorUtilities gu = GeneratorUtilities.get(wc);
            Tree newMember = BatchCodeRefiner.parseMember(wc, declaration, body, cpInfo);
            if (optimize) {
                newMember = gu.importFQNs(newMember);
            }
members.add(calculatedIndex != -1 ? Math.min(calculatedIndex, members.size()) : members.size(), newMember);
        }
    }

    private List<Tree> getContainerMembersByResolvedIndex(WorkingCopy wc, Map<Tree, List<Tree>> modifiedMembers) {
        Tree container = (inferredParentFqn == null) ? wc.getCompilationUnit() : BatchCodeRefiner.findMemberInWorkingCopy(wc, inferredParentFqn);
        return getContainerMembers(container, modifiedMembers);
    }

    private void applyUpdate(WorkingCopy wc, Map<Tree, List<Tree>> modifiedMembers, boolean optimize, boolean clearance, org.netbeans.api.java.source.ClasspathInfo cpInfo) throws Exception {
        List<com.sun.source.tree.Tree> members = getContainerMembersByResolvedIndex(wc, modifiedMembers);

        if (clearance) {
            com.sun.source.tree.Tree oldTreeInSim = BatchCodeRefiner.findMemberInWorkingCopy(wc, memberFqn);
            if (oldTreeInSim == null) {
                BatchCodeRefiner.throwMemberNotFound(wc, memberFqn);
            }
            if (declaration == null) {
                extractedDeclaration = getExtractedDeclaration(wc, oldTreeInSim);
            }
            if (body == null) {
                extractedBody = getExtractedBody(wc, oldTreeInSim);
            }
            int idx = BatchCodeRefiner.findMemberIndex(wc, members, oldTreeInSim);
            if (idx != -1) {
                calculatedIndex = idx;
                members.remove(idx);
            }
        } else {
            if (declaration != null || body != null) {
                com.sun.source.tree.Tree newTree = BatchCodeRefiner.parseMember(wc,
                        declaration != null ? declaration : extractedDeclaration,
                        body != null ? body : extractedBody, cpInfo);

                if (optimize) {
                    newTree = org.netbeans.api.java.source.GeneratorUtilities.get(wc).importFQNs(newTree);
                }
                members.add(Math.min(calculatedIndex, members.size()), newTree);
            }
        }
    }

    private String getExtractedDeclaration(WorkingCopy wc, Tree tree) {
        com.sun.source.util.SourcePositions sp = wc.getTrees().getSourcePositions();
        CompilationUnitTree cut = wc.getCompilationUnit();
        long start = sp.getStartPosition(cut, tree);
        long bodyStart = -1;
        if (tree instanceof MethodTree mt) {
            bodyStart = sp.getStartPosition(cut, mt.getBody());
        } else if (tree instanceof VariableTree vt && vt.getInitializer() != null) {
            bodyStart = sp.getStartPosition(cut, vt.getInitializer());
        }
        if (bodyStart != -1) {
            String decl = wc.getText().substring((int) start, (int) bodyStart).trim();
            return (tree instanceof VariableTree && decl.endsWith("=")) ? decl.substring(0, decl.length() - 1).trim() : decl;
        }
        String decl = wc.getText().substring((int) start, (int) sp.getEndPosition(cut, tree)).trim();
        return decl.endsWith(";") ? decl.substring(0, decl.length() - 1).trim() : decl;
    }

    private String getExtractedBody(WorkingCopy wc, Tree tree) {
        com.sun.source.util.SourcePositions sp = wc.getTrees().getSourcePositions();
        CompilationUnitTree cut = wc.getCompilationUnit();
        if (tree instanceof MethodTree mt) {
            BlockTree block = mt.getBody();
            if (block == null) return null;
            int start = (int) sp.getStartPosition(cut, block);
            int end = (int) sp.getEndPosition(cut, block);
            String text = wc.getText().substring(start, end).trim();
            if (text.startsWith("{") && text.endsWith("}")) {
                return text.substring(1, text.length() - 1).trim();
            }
            return text;
        }
        if (tree instanceof VariableTree vt && vt.getInitializer() != null) {
            int start = (int) sp.getStartPosition(cut, vt.getInitializer());
            int end = (int) sp.getEndPosition(cut, vt.getInitializer());
            return wc.getText().substring(start, end).trim();
        }
        return null;
    }

    private void applyDelete(WorkingCopy wc, Map<Tree, List<Tree>> modifiedMembers, boolean optimize, boolean clearance) throws Exception {
        if (!clearance) return;
        
        Tree memberTree = BatchCodeRefiner.findMemberInWorkingCopy(wc, memberFqn);
        if (memberTree == null) {
            BatchCodeRefiner.throwMemberNotFound(wc, memberFqn);
        }
        TreePath path = TreePath.getPath(wc.getCompilationUnit(), memberTree);
        Tree parent = path.getParentPath().getLeaf();

        List<Tree> members = getContainerMembers(parent, modifiedMembers);
        int idx = BatchCodeRefiner.findMemberIndex(wc, members, memberTree);
        if (idx != -1) {
            members.remove(idx);
        }
    }

    private void applyMove(WorkingCopy wc, Map<Tree, List<Tree>> modifiedMembers, boolean optimize, boolean clearance) throws Exception {
        if (clearance) {
            com.sun.source.tree.Tree memberTree = BatchCodeRefiner.findMemberInWorkingCopy(wc, memberFqn);
            if (memberTree == null) {
                BatchCodeRefiner.throwMemberNotFound(wc, memberFqn);
            }
            com.sun.source.util.TreePath path = com.sun.source.util.TreePath.getPath(wc.getCompilationUnit(), memberTree);
            if (!(path.getParentPath().getLeaf() instanceof com.sun.source.tree.ClassTree ct)) {
                throw new uno.anahata.asi.agi.tool.AgiToolException("Only members of a class can be moved.");
            }
            List<com.sun.source.tree.Tree> members = getContainerMembers(ct, modifiedMembers);

            int idx = BatchCodeRefiner.findMemberIndex(wc, members, memberTree);
            if (idx != -1) {
                members.remove(idx);
            }
            savedTreeForMove = BatchCodeRefiner.cloneTree(wc.getTreeMaker(), memberTree);
            calculatedIndex = BatchCodeRefiner.getInsertIndex(wc, members, position, anchorMemberName);
        } else {
            List<com.sun.source.tree.Tree> members = getContainerMembersByResolvedIndex(wc, modifiedMembers);
members.add(calculatedIndex != -1 ? Math.min(calculatedIndex, members.size()) : members.size(), savedTreeForMove);
        }
    }

    private List<Tree> getContainerMembers(Tree container, Map<Tree, List<Tree>> modifiedMembers) {
        return modifiedMembers.computeIfAbsent(container, p -> {
            if (p instanceof ClassTree ct) {
                return new ArrayList<>(ct.getMembers());
            } else {
                return new ArrayList<>(((CompilationUnitTree) p).getTypeDecls());
            }
        });
    }

    /**
     * Returns a human-readable HTML description of the intent for display in
     * the UI.
     */
    public String getHtmlDisplay() {
        String color = switch (type) {
            case INSERT ->
                "#4CAF50";
            case UPDATE ->
                "#2196F3";
            case DELETE ->
                "#F44336";
            case MOVE ->
                "#FF9800";
        };
        String icon = switch (type) {
            case INSERT ->
                "[+]";
            case UPDATE ->
                "[*]";
            case DELETE ->
                "[-]";
            case MOVE ->
                "[M]";
        };

        String targetName = (memberFqn != null) ? getSimpleName(memberFqn) : "New Member";
        if (type == Type.INSERT && declaration != null) {
            targetName = getSimpleNameFromDeclaration(declaration);
        }

        StringBuilder sb = new StringBuilder("<font color='").append(color).append("'>").append(icon).append("</font> ");
        sb.append("<b>").append(type.toString().toUpperCase()).append("</b> <code>").append(targetName).append("</code>");

        if (position != null) {
            sb.append(" ").append(position);
            if (anchorMemberName != null) {
                sb.append(" ").append(getSimpleName(anchorMemberName));
            }
        }

        if (reason != null && !reason.isBlank()) {
            sb.append(" <i style='color: #888888;'>(").append(reason).append(")</i>");
        }

        return sb.toString();
    }

    /**
     * Determines the FQN that the target member will have after this intent is
     * applied. For inserts, it derives the name from the declaration.
     *
     * @return The absolute FQN or null if it cannot be determined (e.g.
     * DELETE).
     */
    public String getResultingMemberFqn() {
        if (type == Type.DELETE) {
            return null;
        }
        if (type == Type.INSERT) {
            String name = getSimpleNameFromDeclaration(declaration);
            if (classFqn == null || classFqn.isBlank()) {
                return name;
            }
            return classFqn + "." + name;
        }
        return memberFqn;
    }

    private String getSimpleName(String fqn) {
        if (fqn == null || fqn.isBlank()) {
            return "Unknown";
        }
        int paren = fqn.indexOf('(');
        String namePart = (paren == -1) ? fqn : fqn.substring(0, paren);
        int lastDot = Math.max(namePart.lastIndexOf('.'), namePart.lastIndexOf('$'));
        return (lastDot == -1) ? namePart : namePart.substring(lastDot + 1);
    }

    private String getSimpleNameFromDeclaration(String decl) {
        String clean = decl.trim();
        while (clean.startsWith("@")) {
            int space = clean.indexOf(' ');
            if (space == -1) {
                break;
            }
            clean = clean.substring(space).trim();
        }
        int paren = clean.indexOf('(');
        int end = (paren != -1) ? paren : (clean.endsWith(";") ? clean.length() - 1 : clean.length());
        int start = clean.lastIndexOf(' ', end - 1);
        String name = clean.substring(start + 1, end).trim();
        return (paren != -1) ? name + "()" : name;
    }
}
