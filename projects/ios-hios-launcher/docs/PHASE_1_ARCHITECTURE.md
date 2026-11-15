# iOS + HiOS Hybrid Launcher — Phase 1 Architecture

## Repository decision

This project lives in the user's **public** repository:

- Repository: `yash410212/Universal-projects-`
- Development branch: `ios-hios-launcher-phase-1`
- Existing root project: HiOS Island (must remain untouched)
- New launcher project location: `projects/ios-hios-launcher/`

Do not place launcher Gradle files in the repository root or modify the existing root `:app` project. The root project is already HiOS Island and has its own Android build workflow. The launcher must be an isolated Gradle build inside the new subdirectory, with a separate GitHub Actions workflow.

## Target device

- TECNO Spark 8C (KG5k)
- Android 11 / HiOS 7.6
- 3 GB RAM, 64 GB storage
- No root
- Target application ID: `com.yash.liquidlauncher` (confirm no conflict before first install)
- Minimum SDK: 30 (Android 11)
- Kotlin + Jetpack Compose
- JDK 17

## Phase 1 goal

Build a stable, lightweight launcher foundation that provides a unified iOS-inspired + HiOS visual language while preserving normal Android behavior. Later modules (Dynamic Island-style live activity surface, Control Center companion, notification presentation and lock-screen companion) should be able to reuse the same design tokens, settings and event contracts.

This is a normal user-installed application, not a ROM or firmware replacement. No root, hidden APIs, signature permissions or OEM-private APIs.

## Repository layout

Keep the existing root project intact:

```text
Universal-projects-/
  app/                         # Existing HiOS Island app — DO NOT MODIFY
  .github/workflows/
    android.yml                # Existing HiOS Island workflow — DO NOT MODIFY
    launcher-phase-1.yml       # New isolated launcher workflow
  projects/
    ios-hios-launcher/
      settings.gradle.kts
      build.gradle.kts
      gradle.properties
      app/
        build.gradle.kts
        src/main/
          AndroidManifest.xml
          java/com/yash/liquidlauncher/
            LiquidLauncherApp.kt
            MainActivity.kt
            core/
            platform/
            data/
            domain/
            feature/
            design/
      docs/
        PHASE_1_ARCHITECTURE.md
```

The launcher workflow must trigger only for changes under `projects/ios-hios-launcher/**` and its own workflow file. It must run Gradle from the launcher subdirectory. It must upload a clearly named launcher APK artifact and must not overwrite or confuse the HiOS Island artifact.

## Architecture

Use a single app module inside the isolated launcher Gradle project. Prefer package boundaries over multiple Gradle modules in the first iteration; this keeps build complexity and runtime overhead low for the 3 GB RAM device.

Use MVVM and unidirectional state:

1. Platform adapters wrap Android APIs.
2. Repositories own app/workspace/widget data and persistence.
3. ViewModels expose immutable `StateFlow<UiState>` and accept explicit actions.
4. Compose screens render state and emit actions; they do not call platform APIs directly.
5. A small application container wires dependencies; avoid a heavyweight DI framework.

### Package tree

```text
com.yash.liquidlauncher/
  LiquidLauncherApp.kt
  MainActivity.kt
  core/
    app/             # dependency container and app contracts
    model/           # immutable shared models and stable IDs
    result/          # typed operation outcomes
    dispatch/        # app-scoped coroutine dispatchers
    persistence/     # DataStore setup and preference versioning
    performance/     # debug-only tracing
  platform/
    apps/            # LauncherApps adapter and package callbacks
    widgets/         # AppWidgetHost / AppWidgetManager adapter
    wallpaper/       # wallpaper metadata and visibility
    haptics/         # optional vibration adapter
    permissions/     # permission state and explanations
  data/
    apps/
    workspace/
    widgets/
    settings/
  domain/
    apps/            # list, search and launch use cases
    workspace/       # reorder, move, page and dock operations
    settings/
  feature/
    home/ui/
    home/state/
    drawer/ui/
    drawer/state/
    widgets/ui/
    widgets/state/
    settings/ui/
    settings/state/
  design/
    tokens/          # colors, spacing, shapes, elevations, motion
    theme/
    components/      # GlassSurface, GlassButton, IconTile, Dock
    motion/
```

## Core models

### Launchable app

```kotlin
data class LaunchableApp(
    val packageName: String,
    val className: String,
    val label: String,
    val userSerial: Long,
    val isSystemApp: Boolean,
)
```

Use package + activity + user profile as the stable key. Do not persist icon bitmaps. Load icons through the platform adapter and use a bounded in-memory cache.

### Workspace item

```kotlin
data class WorkspaceItem(
    val id: String,
    val packageName: String,
    val activityName: String,
    val page: Int,
    val cellX: Int,
    val cellY: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
)

data class WorkspaceSnapshot(
    val items: List<WorkspaceItem>,
    val dockPackageNames: List<String>,
    val pageCount: Int,
)
```

Persist stable identifiers and placement metadata, not Compose objects or bitmap data. Validate coordinates, spans and duplicate IDs when restoring.

### Appearance preferences

```kotlin
enum class HybridAppearance { SYSTEM, LIGHT, DARK }

data class LauncherAppearance(
    val appearance: HybridAppearance,
    val accentArgb: Long,
    val glassEnabled: Boolean,
    val blurEnabled: Boolean,
    val animationScale: Float,
    val iconScale: Float,
    val gridColumns: Int,
    val gridRows: Int,
)
```

