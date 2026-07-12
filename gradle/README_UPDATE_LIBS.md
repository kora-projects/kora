# Updating `libs.versions.toml`

This directory contains a small Gradle init script for checking and updating
`gradle/libs.versions.toml` from Maven metadata:

- `update-libs-versions.gradle` - the updater task implementation.
- `libs-version-policy.toml` - per-version update limits for dependencies that
  must stay on a specific major/minor line.

The updater is dry-run by default. It prints available updates without changing
files.

## Dry Run

```bash
./gradlew -I gradle/update-libs-versions.gradle updateLibsVersions
```

On Windows:

```bash
.\gradlew.bat -I gradle\update-libs-versions.gradle updateLibsVersions
```

## Apply Updates

Add `-PwriteVersions=true` to write the selected updates back to
`gradle/libs.versions.toml`:

```bash
./gradlew -I gradle/update-libs-versions.gradle updateLibsVersions -PwriteVersions=true
```

Without `-PwriteVersions=true`, the task never modifies files.

## Update Level

Use `-PupdateLevel=...` to control how far versions may move.

Allowed values:

- `any` - allow any newer stable version.
- `major` - allow major version changes.
- `minor` - keep the current major version.
- `patch` - keep the current major and minor version.

Examples:

```bash
./gradlew -I gradle/update-libs-versions.gradle updateLibsVersions -PupdateLevel=minor
```

```bash
./gradlew -I gradle/update-libs-versions.gradle updateLibsVersions -PupdateLevel=patch -PwriteVersions=true
```

If `-PupdateLevel` is omitted, the default is `any`.

## Version Policy

Some dependencies should not be upgraded past a known compatible line. Put those
rules in `gradle/libs-version-policy.toml`.

Example:

```toml
[version-levels]
cxf = "minor"
resteasy = "minor"
javax-jaxb = "patch"
```

Policy keys normally match names from the `[versions]` section in
`libs.versions.toml`.

The effective update level is always the stricter value between:

- the command-line `-PupdateLevel=...`
- the entry in `libs-version-policy.toml`

For example, if the command uses `-PupdateLevel=any` but the policy contains:

```toml
cxf = "minor"
```

then `cxf` is still updated only within the current major version.

The task prints active policy rules and effective levels in the log:

```text
Using version policy .../gradle/libs-version-policy.toml:
  cxf: minor

Policy cxf: requested=any, policy=minor, effective=minor
```

## Custom Policy File

You can point the updater to another policy file:

```bash
./gradlew -I gradle/update-libs-versions.gradle updateLibsVersions -PversionPolicy=gradle/my-policy.toml
```

## Pre-release Versions

By default, pre-release versions are ignored. This excludes versions containing
tokens such as `alpha`, `beta`, `rc`, `snapshot`, `milestone`, `preview`, `ea`,
or Maven-style milestone suffixes like `.M1`.

To include them:

```bash
./gradlew -I gradle/update-libs-versions.gradle updateLibsVersions -PincludePreRelease=true
```

## Vulnerability Report

The updater can also print vulnerability information for the current catalog
versions:

```bash
./gradlew -I gradle/update-libs-versions.gradle updateLibsVersions -PreportVulnerabilities=true
```

This mode queries the OSV.dev API for Maven packages using the current
`group:artifact:version` values from `libs.versions.toml`.

The report prints coordinates that have known vulnerabilities, including OSV
IDs, CVE aliases when available, affected version ranges, and short summaries:

```text
Vulnerability report via OSV.dev for 120 Maven coordinates:

io.undertow:undertow-core:2.3.18.Final (undertow-core)
  - GHSA-..., CVE-....: ...
    affected: < 2.4.0.Beta1
```

When `-PreportVulnerabilities=true` is used without `-PwriteVersions=true`, the
task prints only the vulnerability report and does not calculate or print
version updates.

You can combine the vulnerability report with a write run:

```bash
./gradlew -I gradle/update-libs-versions.gradle updateLibsVersions -PreportVulnerabilities=true -PupdateLevel=minor -PwriteVersions=true
```

MvnRepository also shows vulnerability badges in its UI, but those pages are not
used by the script because they may require browser JavaScript/cookies. OSV.dev
is used instead as a script-friendly vulnerability source. Some OSV records may
contain broad generated version lists together with a narrower
`last_known_affected_version_range`; the report uses that range as a sanity
check before reporting a coordinate as vulnerable.

## Dependency Update Report

Use `-PreportUpdates=true` to write a Markdown report for available dependency
updates:

```bash
./gradlew -I gradle/update-libs-versions.gradle updateLibsVersions -PreportUpdates=true -PupdateLevel=patch
```

The default report path is:

```text
build/reports/dependency-updates.md
```

Use `-PupdateReportFile=...` to write it somewhere else:

