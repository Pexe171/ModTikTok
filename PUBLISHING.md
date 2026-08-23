# Publishing TikTok Chaos

The repository contains a manual GitHub Actions workflow that builds, tests, and uploads a new JAR to CurseForge without storing the API token in source control.

## One-time GitHub configuration

1. Create the CurseForge project and copy its numeric Project ID from the project URL or overview page.
2. Revoke any API token that has appeared in chat, screenshots, logs, commits, or other untrusted locations.
3. Generate a new token from the CurseForge API Tokens page.
4. In the GitHub repository, open **Settings > Secrets and variables > Actions**.
5. Under **Secrets**, create `CURSEFORGE_API_TOKEN` with the new token as its value.
6. Under **Variables**, create `CURSEFORGE_PROJECT_ID` with the numeric project ID as its value.

Never put the token in `gradle.properties`, a workflow file, a script, a commit, or a chat message.

## Publishing an update

### From the GitHub interface

1. Change `mod_version` in `gradle.properties`.
2. Update `CHANGELOG.md` with the new version.
3. Commit and push the changes to GitHub.
4. Open **Actions > Publish to CurseForge > Run workflow**.
5. Select `beta`, `release`, or `alpha`, then run it.

### From a version tag

After committing and pushing the version, create a matching tag:

```bash
git tag v1.2.0
git push origin v1.2.0
```

Tags matching `v*` automatically run the same workflow with the `beta` channel. The workflow refuses a tag whose version does not match `mod_version` in `gradle.properties`.

The workflow uses Java 21, runs the automated tests, creates the shaded distributable JAR, preserves a copy as a GitHub Actions artifact, and uploads it through the official CurseForge Upload API. CurseForge moderation still applies to uploaded files.

## Required values

| Name | GitHub storage | Purpose |
| --- | --- | --- |
| `CURSEFORGE_API_TOKEN` | Actions secret | Authenticates the upload without exposing the token |
| `CURSEFORGE_PROJECT_ID` | Actions variable | Selects the CurseForge project receiving the JAR |
