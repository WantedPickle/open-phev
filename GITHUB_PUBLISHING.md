# Publish Open PHEV on GitHub

Repository: [WantedPickle/open-phev](https://github.com/WantedPickle/open-phev). Description: **Independent open-source Android Wi-Fi remote for MY18 Outlander PHEV vehicles.** Suggested topics: `android`, `outlander-phev`, `electric-vehicle`, `wifi`, `java`. The steps below document the initial publication and future release process.

## Repository

1. Create an empty public repository in your GitHub account. The source already includes README, GPL licence and .gitignore; do not generate competing copies.
2. Extract `open-phev-0.4.1-source.zip`. Put the contents of its `open-phev` directory at the repository root, including `.github` and `.gitignore`. GitHub Desktop can add this local folder, create its initial commit and publish it. Do not upload the surrounding development workspace.
3. Confirm README renders the icon and the Protocol and schedule tests workflow passes. The workflow is prepared but has not yet run on GitHub. It runs tests only and has no signing credentials or publishing permission.

## Downloadable release

Create a draft release from the exact source commit, tag **v0.4.1-alpha**, title **Open PHEV 0.4.1-alpha**. Paste RELEASE_NOTES.md into its description and mark it as a prerelease. Attach these three supplied files:

- `open-phev-0.4.1-alpha.apk` — installable Android app.
- `open-phev-0.4.1-source.zip` — corresponding GPL source including icon resources, tests and build script.
- `SHA256SUMS-0.4.1.txt` — checksums of those exact files.

Review the files and release notes, then publish. Users install the APK from the release's Assets list; GitHub's automatically generated source archives are not Android installers. See [GitHub's release instructions](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository).

## Signing and future updates

The supplied APK retains the existing alpha certificate so it can update current installations. Its certificate SHA-256 is:

`5490864de265bf61c0de67a97daca986f75b1b7994dff2b01d4ac5492a981c31`

The private key is deliberately absent from the source and release packages. Keep the existing keystore backed up privately and securely; it is needed for compatible future updates. The local alpha build uses a development key password. Before public release, establish private release-key custody and protect the existing key; a new unrelated signing key cannot transparently update existing installations. Do not publish keystores, key passwords or private signing credentials in GitHub.

Build future APKs from a clean source tree using the documented toolchain, run tests, verify the certificate and publish matching source alongside the APK. Regenerate checksums after every rebuild; never reuse a checksum from a different APK. Add screenshots of the real new UI after phone verification rather than presenting mockups as screenshots.

## Release scope

Describe MY18 support and Android 12+ accurately. Evidence is from one 2018 car and a Galaxy S23 on Android 16; do not claim every Outlander/phone is supported. Keep the unresolved connection rejection, electrical-windscreen limitation and scheduled-execution test status visible. Do not include the original proprietary APK, decompiled research, Wi-Fi registration cards or raw owner logs.
