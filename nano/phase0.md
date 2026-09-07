Perfect pivot — and honestly this order is *better* for your setup. Auth recon is only 15 minutes of work **once**, and doing it last means the app's own WebView login dialog will exist by then, so the token problem half-solves itself. Phase 0 becomes: **repo + CI compiler + complete buildable skeleton + design system**. GitHub Actions is your IDE's compile button.

# 🔄 Reshuffled Board (auth deferred to the very end)

| Phase | What | Gate |
|---|---|---|
| **P0** | Repo + CI + Skeleton + Design System | **Green build + APK installs on phone** |
| P1 | `ZaiConfig` (placeholder values) + `TokenManager` + `SettingsDataStore` | compiles |
| P2 | DTOs + Interceptors + `ManualSseReader` + `ZaiApiService` | compiles |
| P3 | Room + FTS | compiles |
| P4 | Repository layer | compiles |
| P5 | Auth WebView dialog (**code only**, untested) | compiles |
| P6 | Component library | previews |
| P7 | Chat screen E2E | compiles + runs |
| P8 | Drawer + search + history | runs |
| P9 | Settings wiring | runs |
| P10 | File upload | runs |
| P11 | Hardening + release CI | release APK |
| **P12** | 🔴 **LIVE recon + token + smoke test** (the deferred stuff) | real messages flow |

Placeholder config values in P1, verified values swapped in P12 — and because everything lives in one `ZaiConfig.kt`, that's a 6-line diff. The single-file-config philosophy pays off exactly here.

---

# 🏗️ Phase 0 — Deep Engineering

**Goal:** Termux-only project that produces an installable, themed APK via GitHub Actions on every push.
**Done when:** `gh run download` gives you `app-debug.apk`, it installs, and opens to a black OLED screen.

## Step A — Scaffold in Termux

```bash
# one-time identity (if fresh Termux)
git config --global user.name "you"
git config --global user.email "you@users.noreply.github.com"

mkdir -p ~/zai && cd ~/zai && git init

# full directory tree in one shot
mkdir -p .github/workflows \
  gradle/wrapper \
  app/src/main/java/com/zai/chat/{config,ui/theme} \
  app/src/main/res/values
```

## Step B — The files

### `.gitignore`
```gitignore
*.iml
.gradle/
local.properties
.idea/
.DS_Store
build/
captures/
.externalNativeBuild/
.cxx/
```

### `.github/workflows/android.yml` — this is your compiler
```yaml
name: android-build

on:
  push:
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - uses: gradle/actions/setup-gradle@v4   # caches Gradle + deps automatically

      - name: Assemble debug APK
        run: ./gradlew assembleDebug --no-daemon --stacktrace

      - uses: actions/upload-artifact@v4
        with:
          name: debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
```

Ubuntu runners ship with the Android SDK and accepted licenses — AGP auto-installs `android-35` if missing. No SDK setup needed.

### `settings.gradle.kts`
```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "ZAI-Chat"
include(":app")
```

### `build.gradle.kts` (root)
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
}
```

### `gradle.properties`
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
android.suppressUnsupportedCompileSdk=35
```

### `gradle/libs.versions.toml` — **complete version** (your PDF's copy is line-truncated; use this one)

```toml
[versions]
agp = "8.5.2"
kotlin = "2.0.20"
ksp = "2.0.20-1.0.25"
composeBom = "2024.09.00"
coroutines = "1.8.1"
hilt = "2.51.1"
okhttp = "4.12.0"
kotlinxSerialization = "1.7.1"
room = "2.6.1"
datastore = "1.1.1"
securityCrypto = "1.1.0-alpha06"
coil = "2.7.0"
lifecycle = "2.8.4"
navigationCompose = "2.8.0"
activityCompose = "1.9.2"
coreKtx = "1.13.1"
hiltNavigationCompose = "1.2.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }

compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }

kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }

okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }

androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
androidx-security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "securityCrypto" }

hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }

coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

### `app/build.gradle.kts` — exactly as spec §2
Use the PDF's version verbatim (it's complete on pages 4–5). Plugins, `compileSdk = 35`, `minSdk = 26`, opt-in flags, dependencies block — all correct as-is.

### `app/proguard-rules.pro`
```
# Populated in Phase 11 (kotlinx.serialization + Hilt keep rules)
```

### `app/src/main/AndroidManifest.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".ZaiApplication"
        android:label="@string/app_name"
        android:theme="@style/Theme.ZAI"
        android:supportsRtl="true"
        android:allowBackup="false">

        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

