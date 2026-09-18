# Nalama Health Companion Overhaul Plan

## 1. Objectives & Guiding Principles
- **Readability & High Contrast**: Ensure all text, buttons, badges, and card surfaces have outstanding contrast (meeting WCAG AAA standards) in both Light and Dark themes. No illegible button text, dark-on-dark labels, or muddy gradients.
- **End-User Refinement**: Strip all developer and engineering jargon ("BYOS", "Payload preview", "Schema", "Raw token", "JSON/CSV partitions", etc.). Replace with clean, approachable, health-companion language.
- **Remove Unnecessary / Debug Capabilities**:
  - Remove "View Splash" button from Settings.
  - Remove "View Script Source" / raw code viewer from the script setup dialog.
  - Remove "Full Script" vs "Inside myFunction" toggles (default seamlessly to the optimized `myFunction` workflow).
  - Remove "Payload" tab from the main bottom navigation and the "View Payload Preview" button from Dashboard.
  - Remove Git distribution HTML snippets from Settings.
- **Polished, Less Text-Heavy UI**: Restructure cards to be spacious, clean, and scannable with generous padding, modern typography, and clear visual hierarchy.

---

## 2. Implementation Roadmap

- [x] **Phase 1: Color Palette & Theme Overhaul**
  - Revise `Color.kt` and `Theme.kt` with accessible, high-contrast light and dark palettes.
  - Ensure button container/content color pairs (Primary on White, Tonal, Outlined) are strictly legible across all themes.
  - Fix dark-mode surface separation, card backgrounds, and border outlines.

- [x] **Phase 2: Navigation & Developer Feature Removal**
  - Update `MainActivity.kt` to remove the "Payload" tab from `NavigationBar`.
  - Retain 3 core tabs: Dashboard, History, and Settings.
  - Remove "View Payload Preview" navigation triggers from `DashboardScreen.kt`.
  - Remove `onNavigateToSplash` / "View Splash" from `SettingsScreen.kt` and `MainActivity.kt`.

- [x] **Phase 3: Dialogs & Script Setup Simplification**
  - Overhaul `AppsScriptDialog.kt`:
    - Remove "View Script Source" / code inspector block.
    - Remove "Full Script" / "myFunction" toggle chips.
    - Create a clean 1-tap "Copy Script & Open Editor" action.
    - Provide concise, friendly 3-step instructions for non-technical users.
  - Overhaul Google Account connection dialog in `LandingScreen.kt`:
    - Fix button legibility (crisp white text on vibrant primary button, clear outline/text for cancel).
    - Remove developer terminology ("private storage token remains on this phone", "BYOS", etc.).

- [x] **Phase 4: Screen Refinement & Text-Heavy Reduction**
  - **LandingScreen.kt**: Condense lengthy technical privacy/terms cards into clean, friendly health-companion messaging.
  - **DashboardScreen.kt**:
    - Refine `NalamaCompanionBanner` to remove tech jargon.
    - Polish `StatusBadge` items with high-contrast text and vibrant indicator icons.
    - Clean up `ExportControlsCard` to present defaults gracefully with less visual noise.
  - **SettingsScreen.kt**:
    - Remove Git APK HTML snippets and raw architecture lists.
    - Simplify account, source app, schedule, and sync settings.
  - **HistoryScreen.kt**:
    - Remove raw CSV string dumps from the export detail dialog; present a clean summary instead.

- [x] **Phase 5: Verification & Testing**
  - Run unit and UI tests (`gradle :app:testDebugUnitTest`).
  - Run full compilation verification (`compile_applet`).
  - Verify accessibility contrast and user journey.