Clamp values at the repository boundary. Default to conservative effects on Android 11 and provide reduced motion and transparency options.

## Visual language

- Combine iOS-inspired rounded geometry and translucent surfaces with HiOS-friendly behavior and Android conventions.
- Centralize color, spacing, typography, shape, elevation and motion tokens.
- Use wallpaper visibility, alpha, gradients and elevation as the Android 11 glass fallback.
- Android 11 has no public universal real-time backdrop blur API. Do not claim live blur; avoid expensive fake per-frame blur.
- Maintain text contrast, accessibility labels and usable touch targets.
- Support light/dark appearance, configurable accent and reduced motion.

Initial token scales:
- Spacing: 4, 8, 12, 16, 20, 24, 32 dp.
- Corner radii: 12, 18, 24, 30 dp.
- Surface levels: base, raised, floating.
- Motion: short interaction, standard transition, page transition.

## 3 GB RAM performance policy

These are targets to validate on the physical device, not guarantees:

- Event-driven work; no periodic polling loops for idle launcher state.
- Pause or stop nonessential animations when the launcher is not visible.
- No real-time backdrop blur on Android 11.
- Bounded icon and wallpaper caches; decode images near display dimensions.
- Never retain Activity, View, Context or bitmap references in global singletons.
- Lazy containers for large app lists.
- Stable Compose keys and immutable UI models to reduce recomposition.
- DataStore for small preferences; no database until justified.
- Avoid heavyweight DI and animation dependencies.
- Profile cold start, PSS, frame pacing and battery on the actual Spark 8C.

Acceptance observations:
- No recurring CPU work while home is idle.
- No obvious jank in drawer scroll or workspace paging.
- No progressive memory growth after repeatedly opening/closing drawer and settings.
- No crash after process recreation or app install/update/uninstall.
- Record actual PSS and frame timings; do not invent benchmark claims.

## Phase 1 milestones

### P1.0 — Isolated build baseline
- Create independent Gradle project under `projects/ios-hios-launcher/`.
- Use JDK 17, Android Gradle Plugin / Kotlin / Compose versions selected for compatibility.
- Add a launcher-specific GitHub Actions workflow.
- Verify root HiOS Island build remains unchanged.
- Confirm public Actions run and artifact naming.

### P1.1 — Launcher skeleton
- HOME / DEFAULT intent registration.
- Setup screen and default launcher guidance.
- App discovery using public `LauncherApps` APIs.
- App launch through public launcher APIs.
- Empty, loading and error states.

### P1.2 — Workspace and drawer
- Paginated workspace and dock.
- App drawer with search.
- Stable IDs and persisted ordering.
- Drag/drop and reorder with validated grid bounds.

### P1.3 — Design foundation
- Shared tokens and Compose theme.
- Reusable glass surfaces and icon tiles.
- Android 11-safe glass fallback.
- Light/dark, accent and reduced-motion preferences.

### P1.4 — Settings and persistence
- Persist appearance, accent, glass toggle, animation scale, icon scale and grid.
- Validate corrupt, missing and older preference values.
- Verify process-death and reboot persistence.

### P1.5 — Widget foundation
- App widget picker and configuration flow.
- AppWidgetHost lifecycle and widget ID persistence.
- Add/remove and basic placement metadata.
- Keep widget-host operations isolated from Compose rendering.

### P1.6 — Regression and device testing
- Default HOME selection and app launch.
- Install/update/uninstall refresh.
- Workspace/dock order persistence.
- Widget configure/remove and lifecycle.
- Process recreation and orientation/configuration changes.
- Physical-device smoke test and battery/memory observations.

## GitHub Actions requirements

The launcher workflow should:
- Trigger on pushes/PRs touching `projects/ios-hios-launcher/**` and its workflow file.
- Check out the public repository.
- Set up JDK 17.
- Set up a pinned Gradle version compatible with the wrapper/build plugins.
- Run `gradle --no-daemon :app:assembleDebug` with working directory `projects/ios-hios-launcher`.
- Upload `projects/ios-hios-launcher/app/build/outputs/apk/debug/app-debug.apk` as `Liquid-Hybrid-Launcher-debug`.
- Use read-only repository permissions unless a workflow step demonstrably needs more.

Do not alter the existing root `android.yml` workflow or the root HiOS Island app.

## Phase 1 acceptance criteria

- [ ] Isolated launcher Gradle project exists under the designated directory.
- [ ] Existing root HiOS Island project remains unchanged.
- [ ] Launcher-specific public GitHub Actions workflow succeeds.
- [ ] Debug APK artifact is uploaded with a distinct name.
- [ ] APK installs on Android 11 and can be selected as HOME.
- [ ] App discovery and launch work.
- [ ] Workspace, dock and drawer state persist.
- [ ] Theme and preferences survive process death.
- [ ] Widget picker/configuration lifecycle works.
- [ ] No root, hidden API or privileged permission dependency.
- [ ] Physical TECNO Spark 8C smoke test is completed and recorded.

## Out of scope

Dynamic Island, Control Center, lock-screen companion, notification shade replacement, custom volume panel and screenshot UI are later phases. Use public Android APIs and explicit user-granted permissions. If HiOS blocks an action, open the appropriate system settings page instead of pretending the action succeeded.

## Definition of done

Phase 1 is complete only when source implementation exists, the public CI workflow succeeds, the APK artifact is available, and the APK passes smoke testing on the actual Spark 8C. Documentation or CI compilation alone is not completion.
