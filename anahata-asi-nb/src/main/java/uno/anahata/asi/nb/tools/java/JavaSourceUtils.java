/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
package uno.anahata.asi.nb.tools.java;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import lombok.extern.slf4j.Slf4j;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.Task;
import org.netbeans.api.java.source.TreeMaker;
import org.netbeans.api.java.source.TreePathHandle;
import org.netbeans.api.java.source.TreeUtilities;
import org.netbeans.api.java.source.WorkingCopy;
import org.netbeans.modules.refactoring.java.api.MemberInfo;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import uno.anahata.asi.agi.tool.AgiToolException;
import org.openide.loaders.DataObject;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.SaveCookie;

/**
 * Shared utilities for NetBeans Java Source (AST) operations.
 * <p>
 * Provides surgical helper methods for element resolution, tree handle
 * management, and Javadoc manipulation using the NetBeans Java Source API.
 * </p>
 *
 * @author anahata
 */
@Slf4j
public class JavaSourceUtils {
    

    /**
     * Extracts the parent FQN (Class or Outer Class) from a member or nested
     * type FQN.
     *
     * @param memberFqn The FQN to parse.
     * @return The parent FQN.
     */
    public static String getParentFqn(String memberFqn) {
        int paren = memberFqn.indexOf('(');
        String namePart = paren != -1 ? memberFqn.substring(0, paren) : memberFqn;
        int lastSeparator = Math.max(namePart.lastIndexOf('.'), namePart.lastIndexOf('$'));
        if (lastSeparator == -1) {
            return "";
        }
        return namePart.substring(0, lastSeparator);
    }

    /**
     * Extracts the simple name of a member or nested type from an FQN.
     *
     * @param memberFqn The FQN to parse.
     * @return The simple name (e.g., 'myMethod' or 'InnerClass').
     */
    public static String getMemberSimpleName(String memberFqn) {
        int paren = memberFqn.indexOf('(');
        String namePart = paren != -1 ? memberFqn.substring(0, paren) : memberFqn;
        int lastSeparator = Math.max(namePart.lastIndexOf('.'), namePart.lastIndexOf('$'));
        return namePart.substring(lastSeparator + 1);
    }

    /**
     * Resolves a {@link FileObject} for the given absolute path.
     *
     * @param filePath The absolute path to the file.
     * @return The corresponding FileObject.
     * @throws AgiToolException If the file does not exist or cannot be
     * resolved.
     */
    public static FileObject getFileObject(String filePath) throws AgiToolException {
        File f = new File(filePath);
        if (!f.exists()) {
            throw new AgiToolException("File does not exist: " + filePath);
        }
        FileObject fo = FileUtil.toFileObject(f);
        if (fo == null) {
            throw new AgiToolException("Could not resolve FileObject for: " + filePath);
        }
        return fo;
    }

    /**
     * Normalizes an Anahata FQN (which uses '$' for inner classes) to a
     * standard Java FQN (using '.') for compatibility with standard APIs.
     *
     * @param fqn The FQN to normalize.
     * @return The normalized FQN.
     */
    public static String normalizeFqn(String fqn) {
        if (fqn == null) {
            return null;
        }
        return fqn.replace('$', '.');
    }

    

    public static Tree findTree(CompilationInfo info, final String memberFqn) {
        final String pureFqn;
        final String indexPart;
        if (memberFqn.contains("#")) {
            int hash = memberFqn.indexOf('#');
            int paren = memberFqn.indexOf('(', hash);
            if (paren != -1) {
                indexPart = memberFqn.substring(hash + 1, paren);
                pureFqn = memberFqn.substring(0, hash);
            } else {
                indexPart = memberFqn.substring(hash + 1);
                pureFqn = memberFqn.substring(0, hash);
            }
        } else if (memberFqn.contains("(")) {
            indexPart = null;
            pureFqn = memberFqn.substring(0, memberFqn.indexOf('('));
        } else {
            indexPart = null;
            pureFqn = memberFqn;
        }

        final Tree[] found = new Tree[1];
        new com.sun.source.util.TreePathScanner<Void, Void>() {
            @Override
            public Void visitClass(ClassTree node, Void p) {
                if (found[0] != null) {
                    return null;
                }
                Element el = info.getTrees().getElement(getCurrentPath());
                if (el instanceof TypeElement te) {
                    String currentFqn = te.getQualifiedName().toString();
                    String normalizedPureFqn = normalizeFqn(pureFqn);
                    if (currentFqn.equals(normalizedPureFqn) && !memberFqn.contains("(") && indexPart == null) {
                        found[0] = node;
                        return null;
                    }

                    if (normalizedPureFqn.startsWith(currentFqn)) {
                        int currentBlockCount = 0;
                        for (Tree member : node.getMembers()) {
                            if (indexPart != null && member.getKind() == Tree.Kind.BLOCK) {
                                String blockName = ((BlockTree) member).isStatic() ? "<clinit>" : "<init-block>";
                                if (blockName.equals(getMemberSimpleName(pureFqn))) {
                                    if (++currentBlockCount == Integer.parseInt(indexPart)) {
                                        found[0] = member;
                                        return null;
                                    }
                                }
                            }
                            if (member instanceof MethodTree mt) {
                                String name = mt.getName().toString();
                                String targetName = getMemberSimpleName(pureFqn);
                                if (name.equals(targetName) || (name.equals("<init>") && targetName.equals("<init>"))) {
                                    if (memberFqn.contains("(")) {
                                        Element e = info.getTrees().getElement(new TreePath(getCurrentPath(), member));
                                        if (e instanceof ExecutableElement ee && matchSignature(info, ee, memberFqn)) {
                                            found[0] = member;
                                            return null;
                                        }
                                    } else if (!memberFqn.contains("#")) {
                                        found[0] = member;
                                        return null;
                                    }
                                }
                            }
                            if (member instanceof VariableTree vt && vt.getName().contentEquals(getMemberSimpleName(pureFqn))) {
                                if (currentFqn.equals(getParentFqn(pureFqn))) {
                                    found[0] = member;
                                    return null;
                                }
                            }
                        }
                    }
                }
                return super.visitClass(node, p);
            }
        }.scan(new TreePath(info.getCompilationUnit()), null);

        return found[0];
    }
    
