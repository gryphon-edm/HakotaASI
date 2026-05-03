/* Licensed under the Anahata Software License (ASL) v 108. See the LICENSE file for details. Força Barça! */
# Anahata ASI NetBeans (`anahata-asi-nb`)

> [!IMPORTANT]
> This file is an extension of the `anahata.md` in the parent project. Always keep the root `anahata.md` in context as it contains the master Coding Principles and Javadoc Standards.

This is the V2 NetBeans integration module for the Anahata ASI framework.

## 1. Core Principles

1.  **IDE API Preference**: Always prefer NetBeans APIs (e.g., `MimeLookup`, `EditorKit`) over direct file manipulation or generic Swing components when integrated into the IDE.
2.  **Dependency Hygiene**: 
    - **Automatic Spec Dependencies**: All artifacts listed in the `<dependencies>` section of the `pom.xml` are automatically included as `spec` dependencies in the module manifest by the `nbm-maven-plugin`.
    - Version Alignment: Always ensure that library versions (especially `flexmark` and `jsoup`) match the versions bundled with the target NetBeans release.

## 2. Annotator Strategy

We use a non-intrusive annotation system to provide visual feedback and context within the NetBeans code editors and project views. This includes:
- **Project Icons & Names**: Real-time badges and session counters for Anahata-enabled projects.
- **Dynamic Context Menus**: Unified "AI Context" submenus across all file types via the `AnahataAnnotationProvider`.
- **Editor Annotations**: Real-time feedback from the ASI directly on the source code lines.

## 3. Dependency Management

-   **`commons-io` Isolation**: This module explicitly bundles `commons-io` to avoid conflicts with the version used by the NetBeans Maven Embedder.

## 4. Classpath Safety

> [!TIP]
> **Automated Classpath Safety**
> When using `NbJava.compileAndExecuteInProject`, the tool automatically detects the NBM packaging and filters out NetBeans Platform/Stub JARs to prevent `LinkageError`s.

## 5. Reloading and Lifecycle
The NetBeans plugin runtime is static and **always** loads classes from the installed JAR:
`/anahata-asi-nb/target/nbm/clusters/extra/modules/uno-anahata-asi-nb.jar`.
Standard tool calls (the ones that show run buttons in the ui) will not reflect changes until an `nbmreload` is performed.


> [!IMPORTANT]
> to test changes to toolkits without reloading, do this: compileAndExecuteInproject(anahata-asi-nb, no compile deps, no test deps)
then instantiate the toolkit e.g. Refactor r = new Refactor(); and just use it, **there is no need to do setToolkit or setAgi** or onboard it any other way, instantiation is enough, contextpropagation (e.g. if the toolkit does log("") or error("") should happen automatically as it is based on a thread local, no need for manual/explicit onboarding)


- **Hot Reload Workflow (NbJava.compileAndExecuteInProject)**: 
    - **Strategy**: Implements a **Child-First** classloading strategy for project classes.
    - **Mechanism**: It prepends the project's `target/classes` folder to the search path. This allows you to test modified toolkit logic or DTOs immediately without reloading the whole IDE.
    - **Infrastructure Whitelist**: Critical classes (Agi, Resource, ResourceManager, ToolContext, SwingAgiTool, NbHandle, etc.) are forced to the **Parent ClassLoader**. This preserves "Instance Identity" for singletons and ThreadLocals, allowing your hot-reloaded script to safely interact with the live IDE environment.
- **nbmreload**: Required to update the "Static" tool calls used by the UI and the standard framework execution path.
- **Turn Sequencing**: Never batch `nbmreload` with source write operations. Wait for a successful compilation before triggering a reload.

Força Barça!
