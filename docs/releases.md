# Preview and Production

## Pull requests

Android CI builds the exact PR head, not a synthetic merge commit. Branch protection requires
an up-to-date branch; update it when main advances. New PRs and updated commits build unless
only documentation/reference pins changed. workflow_dispatch can force an APK or run device
tests. A maintainer can also add the `device-tests` PR label to request emulator
verification before merging the bootstrap PR; remove it once that verification is complete. Earlier runs on the same PR are cancelled. Main pushes run the lightweight Foundation
check without republishing or repeating the Android build.

The APK artifact contains unsigned Preview, temporary-key Debug, and run/source/checksum
metadata. It uses one CI workflow's run number for Preview's versionCode. Debug uses .debug;
Preview .preview; Production the base ID. Same signing key plus nondecreasing versionCode is
required for an in-place Android update. CI debug keys are not a persistent signing identity.

After bootstrap is merged, a separate workflow_run publisher executes only trusted-main code.
It accepts same-repository successful current-PR runs, validates run/PR/head identity, checks
fixed archive entries and digests, inspects the package ID/version, and signs without Gradle
or executing anything from the PR artifact. Cross-host artifact redirects strip GitHub auth.
The preview environment owns the Preview key; Production keys are not visible to PR builds.
Forks get build artifacts but are not automatically signed with the maintainer key.

A preview-pr-N pre-release and a single bot PR comment link to the current signed APK. Stale
head builds are rejected, and same-head older run numbers cannot downgrade the published APK.
Closing/merging the PR removes only its generated preview release/tag through a
trusted-default-branch pull_request_target cleanup workflow that checks out no code. No recursive release
workflow is triggered by these tags. Artifacts expire after 14 days.

## Bootstrap limitation

workflow_run publisher code must exist on main. The bootstrap PR therefore provides the Dev
APK as an Actions artifact. Stable Preview signing/direct links begin after that PR is merged
and the preview environment secrets are configured. Missing secrets are reported as pending
setup; they do not silently produce a different Preview signature.

## Production

The Production release workflow is manual and accepts main only. Supply version_name and a
new version_code. By default it produces a signed artifact only; explicitly enable publish
to create a public release. Build/check jobs have no signing secrets. The signing job is in
the production environment, checks artifact identity and uses the distinct Production key.
Publication refuses existing versions and non-increasing recorded Production version codes.
Do not rotate either channel's key to repair an ordinary CI failure. Preserve local backups.

See github-setup.md for one-time administration and signing setup. No personal access token
is needed by normal builds or publishers; they use narrowly scoped GITHUB_TOKEN permissions.