    /**
     * Internal helper to match a method element against a string signature.
     * <p>
     * This implementation is erasure-aware. If the provided signature does not
     * contain generics, it will match against the raw AST type.
     * </p>
     */
    private static boolean matchSignature(CompilationInfo wc, ExecutableElement ee, String methodFqn) {
        String paramsPart = methodFqn.substring(methodFqn.indexOf('(') + 1, methodFqn.lastIndexOf(')')).trim();
        List<String> expectedTypes = splitParameters(paramsPart);
        if (expectedTypes.size() != ee.getParameters().size()) {
            return false;
        }
        for (int i = 0; i < expectedTypes.size(); i++) {
            String expected = expectedTypes.get(i);
            if (expected.contains(" ")) {
                String[] parts = expected.split("\\s+");
                expected = parts[parts.length - 1];
                if (expected.endsWith(">")) {
                    expected = parts[parts.length - 2];
                }
            }
            TypeMirror actualMirror = ee.getParameters().get(i).asType();
            Element actualEl = wc.getTypes().asElement(actualMirror);
            String actual = (actualEl instanceof TypeElement te)
                    ? ElementHandle.create(te).getBinaryName()
                    : actualMirror.toString();
            if (!expected.contains("<") && actual.contains("<")) {
                actual = actual.substring(0, actual.indexOf('<'));
            }
            if (!actual.endsWith(expected)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Splits a parameter string into individual types, respecting generic
     * brackets.
     */
    private static List<String> splitParameters(String params) {
        List<String> result = new ArrayList<>();
        if (params.isEmpty()) {
            return result;
        }

        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (char c : params.toCharArray()) {
            if (c == '<') {
                depth++;
            }
            if (c == '>') {
                depth--;
            }

            if (c == ',' && depth == 0) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString().trim());
        }
        return result;
    }

    

    


    /**
     * Generates a list of canonical candidate FQNs for a given partial member
     * name. Use this when a resolution fails to provide helpful feedback.
     *
     * @param info CompilationInfo.
     * @param memberFqn The FQN that failed resolution.
     * @return A list of valid canonical FQNs that share the same base name.
     */
    public static List<String> findMemberCandidates(CompilationInfo info, String memberFqn) {
        int paren = memberFqn.indexOf("(");
        String namePart = paren != -1 ? memberFqn.substring(0, paren) : memberFqn;
        int lastSeparator = Math.max(namePart.lastIndexOf("."), namePart.lastIndexOf("$"));
        if (lastSeparator == -1) {
            return Collections.emptyList();
        }
        String parentFqn = namePart.substring(0, lastSeparator);
        String name = namePart.substring(lastSeparator + 1);
        TypeElement parent = info.getElements().getTypeElement(normalizeFqn(parentFqn));
        if (parent == null) {
            return Collections.emptyList();
        }
        List<String> candidates = new ArrayList<>();
        for (Element e : parent.getEnclosedElements()) {
            if (e.getSimpleName().contentEquals(name)) {
                if (e instanceof ExecutableElement ee) {
                    String params = ee.getParameters().stream()
                            .map(p -> {
                                String type = p.asType().toString();
                                return type.contains("<") ? type.substring(0, type.indexOf("<")) : type;
                            })
                            .collect(Collectors.joining(","));
                    String namePrefix = (e.getKind() == javax.lang.model.element.ElementKind.CONSTRUCTOR) ? "<init>" : name;
                    candidates.add(parentFqn + "." + namePrefix + "(" + params + ")");
                } else {
                    candidates.add(parentFqn + "." + name);
                }
            }
        }
        return candidates;
    }

    
    

    public static void handleSave(FileObject fo) throws IOException {
        DataObject doid = DataObject.find(fo);
        EditorCookie ec = doid.getLookup().lookup(EditorCookie.class);
        if (ec != null && ec.getOpenedPanes() != null) {
            ec.saveDocument();
        }
        SaveCookie sc = doid.getLookup().lookup(SaveCookie.class);
        if (sc != null) {
            sc.save();
        }
        fo.refresh();
    }

}
