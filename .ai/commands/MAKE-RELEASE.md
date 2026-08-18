# Make release

Prepare a release. Use the version requested by the user. If no version is specified, increment the patch component of the current `versionName`.

1. Run `just previous-version-tag` once and keep its output as the previous version tag.
2. Analyze `git diff <previous-version-tag>..HEAD` to understand the user-visible changes.
3. Write a concise, human-readable release entry describing those changes. Generate the description yourself; do not dump commit subjects, filenames, or raw diff output.
4. Add the release entry to the top of `/CHANGELOG.md` under a heading containing the new version.
5. In `/app/build.gradle`, increment `versionCode` by one and set `versionName` to the release version determined above.
6. Generate the release APK:

```sh
just build
```

7. After the build succeeds, stage `/CHANGELOG.md` and `/app/build.gradle`, then commit the release changes:

```sh
git add CHANGELOG.md app/build.gradle
git commit -m "Release <new-version>"
```

8. Create the new version tag on that release commit:

```sh
git tag <new-version>
```

9. Return the generated changelog and the APK path printed by `just build`.
