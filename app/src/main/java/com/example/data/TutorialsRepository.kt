package com.example.data

import com.example.model.TutorialDiagramType
import com.example.model.TutorialLesson
import com.example.model.TutorialStep

data class TutorialSeriesRange(
    val seriesNumber: Int,
    val startLesson: Int,
    val endLesson: Int,
    val categoryKey: String,
    val targetRoute: String,
    val targetHint: String,
    val diagram: TutorialDiagramType,
    val expectedResult: String,
    val commonError: String,
    val safetyNote: String? = null
) {
    val rangeLabel: String get() = "$startLesson–$endLesson"
    val fullLabel: String get() = "$rangeLabel $categoryKey"
}

object TutorialsRepository {

    val seriesRanges: List<TutorialSeriesRange> = listOf(
        TutorialSeriesRange(1, 1, 10, "Intro & Architecture", "home", "Home / Profile", TutorialDiagramType.WORKSPACE_FLOW, "Workspace health, identity, and offline modules load without network stalls.", "Assuming remote GitHub/CI works before configuring credentials."),
        TutorialSeriesRange(2, 11, 22, "Home & Navigation", "home", "5-Tab Shell", TutorialDiagramType.WORKSPACE_FLOW, "Tabs preserve state cleanly and Back exits dialogs/subscreens predictably.", "Leaving subscreens open without Back confirmation on deep flows."),
        TutorialSeriesRange(3, 23, 36, "Themes & Motion", "settings", "Settings -> Appearance", TutorialDiagramType.LIVE_CODING_SPLIT, "Selected Cyber/Glass theme and motion curves persist immediately across restarts.", "Testing bright glass cards at too low opacity in outdoor sunlight."),
        TutorialSeriesRange(4, 37, 52, "Workspace & Explorer", "project", "Project -> Explorer", TutorialDiagramType.WORKSPACE_FLOW, "Files, folders, breadcrumbs, and real byte totals update safely on disk.", "Editing or deleting inside wrong subfolder without checking breadcrumbs."),
        TutorialSeriesRange(5, 53, 64, "ZIP Safety & Detection", "project", "Project -> Import ZIP", TutorialDiagramType.WORKSPACE_FLOW, "Archives extract within project root while '..' and ZIP bombs are rejected.", "Disabling ZIP-Slip guard when opening untrusted third-party archives.", "Never bypass ZIP-Slip traversal validation for external archives."),
        TutorialSeriesRange(6, 65, 76, "Code Editor Core", "live_coding", "Live Coding -> Editor", TutorialDiagramType.LIVE_CODING_SPLIT, "Tabs, line numbers, undo/redo stacks, and syntax highlighting respond smoothly.", "Closing a dirty tab or editing huge minified bundles without Large-File mode."),
        TutorialSeriesRange(7, 77, 90, "Live Coding & Console", "live_coding", "Live Coding -> Preview", TutorialDiagramType.LIVE_CODING_SPLIT, "Split WebView renders local HTML/CSS/JS and streams console errors live.", "Expecting native Kotlin Composables to render inside Android WebView.", "Keep WebView Safe Mode active for untrusted HTML snippets."),
        TutorialSeriesRange(8, 91, 104, "HTML5, CSS3 & JS", "live_coding", "Live Coding -> Viewport", TutorialDiagramType.LIVE_CODING_SPLIT, "Responsive 360dp/768dp layouts and local script events execute cleanly.", "Using broken relative script paths or blocking synchronous scripts."),
        TutorialSeriesRange(9, 105, 116, "Web-to-APK Config", "build_center", "Build Center -> Web Wrapper", TutorialDiagramType.APK_ANATOMY, "Wrapper config, allowlist domains, and bundled assets validate cleanly.", "Leaving external navigation unrestricted or omitting entryUrl."),
        TutorialSeriesRange(10, 117, 128, "Native Kotlin & Gradle", "build_center", "Build Center -> Native Compat", TutorialDiagramType.APK_ANATOMY, "Gradle plugins, manifest flags, and missing local binaries are reported honestly.", "Assuming AAPT2/Kotlinc host builds run locally without CI/external SDKs."),
        TutorialSeriesRange(11, 129, 142, "Build Center & Cache", "build_center", "Build Center", TutorialDiagramType.APK_ANATOMY, "18-step pipeline logs, SHA-256 incremental hits, and build reports save accurately.", "Treating verified source bundles as Play-ready APKs when compiler tools are absent."),
        TutorialSeriesRange(12, 143, 152, "Toolchain & Health", "toolchain", "Toolchain Inspector", TutorialDiagramType.APK_ANATOMY, "Device ABIs, ART version, heap caps, and self-test probes report real status.", "Running parallel workers above RAM capacity on low-memory devices."),
        TutorialSeriesRange(13, 153, 164, "APK Inspector & Diff", "apk_tool", "APK Tool Tab", TutorialDiagramType.APK_ANATOMY, "Binary AndroidManifest tags, hashes, certificates, and two-APK diffs load.", "Ignoring permission additions or versionCode downgrades when comparing APKs."),
        TutorialSeriesRange(14, 165, 176, "Signing & Key Vault", "signing_wizard", "Profile -> Signing Keys", TutorialDiagramType.SIGNING_VAULT, "RSA/EC keystores encrypt with PBKDF2/AES-256-GCM and verify passwords cleanly.", "Losing release keystore passwords or reusing debug keys for upgrades.", "Never share private signing key material in plaintext or remote commits."),
        TutorialSeriesRange(15, 177, 188, "My Apps & Upgrades", "my_applications", "Profile -> My Applications", TutorialDiagramType.APK_ANATOMY, "Built outputs link to source projects with key continuity verified before upgrade.", "Upgrading an app with a mismatched signing key, which Android rejects."),
        TutorialSeriesRange(16, 189, 200, "Local Git & Merge", "git_ci", "Profile -> Git & GitHub", TutorialDiagramType.GITHUB_CI_PIPELINE, "Staging, commits, stashes, and 3-way branch conflict resolution preserve clean history.", "Switching branches or resetting with uncommitted changes without confirming."),
        TutorialSeriesRange(17, 201, 212, "GitHub OAuth & Sync", "git_ci", "GitHub Config & Device Auth", TutorialDiagramType.GITHUB_CI_PIPELINE, "OAuth/PAT authenticates via Keystore without logging tokens; rate limits show live.", "Pasting broad admin tokens or committing credentials inside source files.", "Tokens remain masked in UI and redacted from all logs."),
        TutorialSeriesRange(18, 213, 222, "GitHub Actions CI", "git_ci", "Workflow Builder & CI Runs", TutorialDiagramType.GITHUB_CI_PIPELINE, "YAML validates pinned actions, triggers dispatch, and verifies downloaded CI APK hashes.", "Passing repository secrets to untrusted pull_request workflows.", "Keep workflow permissions least-privilege and opt-in push triggers explicit."),
        TutorialSeriesRange(19, 223, 232, "Asset Studio & Audit", "asset_studio", "Asset Studio", TutorialDiagramType.WORKSPACE_FLOW, "Photo Picker images resize cleanly and duplicate/oversized assets are flagged.", "Shipping unoptimized >250KB raw images or invalid uppercase resource filenames."),
        TutorialSeriesRange(20, 233, 240, "Dev Utilities & ZIP", "utilities", "Dev Utilities", TutorialDiagramType.WORKSPACE_FLOW, "JSON/XML formatters, hashes, regex, diffs, and ZIP entry scanner run offline.", "Using raw unvalidated JSON/XML configs in production builds."),
        TutorialSeriesRange(21, 241, 250, "Security & PIN Vault", "security_diagnostics", "Privacy & Security", TutorialDiagramType.SIGNING_VAULT, "Secret scanner redacts leaks, repairs HTTP links safely, and enforces PIN lockout.", "Sharing debug logs before running redaction or leaving HTTP URLs unpatched."),
        TutorialSeriesRange(22, 251, 256, "Backup & AES-256", "backup_revision", "Backup & Revisions", TutorialDiagramType.BACKUP_ROLLBACK, "Snapshots verify SHA-256 checksums and save automatic pre-rollback backups.", "Restoring an archive over modified files without checking rollback diff preview."),
        TutorialSeriesRange(23, 257, 266, "Accessibility, 5 Languages & QA", "test_qa", "Test Center & Settings", TutorialDiagramType.WORKSPACE_FLOW, "RTL mirrors cleanly, 48dp targets hold, and Test QA reports verified metrics.", "Truncating translated strings or skipping LTR isolation on paths and hashes.")
    )