### `app/src/main/res/values/strings.xml`
```xml
<resources>
    <string name="app_name">Z.AI</string>
</resources>
```

### `app/src/main/res/values/themes.xml` — black from first frame (no white flash)
```xml
<resources>
    <style name="Theme.ZAI" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">#FF000000</item>
    </style>
</resources>
```

### `java/com/zai/chat/ZaiApplication.kt`
```kotlin
package com.zai.chat

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ZaiApplication : Application()
```

### `java/com/zai/chat/ui/MainActivity.kt` — Phase 0 placeholder
```kotlin
package com.zai.chat.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zai.chat.ui.theme.ZaiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ZaiTheme(themeMode = "OLED") {
                Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
                    Box(
                        Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Z.AI — Phase 0 online",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
```

### Design system — `ui/theme/`
**Copy verbatim from your spec PDF §7:** `Color.kt`, `Spacing.kt`, `Motion.kt`, `Type.kt`, `Theme.kt`.

**`Shape.kt` is missing from the PDF** — here it is:
```kotlin
package com.zai.chat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// Semantic shape tokens — components reference these, never raw values
val ShapeBubbleUser = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
val ShapeThinking = RoundedCornerShape(12.dp)
val ShapeCodeBlock = RoundedCornerShape(10.dp)
val ShapeChip = RoundedCornerShape(16.dp)
val ShapeCard = RoundedCornerShape(8.dp)
```

## Step C — Gradle wrapper (Termux can't generate it, so we fetch it)

```bash
curl -sL -o gradle/wrapper/gradle-wrapper.jar \
  https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar

curl -sL -o gradlew \
  https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradlew
chmod +x gradlew
```

Then create `gradle/wrapper/gradle-wrapper.properties`:
```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

*(Gradle 8.9 satisfies AGP 8.5.2's 8.7+ requirement.)*

## Step D — Push and enter the CI loop

```bash
pkg install -y gh
gh auth login        # GitHub.com → HTTPS → "Login with a web browser" → type the code on github.com/login/device

git add -A
git commit -m "P0: skeleton + CI + design system"
gh repo create zai --private --source=. --push

gh run watch         # live CI status — first run ~5min (dep download), later runs ~2-3min

# when green:
gh run download -n debug-apk -D ~/apk
termux-open ~/apk/app-debug.apk    # share sheet → install
```

---

## ✅ Phase 0 Gate

- [ ] `gh run watch` ends green
- [ ] `app-debug.apk` downloaded, sideloaded, launches
- [ ] Black screen, peach "Phase 0 online" text centered (proves theme + Hilt init)

## Troubleshooting quick reference

| Symptom | Fix |
|---|---|
| `SDK location not found` | Add env `ANDROID_HOME: /usr/local/lib/android/sdk` to the build step |
| `Unsupported class file major version` | JDK mismatch — confirm `java-version: '17'` |
| Hilt/KSP version clash | You edited the toml — restore pinned set above, they're mutually compatible |
| `gradlew: permission denied` locally | cosmetic only; CI uses its own exec bit — ignore |
| compileSdk 35 warning in logs | Harmless (already suppressed in gradle.properties) |
| Red run | `gh run view --log-failed` → paste the tail, fix, `git push` again |

**Your loop from Phase 1 onward:** nano the files → `git add -A && git commit -m "P3: room" && git push` → `gh run watch`. Red? Fix + push. Green? Next phase. APK install only when you want to *see* something (P6+).

One nuance to note for later: since compile-checks are remote, let the agent write **one phase at a time** and keep every phase compilable in isolation — a red CI with 15 new files is miserable to debug; red with 6 is a 30-second fix.

Ready for **Phase 1** whenever you are — that's `ZaiConfig.kt` (with clearly-marked placeholder recon values), `TokenManager.kt`, and `SettingsDataStore.kt`, full file contents, ready for nano.
