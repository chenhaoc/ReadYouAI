# Parallel Install Flavor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a separately installable Android flavor that does not replace the existing Read You app on device.

**Architecture:** Introduce a new product flavor under the existing `channel` dimension with a different `applicationId` and app label. Keep the current `githubRelease` behavior unchanged, then build the new release variant explicitly.

**Tech Stack:** Android Gradle Plugin, product flavors, Android resources, Gradle release build

---

### Task 1: Add Parallel Install Flavor

**Files:**
- Modify: `/Users/hao.chen/工作文档/Work/readyou/ReadYou/app/build.gradle.kts`

- [ ] **Step 1: Add a new flavor with a distinct package id and label override**

Add a new flavor named `githubAi` under the existing `channel` dimension. Set a unique `applicationId` or suffix so Android treats it as a different app, and override the visible app label to `Read You AI`.

- [ ] **Step 2: Keep existing flavors unchanged**

Do not modify the current `github`, `fdroid`, or `googlePlay` package ids so the normal release pipeline still behaves as before.

- [ ] **Step 3: Verify the new task name**

Use the flavor name so the resulting release task is `assembleGithubAiRelease`.

### Task 2: Build and Verify APK

**Files:**
- Use: `/Users/hao.chen/工作文档/Work/readyou/ReadYou/scripts/build-github-debug.sh`

- [ ] **Step 1: Run the dedicated release build**

Run:

```bash
./scripts/build-github-debug.sh assembleGithubAiRelease
```

Expected:
- Build succeeds
- APK is emitted under `app/build/outputs/apk/githubAi/release/`

- [ ] **Step 2: Confirm package separation behavior**

Verify from build configuration that the flavor package name differs from `me.ash.reader`, which ensures side-by-side installation rather than replacement.

- [ ] **Step 3: Record the output path**

Report the final APK path back to the user so they can install the parallel app directly.