    private val topicsBySeries: Map<Int, List<Pair<String, String>>> = mapOf(
        1 to listOf(
            "Meet TK Studio v0.6.8" to "Explore the offline-first mobile IDE architecture and 5-tab workspace.",
            "Hybrid Glass & Soft UI Design" to "See how translucent cards and tactile raised/inset controls separate reading from action.",
            "Offline Engines vs Cloud Services" to "Distinguish local workspace/build/inspector engines from opt-in GitHub REST calls.",
            "App Identity & Package Layout" to "Inspect com.tkofficial.tkstudio metadata, sandbox directories, and Room storage.",
            "Command Search Architecture" to "Jump straight to projects, tutorials, and studio modules from Home search.",
            "First-Run Environment Diagnostics" to "Interpret CPU core counts, heap ceiling, supported ABIs, and free space.",
            "Zero-Mock Integrity Principles" to "Verify why every badge, checksum, and status pill reflects real state on disk.",
            "Task Notification & Event Log" to "Track redacted runtime events across builds, backups, and CI syncs.",
            "Deep Link Route Entry Points" to "Open specific TK Studio screens directly via tkstudio:// host routes.",
            "Profile Hub & Quick Launchers" to "Use the 20-card Profile shortcut hub and top-right Settings gear."
        ),
        2 to listOf(
            "5-Tab Bottom Navigation Bar" to "Move between Home, Project, Live Coding, APK Tool, and Profile smoothly.",
            "Adaptive Navigation Rail (Tablets)" to "Use side NavigationRail automatically on screens 720dp and wider.",
            "Startup Route & Last Project" to "Configure your preferred landing tab and project resume behavior in Settings.",
            "Home Health Pill & Dialog" to "Inspect quick device readiness and Room PRAGMA integrity directly from Home.",
            "Quick Actions Grid Workflows" to "Launch ZIP import, Live Coding, Quick Build, or GitHub CI in one tap.",
            "Task Queue & Progress Cards" to "Cancel long ZIP imports or builds cleanly without blocking UI navigation.",
            "Recovery Draft Banner on Home" to "Restore or dismiss interrupted editor buffers saved by crash recovery.",
            "Recent Projects Limit & Cards" to "Control how many recent workspaces appear on Home.",
            "System Back & Exit Confirmation" to "Respect modal exit prompts and subscreen BackHandler navigation.",
            "Snackbar & Error State Routing" to "Move from plain-language errors directly to retry or diagnostics.",
            "Multi-Window Split Responsiveness" to "Keep padding, cards, and WebView ratios legible in split-screen mode.",
            "Deep-Screen Subnavigation Flow" to "Navigate between Profile shortcuts, Settings categories, and back safely."
        ),
        3 to listOf(
            "7 Cyber/Glass Studio Palettes" to "Compare Cyber Blue, Emerald Teal, Obsidian Gold, Deep Rose, Electric Violet, Graphite Silver, and Light Indigo.",
            "Light, Dark & System Brightness" to "Switch theme brightness mode or follow Android system appearance.",
            "Glow Intensity Tuning" to "Adjust subtle rim highlights from 40% to 150% without harming text contrast.",
            "Glass Surface Opacity Control" to "Tune translucent card layers for dark OLED or bright daylight readability.",
            "Compact Layout Density" to "Switch tighter card spacing on smaller handheld screens.",
            "Text Scale & Typography Pairing" to "Combine legible UI fonts with JetBrains Mono for code and hashes.",
            "Reduce Motion Accessibility" to "Disable continuous animation loops and shorten screen transitions.",
            "Low Performance GPU Mode" to "Simplify ambient shadows and glows on entry-level devices.",
            "Animation Speed Multiplier" to "Adjust transition timing between 0.5x snappy, 1.0x standard, and 1.5x.",
            "High-Contrast Border Mode" to "Strengthen card borders for sharper separation under low vision.",
            "Canvas Signal & Orbital Motion" to "Observe code-drawn Vector/Canvas animations inside active scanners.",
            "Theme Isolation Safety" to "Confirm switching themes never discards unsaved Live Coding buffers.",
            "Live Palette Preview Cards" to "Inspect primary, cyan, violet, and background swatches before applying.",
            "Resetting Appearance Defaults" to "Restore Cyber Blue dark defaults safely with confirmation."
        ),
        4 to listOf(
            "Create Starter Workspaces" to "Initialize Web App, Web-to-APK, Kotlin, or Blank projects safely.",
            "All / Recent / Favorite / Archived Filters" to "Filter workspaces quickly by workflow stage and pinning.",
            "Project Type Chips" to "Narrow projects down to HTML5 Web, Web-to-APK, or Kotlin modules.",
            "Grid vs List Explorer View" to "Switch between dense file rows and visual folder cards.",
            "Breadcrumb Directory Traversal" to "Step up or down nested folders without losing root context.",
            "Sorting by Name, Size, Date & Type" to "Order files deterministically during large source audits.",
            "Creating Nested Files & Folders" to "Add stylesheets, scripts, and assets inside verified paths.",
            "Renaming Projects & Files" to "Rename safely while keeping project metadata and timestamps updated.",
            "Duplicating Baseline Projects" to "Clone an entire workspace directory before experimental edits.",
            "Archiving Finished Workspaces" to "Move inactive projects out of the main view without deleting them.",
            "Pinning Daily Favorites" to "Mark priority workspaces so they stay easy to open.",
            "Multi-Select Batch Delete" to "Select multiple files at once for clean workspace pruning.",
            "SAF File Import Without Storage Perms" to "Import external documents using Android OpenDocument zero-perm pickers.",
            "Exporting Projects as Clean ZIPs" to "Write full project archives via SAF CreateDocument excluding temp git dirs.",
            "Real Disk Usage & File Counts" to "Inspect exact byte totals computed from files on disk.",
            "Opening Files Straight into Editor" to "Tap any source file in Explorer to open a tab in Live Coding."
        ),
        5 to listOf(
            "Canonical Path Traversal Guard" to "Verify how ZIP-Slip protection rejects '..' and absolute root entries.",
            "Entry Count & Size Bomb Limits" to "Enforce 3,000-entry and 100MB extraction caps safely.",
            "Streaming Staging Extraction" to "Inspect progress feedback and mid-import cancellation.",
            "Automatic Web App Detection" to "Recognize index.html and HTML5 structures on archive import.",
            "Automatic Web-to-APK Detection" to "Identify www/index.html + app.config.json wrapper layouts.",
            "Native Kotlin Project Detection" to "Flag AndroidManifest.xml and .kt files for compatibility checks.",
            "Primary Entry-Point Resolution" to "Fallback cleanly to the main source file when default templates differ.",
            "Handling Corrupt ZIP Headers" to "Fail gracefully with descriptive errors on malformed archives.",
            "Inspecting Archive Contents First" to "Preview ZIP entry names and sizes in Utilities ZIP Explorer.",
            "Cleaning Failed Import Artifacts" to "Ensure failed or cancelled imports do not pollute active tabs.",
            "Preserving Subfolder Relative Paths" to "Keep css/, js/, and images/ hierarchies intact on extraction.",
            "Post-Import Workspace Indexing" to "Confirm newly imported projects appear in Home and Build Center."
        ),
        6 to listOf(
            "Opening Multiple File Tabs" to "Keep HTML, CSS, JS, JSON, and Kotlin tabs open simultaneously.",
            "Dirty Unsaved Tab Indicators" to "Spot modified buffers immediately from dot indicators on tab chips.",
            "Syntax Highlighting Toggle" to "Highlight keywords, tags, strings, and comments using Studio palette colors.",
            "Line Numbers Gutter Alignment" to "Sync monospace gutter numbers cleanly with scrolled code lines.",
            "Word Wrap vs Horizontal Scroll" to "Toggle line wrapping for prose/JSON or wide horizontal view for code.",
            "Undo & Redo 40-Step Stacks" to "Revert and reapply edits per tab without losing buffer state.",
            "Find & Replace (Single / All)" to "Search and update identifiers or CSS classes across the active file.",
            "Autosave Modes & Intervals" to "Switch between Instant autosave, timed saves, and manual Room draft protection.",
            "Crash Recovery Draft Protection" to "Persist unsaved manual buffers in Room until explicitly saved.",
            "Tab Width & Auto-Indent Options" to "Configure 2–8 space indentation and smart newline indent.",
            "Customizing Monospace Font Size" to "Tune editor size from 10sp to 22sp for handheld or tablet coding.",
            "Large-File Mode Optimization" to "Reduce heavy regex highlighting overhead on oversized files."
        ),
        7 to listOf(
            "Split Screen Editor & Preview" to "Code alongside live WebView updates with a draggable split slider.",
            "Tablet Side-by-Side Split" to "Switch automatically to horizontal dual-pane layout on screens >= 720dp.",
            "Editor-Only Focus Layout" to "Dedicate 100% height to distraction-free coding.",
            "Preview-Only Layout Check" to "Inspect rendered HTML interactions across full screen area.",
            "Fullscreen WebView Mode" to "Verify edge-to-edge mobile UI with Back button exit.",
            "Mobile 360dp vs Tablet 768dp Viewports" to "Preview responsive CSS breakpoints without changing devices.",
            "Base URL Local Asset Loading" to "Resolve relative css/, js/, and local images from project storage.",
            "WebChrome Console Log Bridge" to "Stream console.log, warnings, and JS errors into the Console drawer.",
            "Redacted Console Security" to "Ensure accidental secret logs are masked before display.",
            "Auto-Refresh vs Manual Reload" to "Control whether preview reloads automatically on save or manually.",
            "WebView JavaScript Toggle" to "Test static HTML fallback behavior with JS disabled in Settings.",
            "WebView Safe Mode Isolation" to "Keep allowUniversalAccessFromFileURLs and cross-file access locked off.",
            "Default Console Drawer State" to "Open Live Coding with console visible when debugging scripts.",
            "Native Kotlin Preview Notice" to "Understand why Compose/Kotlin directs users to Build Center instead of WebView."
        ),
        8 to listOf(
            "Semantic HTML5 Entry Structure" to "Validate DOCTYPE, viewport meta tags, and root landmarks.",
            "Responsive CSS Variables & Glass Cards" to "Build dark-mode cyber layouts using CSS custom properties.",
            "Flexbox & CSS Grid Breakpoints" to "Avoid horizontal overflow at 360dp mobile viewport width.",
            "Relative Stylesheet & Script Linking" to "Fix 404 console errors caused by leading slash paths in local WebView.",
            "DOM Event Listeners & State" to "Bind click/input handlers cleanly after DOM readiness.",
            "Capturing Async Fetch Errors" to "Log rejected promises clearly into the Live Coding console.",
            "LocalStorage & DOM Storage Checks" to "Configure domStorageEnabled safely for offline web persistence.",
            "Inline SVG vs Canvas Drawing" to "Render crisp vector badges cleanly inside HTML5 canvases.",
            "Form Validation & Touch Sizing" to "Size web buttons to >= 44px height for thumb comfort.",
            "Preventing Mixed-Content Loads" to "Use HTTPS endpoints inside web projects to pass WebView policies.",
            "Debugging JSON Payloads in App" to "Verify fetch responses and app.config.json syntax in Utilities.",
            "Bundling Offline Web Fonts" to "Keep stylesheets self-contained for zero-network environments.",
            "Inspecting Missing Asset Warnings" to "Cross-check HTML script/link tags using Web Wrapper analyzer.",
            "Preparing Web Assets for Packaging" to "Organize index.html, css/, js/, and assets/ before building."
        ),
        9 to listOf(
            "Web-to-APK Wrapper Blueprint" to "Inspect generated MainActivity.kt, AndroidManifest.xml, and wrapper config.",
            "Editing app.config.json Safely" to "Set appName, packageId, versionName, versionCode, and entryUrl.",
            "Domain Allowlist Navigation Guard" to "Restrict WebView navigation to approved host domains only.",
            "Disabling Cleartext HTTP Traffic" to "Verify android:usesCleartextTraffic=\"false\" in wrapper manifests.",
            "Mixed-Content Never-Allow Policy" to "Block insecure http subresources on https pages.",
            "SSL Error Safe Cancellation" to "Ensure WebViewClient calls handler.cancel() instead of proceeding.",
            "Orientation & Status Bar Options" to "Configure portrait, landscape, or unspecified orientation lock.",
            "WebView Back History Navigation" to "Route Android Back presses through WebView.goBack() cleanly.",
            "Detecting Missing Wrapper Assets" to "Catch broken local script/stylesheet references prior to build.",
            "Suggesting Permissions from Web APIs" to "Detect fetch/geolocation/camera calls and map Android permissions.",
            "Handling Modern JS Frameworks" to "Review notice when Vite/React/Next builds need static dist output first.",
            "Generating Web-to-APK Bundle" to "Package verified www/ bundle + wrapper Kotlin into build outputs."
        ),
        10 to listOf(
            "Native Kotlin Workspace Layout" to "Understand AndroidManifest.xml, build.gradle.kts, and source roots.",
            "Analyzing Gradle Plugins & Blocks" to "Detect com.android.application, Compose, KSP, and CMake blocks.",
            "Honest Local Compilation Boundary" to "Understand why full AGP/Kotlinc/AAPT2 requires host JDK/CI runners.",
            "Detecting Blocking Manifest Errors" to "Spot missing android:exported on launcher activities for API 31+.",
            "Detecting Insecure Cleartext Flag" to "Flag android:usesCleartextTraffic=\"true\" as a repairable issue.",
            "Confirmed Safe Manifest Repair" to "Create .bak backup automatically before patching manifest flags.",
            "Validating Package ID Syntax" to "Ensure reverse-domain identifiers follow [a-z][a-z0-9_]* rules.",
            "Validating Min/Target SDK Targets" to "Align targetSdk 36 compliance and minSdk device support.",
            "Inspecting Imported Kotlin Files" to "Read package declarations and imports inside Code Map.",
            "Dependency Checksum Inspection" to "Audit Gradle/package dependency references offline.",
            "Exporting Native Source to GitHub CI" to "Generate android-ci.yml workflow for remote APK compilation.",
            "Inspecting CI-Built Native APKs" to "Download workflow APK artifacts and inspect them in APK Tool."
        ),
        11 to listOf(
            "Auto Build Proposal & Config Diff" to "Review proposed build settings before applying changes.",
            "Manual Build Overrides" to "Customize appName, packageId, version, SDK levels, and output filename.",
            "Custom Build Compression & Flags" to "Tune ZIP compression levels (1–9) and strict flags.",
            "8+ Build Profiles Mapped to Behavior" to "Understand Fast, Stable, Clean, Low RAM, Debug, Release, Web, Native.",
            "Preflight Readiness Checklist" to "Verify entry file, free disk space, XML/JSON syntax, and key availability.",
            "SHA-256 Incremental File Cache" to "Skip unchanged assets and invalidate cleanly on config changes.",
            "Per-Workspace Build Mutex Locks" to "Prevent concurrent builds from corrupting staging directories.",
            "18-Step Pipeline Traceability" to "Follow Preparing, Scanning, Validating, Packaging, Signing, Verifying.",
            "Cancelling Active Builds Safely" to "Abort running tasks without leaving locked workspaces or partial outputs.",
            "Structured Error Codes & Locations" to "Read exact ERR_* codes, failed stage, and offending line numbers.",
            "Artifact Readback Verification" to "Verify archive entry count, path safety, and output SHA-256.",
            "Exporting TXT, JSON & HTML Reports" to "Share redacted build reports cleanly from Build History.",
            "Clean Cache Before Build Toggle" to "Force full re-verification when testing clean builds.",
            "Build-on-Save Opt-In Setting" to "Configure optional automated triggers safely in Settings."
        ),
        12 to listOf(
            "ART/JVM & Device Hardware Probe" to "Read live OS SDK, ART runtime, heap budget, and CPU core count.",
            "Supported CPU ABI Inventory" to "Inspect arm64-v8a, armeabi-v7a, and x86_64 host compatibility.",
            "Chromium WebView Provider Check" to "Inspect the device WebView package and version backing previews.",
            "Built-in Crypto & ZIP Engine Check" to "Verify AES-GCM, EC-256, RSA, and Deflate engines on device.",
            "Checking Missing Host Compilers" to "See clear guidance when aapt2, d8, kotlinc, or gradle are absent.",
            "Importing Custom Tool Binaries via SAF" to "Register user-supplied tool files safely and verify SHA-256 hashes.",
            "Running Live Toolchain Self-Tests" to "Execute live ZIP roundtrip and EC-256 sign/verify probes.",
            "Capping Workers by RAM & Cores" to "Protect devices from OutOfMemory crashes during multi-file scans.",
            "Toolchain Completeness Score" to "Track ready vs missing tools accurately without inflation.",
            "Low-Storage Preflight Thresholds" to "Prevent build failures when usable device space drops below 25MB."
        ),
        13 to listOf(
            "Inspecting Installed TK Studio APK" to "Load the app's own base APK path in one tap.",
            "Loading External APKs via SAF" to "Inspect downloaded or shared APKs with zero broad storage permissions.",
            "Overview Metadata Telemetry" to "Read packageName, versionName, versionCode, minSdk, targetSdk.",
            "Binary AndroidManifest (AXML) Parser" to "Decode binary XML string pools and manifest attributes offline.",
            "Permission Risk Categorization" to "Review all declared <uses-permission> entries in the APK.",
            "App Components Breakdown" to "Inspect Activities, Services, BroadcastReceivers, and Providers.",
            "ZIP Entry Compression & Alignment" to "Audit STORED vs DEFLATED entries and ZipAlign verification.",
            "DEX Count & Native ABI Folders" to "Count classes*.dex files and detect bundled .so ABIs.",
            "SHA-256, SHA-1 & MD5 Checksums" to "Verify release file integrity digests across tabs.",
            "Side-by-Side APK Comparison" to "Compare added, removed, and modified entries between two APKs.",
            "Permission & Signature Delta Check" to "Catch new permissions or certificate mismatches before upgrading.",
            "Safe Extract & System Install Handoff" to "Unpack APK safely with Zip-Slip guard or launch Android installer."
        ),
        14 to listOf(
            "Why App Signing Keys Matter" to "Understand Android update enforcement tied to signing certificates.",
            "X.500 Certificate Fields" to "Configure CN, OU, O, Locality, State, and 2-letter Country.",
            "RSA 2048/4096 vs EC P-256 Keys" to "Choose algorithm strength and compatibility for target SDKs.",
            "25+ Year Validity Planning" to "Ensure certificates outlive long-term app maintenance lifecycles.",
            "PBKDF2 + AES-256-GCM Key Vault" to "Protect private keys at rest with strong salted derivation.",
            "Password Confirmation Validation" to "Verify key unlock passwords live before signing artifacts.",
            "Signing Build Manifests" to "Generate cryptographic .sig proofs during Release builds.",
            "Reading X.509 Certs from APKs" to "Inspect subject, issuer, algorithm, and SHA-256 fingerprint.",
            "APK Signature Schemes v1 vs v2/v3" to "Identify META-INF JAR signatures vs APK Sig Block 42.",
            "Separating Debug & Release Keys" to "Avoid shipping local test signatures to production channels.",
            "Exporting Key Public Metadata" to "Share SHA-256 fingerprints safely while excluding private keys.",
            "Deleting Obsolete Test Keys" to "Clean unused test aliases without disturbing active release keys."
        ),
        15 to listOf(
            "My Applications Hub Overview" to "View every built bundle or inspected app linked to its source project.",
            "Reading Recorded App Metadata" to "Inspect appName, packageId, versionName, versionCode, and SHA-256.",
            "Missing Source or Missing APK States" to "Spot deleted projects or moved APK outputs clearly via badges.",
            "Signing Continuity Check Explained" to "Compare active keystore fingerprint against previous build signature.",
            "Handling Signing Key Mismatch Alerts" to "Resolve mismatched aliases or missing vault keys before shipping.",
            "Opening Linked Source Workspace" to "Jump straight from an application entry to Project Explorer.",
            "Editing Code from My Applications" to "Open the linked app's entry file directly in Live Coding.",
            "Incrementing Version Name & Code" to "Bump versionCode cleanly before triggering an upgrade build.",
            "Rebuilding With Matching Identity" to "Launch Build Center pre-filled with the app's package and signing config.",
            "Comparing Old vs New App Builds" to "Open APK Tool compare mode directly from an app entry.",
            "System Installer & Export Handoff" to "Share or hand off valid .apk files via FileProvider safely.",
            "Tracking Per-App Build History" to "Review timestamps, build profiles, and pass/fail logs per app."
        ),
        16 to listOf(
            "Pure-Kotlin Local Git Snapshots" to "Track changes inside .tkstudio_git without needing a native git binary.",
            "Staging & Unstaging Modified Files" to "Review line-level ADDED/MODIFIED/DELETED diffs before committing.",
            "Author Identity Settings" to "Set persistent commit author name and email in Settings.",
            "Creating & Listing Local Branches" to "Isolate features on sanitized feature branches.",
            "Dirty Working Tree Branch Guard" to "Prevent accidental loss of uncommitted edits on branch switch.",
            "Stashing & Popping WIP Edits" to "Save work in progress cleanly when switching tasks.",
            "Common Ancestor 3-Way Merge Base" to "Understand how TK Studio detects independent branch edits.",
            "Previewing Merge Conflicts First" to "Inspect clean vs conflicting files before mutating working files.",
            "Resolving Conflicts (Ours vs Theirs)" to "Pick exact conflict resolutions interactively in Git & CI.",
            "Discarding Changes With Confirmation" to "Confirm destructive working-tree resets against HEAD safely.",
            "Deleting Merged Branches Safely" to "Require confirmation before removing inactive local branches.",
            "Inspecting Local Commit Timeline" to "Browse commit IDs, messages, branches, and file counts."
        ),
        17 to listOf(
            "GitHub OAuth Device Flow vs PAT" to "Choose browser code authorization or masked Personal Access Token.",
            "AndroidKeyStore Token Protection" to "Verify tokens encrypt at rest via Hardware/KeyStore AES-GCM.",
            "Migrating Legacy Plaintext Tokens" to "Automatically move old stored tokens into the encrypted vault.",
            "Setting Least-Privilege Token Scopes" to "Limit PAT permissions strictly to repo and workflow scopes.",
            "Listing Accessible Repositories" to "Filter private/public repos and inspect push permissions.",
            "Creating Remote GitHub Repositories" to "Provision a new repo directly from TK Studio.",
            "Cloning Repositories Into Sandbox" to "Download remote trees safely with Zip-Slip path protection.",
            "Pulling Remote Branch Updates" to "Sync remote commits cleanly into local project files.",
            "Pushing Local Commits via REST API" to "Create remote blobs, trees, and commits atomically.",
            "Classifying 401/403/404 Errors" to "Read clear diagnostics for invalid tokens vs missing permissions.",
            "Live X-RateLimit-Remaining Meter" to "Pause polling automatically when API quota drops low.",
            "Disconnecting & Wiping Credentials" to "Erase stored tokens completely on sign-out."
        ),
        18 to listOf(
            "Android Gradle & Web YAML Templates" to "Start from verified GitHub Actions workflow templates.",
            "Workflow Security Audit Rules" to "Enforce least-privilege contents: read permissions.",
            "Pinning Action Versions Safely" to "Flag unpinned or deprecated action tags before saving.",
            "Pull-Request Secret Exposure Guard" to "Block pull_request workflows referencing sensitive signing secrets.",
            "Opt-In Push Triggers" to "Keep push triggers disabled until intentionally enabled.",
            "Previewing Workflow YAML Diffs" to "Compare proposed YAML against existing .github/workflows/ + .bak.",
            "Triggering workflow_dispatch Builds" to "Start remote GitHub Actions runs directly from mobile.",
            "Live Run Polling & Rate Guard" to "Track queued, in_progress, and completed runs cleanly.",
            "Downloading & Hashing CI Artifacts" to "Verify SHA-256 checksums and safely unzip APK outputs.",
            "Drafting Tagged GitHub Releases" to "Publish release notes and upload verified APK assets."
        ),
        19 to listOf(
            "Zero-Permission Photo Picker" to "Pick user images cleanly via PickVisualMedia without storage perms.",
            "Resizing Images to Exact Px" to "Scale icons and splash banners from 64px to 1024px.",
            "PNG vs WEBP vs JPEG Quality" to "Trade lossless transparency against compact mobile bundle size.",
            "Android Resource Filename Sanitizer" to "Auto-enforce lowercase [a-z0-9_]+ filenames cleanly.",
            "Coded Brand Icon PNG Generator" to "Render crisp monogram icons using active theme colors.",
            "Scanning Duplicate Project Assets" to "Detect identical files by SHA-256 content hash.",
            "Flagging Oversized >250KB Media" to "Spot heavy unoptimized assets bloating APK/bundle size.",
            "Previewing Unused Asset Candidates" to "Find images not referenced in HTML, CSS, JS, or XML sources.",
            "Confirmed Resource Cleanup" to "Inspect exact bytes reclaimed before deleting flagged assets.",
            "Exporting Visual Assets Back to Project" to "Keep project assets/ organized and immediately ready to build."
        ),
        20 to listOf(
            "JSON Pretty-Printer & Validator" to "Format minified configs and pinpoint syntax errors cleanly.",
            "XML PullParser Validator" to "Verify AndroidManifest and layout XML well-formedness offline.",
            "HEX, RGB & HSL Color Converter" to "Convert design tokens accurately with live color swatch preview.",
            "Multi-Algorithm Digest Hasher" to "Generate SHA-256, SHA-1, and MD5 checksums offline.",
            "Live Regex Tester & Match Ranges" to "Test regex expressions and inspect captured index bounds.",
            "Line-by-Line Text Diff Viewer" to "Compare config snippets or code revisions offline.",
            "Base64 & URL Encoder/Decoder" to "Encode or inspect payloads cleanly with UTF-8 safety.",
            "SAF ZIP Explorer & Copy/Export" to "Inspect ZIP entries + traversal risk and copy/export tool output."
        ),
        21 to listOf(
            "Workspace Secret Scanner Engine" to "Detect AIza API keys, GitHub PATs, AWS keys, and private PEMs.",
            "Redacted Leak Previews" to "Inspect only the final 4 characters while masking sensitive bodies.",
            "Scanning Cleartext HTTP URLs" to "Identify insecure http:// links in manifests and web configs.",
            "Confirmed Safe Repair Previews" to "View exact before/after lines before patching insecure links.",
            "Threat Model Matrix Checklist" to "Audit ZIP-Slip, WebView, Keystore, and CI security controls.",
            "App Lock PIN Setup & PBKDF2 Hash" to "Protect workspace access with salted PBKDF2-HMAC-SHA256 PINs.",
            "5-Attempt Brute-Force Lockout" to "Enforce 60-second cooldown after 5 invalid PIN tries.",
            "Auto-Lock Timeout on Background" to "Lock TK Studio automatically after configurable idle timeouts.",
            "Auditing Least-Privilege Manifest" to "Verify zero broad storage or install-package permissions.",
            "Exporting Redacted Privacy Metadata" to "Share sanitized diagnostics with secrets stripped."
        ),
        22 to listOf(
            "Point-in-Time .tkbak Snapshots" to "Archive entire project trees excluding temporary git caches.",
            "AES-256-GCM Encrypted .tkenc Backups" to "Lock sensitive snapshots using PBKDF2-derived keys.",
            "SHA-256 Integrity Verification" to "Verify archive checksums before trusting any restore point.",
            "Pre-Rollback Diff Preview" to "Review added, removed, and modified files prior to overwrite.",
            "Automatic Pre-Rollback Safety Backup" to "Save current state automatically before any rollback restore.",
            "Full Settings & Crash Recovery Inspection" to "Export sanitized settings (no private keys) and clear recovery queues."
        ),
        23 to listOf(
            "Accessibility Touch Targets >= 48dp" to "Ensure buttons, chips, and icon actions meet ergonomic touch minimums.",
            "TalkBack Content Descriptions" to "Verify meaningful semantic labels across interactive controls.",
            "5-Language UI Switching" to "Toggle English, বাংলা, العربية, 简体中文, and اردو seamlessly.",
            "RTL Mirroring for Arabic & Urdu" to "Mirror navigation, rows, and spacing while keeping code LTR.",
            "TechnicalLtrText Isolation" to "Keep file paths, hashes, package IDs, and URLs left-to-right in RTL.",
            "Locale-Aware Numbers & Dates" to "Format counts and timestamps for active studio language.",
            "Room Database PRAGMA Integrity Check" to "Verify schema version and SQLite integrity inside Test Center.",
            "Safe Temporary Cache Cleanup Preview" to "Inspect cleanable apk_cache and extracted bytes before wiping.",
            "Performance Latency & Heap Telemetry" to "Measure QA check execution time and live memory headroom.",
            "Full 5-Language & 266-Lesson QA Suite" to "Run automated completeness, policy, and compatibility checks."
        )
    )