```bash
./gradlew -I gradle/update-libs-versions.gradle updateLibsVersions -PreportUpdates=true -PupdateLevel=minor -PupdateReportFile=build/reports/dependency-updates.md
```

The report includes `gradle/libs.versions.toml` updates and
`buildSrc/build.gradle` synchronization changes.

## PR Comments In CI

The workflow `.github/workflows/dependency-reports.yml` runs after
`.github/workflows/test-pr.yml` completes successfully for a pull request.
The PR test workflow generates Markdown report artifacts, and the dependency
report workflow downloads those artifacts and posts PR comments. The artifact
also contains `pr-number.txt`, so the reporting workflow does not depend on
`workflow_run.pull_requests` metadata being present. The artifact is downloaded
to the runner temporary directory, not to the repository workspace.

GitHub only triggers `workflow_run` workflows when the workflow file already
exists on the default branch. If `.github/workflows/dependency-reports.yml` is
introduced by the same pull request, it will start working after that workflow
file is merged to the default branch.

If the generated vulnerability Markdown report contains known vulnerabilities,
the workflow creates or updates a single PR comment. The vulnerability comment
is identified by this hidden marker:

```html
<!-- kora-dependency-vulnerability-report -->
```

The same workflow also creates or updates a separate dependency update comment
when updates are available. The update comment is identified by this marker:

```html
<!-- kora-dependency-update-report -->
```

Because the markers are stable, repeated CI runs update the previous report
comments instead of creating new comments each time.

The update report uses `patch` level in CI.

## `buildSrc/build.gradle` Synchronization

`buildSrc` is a separate Gradle build, so it does not automatically share the
main build's version catalog accessors. The updater therefore also scans
`buildSrc/build.gradle` for literal Maven coordinates:

```groovy
implementation 'com.squareup.okhttp3:okhttp:5.3.2'
```

If the same `group:artifact` exists in `libs.versions.toml`, the updater reports
and, with `-PwriteVersions=true`, rewrites `buildSrc/build.gradle` to the
effective catalog version.

Example dry-run output:

```text
buildSrc/build.gradle sync:
  line 6: com.squareup.okhttp3:okhttp (okhttp) 5.3.2 -> 5.4.0
```

If a `buildSrc` dependency has no matching `group:artifact` in the catalog, the
task prints it explicitly and leaves it unchanged:

```text
buildSrc/build.gradle: no libs.versions.toml entry for com.fasterxml.jackson.core:jackson-databind:2.19.2
```

This is intentional: the updater only synchronizes exact coordinates. It does
not silently migrate to a different Maven group/artifact because that can
require source-code changes.

## Inline Versions

The updater supports inline versions like:

```toml
logback-classic = { module = "ch.qos.logback:logback-classic", version = "1.5.18" }
```

They are reported as `inline:<lineNumber>` because they do not have a stable
version key. The log also includes the library alias and Maven coordinates. For
example:

```text
logback-classic (ch.qos.logback:logback-classic, inline:42): 1.5.18 -> 1.5.37
```

If an inline dependency needs a permanent policy rule, prefer moving its version
into `[versions]` first:

```toml
[versions]
logback = "1.5.18"

[libraries]
logback-classic = { module = "ch.qos.logback:logback-classic", version.ref = "logback" }
```

Then add a stable policy entry:

```toml
[version-levels]
logback = "patch"
```

Inline policies are possible with quoted keys, but they are fragile because line
numbers change:

```toml
[version-levels]
"inline:42" = "patch"
```

## Plugins

The updater also checks Gradle plugins declared in the catalog:

```toml
[plugins]
protobuf = { id = "com.google.protobuf", version.ref = "protobuf-plugin" }
```

Plugin metadata is resolved from the Gradle Plugin Portal marker artifact:

```text
<plugin-id>:<plugin-id>.gradle.plugin
```

## Version Selection Notes

The updater reads Maven `maven-metadata.xml` from:

- Maven Central for libraries
- Gradle Plugin Portal for Gradle plugins

It preserves common version families:

- A current version ending in `-jre` only updates to another `-jre` version when
  such versions exist.
- A current version ending in `-android` only updates to another `-android`
  version when such versions exist.
- A plain numeric current version prefers plain numeric candidates.

## Recommended Workflow

1. Run a dry-run first:

   ```bash
   ./gradlew -I gradle/update-libs-versions.gradle updateLibsVersions -PupdateLevel=minor
   ```

2. Review the proposed changes and policy log.

3. Apply:

   ```bash
   ./gradlew -I gradle/update-libs-versions.gradle updateLibsVersions -PupdateLevel=minor -PwriteVersions=true
   ```

4. Run dependency insight or tests for sensitive modules.

5. Avoid broad major updates unless the affected modules are tested.
