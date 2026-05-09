# Tasks for Anahata ASI NetBeans (`anahata-asi-nb`)

check tasks.md in parent

## High Priority


## Medium Priority
- **BatchCodeRefiner Bug 1**: Fails with `Anchor member not found` if an intent refers to an anchor member that was just inserted in an earlier intent within the *same* batch. The AST/Lookup table isn't refreshing mid-batch to locate the newly created member.
- **BatchCodeRefiner Bug 2**: Fails with `Cannot invoke "String.contains(java.lang.CharSequence)" because "memberFqn" is null` if a relative `BEFORE/AFTER` position is used but `memberFqn` (or related lookup attributes) isn't correctly populated/inferred.
- **CodeRefiner Bug**: `optimizeImports` throws `NullPointerException: Cannot invoke "org.netbeans.api.lexer.Token.id()"` during `CasualDiff` if there are syntax ambiguities or specific generic parameter typings (e.g. `<T, R>`) not properly recognized by the lexer before imports are updated.
- **Investigate editTextResource Diff limitations**: Investigate why in `editTextResource` we cannot edit the right-hand side of the diff or do cherry-picking.
- **Investigate Session Save**: Investigate at what points we are saving the session to prevent NetBeans crashes from losing data.