    val lessons: List<TutorialLesson> by lazy {
        val result = ArrayList<TutorialLesson>(266)
        seriesRanges.forEach { series ->
            val topics = topicsBySeries[series.seriesNumber].orEmpty()
            topics.forEachIndexed { idx, (topicTitle, topicSummary) ->
                val num = series.startLesson + idx
                val id = "tut_${num.toString().padStart(3, '0')}"
                result.add(
                    TutorialLesson(
                        id = id,
                        title = "$num. $topicTitle",
                        category = series.fullLabel,
                        seriesKey = series.categoryKey,
                        summary = topicSummary,
                        durationMinutes = 3 + (num % 4),
                        prerequisites = if (num == 1) listOf("None") else listOf("Lesson #${num - 1}"),
                        diagramType = series.diagram,
                        targetRoute = series.targetRoute,
                        expectedResult = series.expectedResult,
                        commonError = series.commonError,
                        safetyNote = series.safetyNote,
                        checkpointQuestion = "Did you verify '$topicTitle' inside ${series.targetHint}?",
                        checkpointAnswerHint = series.expectedResult,
                        steps = listOf(
                            TutorialStep("Open ${series.targetHint}", "Launch ${series.targetHint} directly or via Profile shortcuts.", series.targetHint),
                            TutorialStep("Practice: $topicTitle", topicSummary, series.targetHint),
                            TutorialStep("Confirm Expected Result", series.expectedResult, series.targetHint)
                        )
                    )
                )
            }
        }
        result
    }
}
