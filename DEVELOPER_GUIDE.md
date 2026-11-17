# Developer Guide

- [Building](#building)
- [Running a Local Cluster](#running-a-local-cluster)
- [Upgrading the OpenSearch Version](#upgrading-the-opensearch-version)
- [Releasing](#releasing)

See also [CONTRIBUTING.md](CONTRIBUTING.md) for more details on the development workflow, including IDE setup.

## Building

To build the plugin and run all checks (unit tests, integration tests and code style):

```
./gradlew clean build
```

## Running a Local Cluster

To start a local OpenSearch cluster with your build of the plugin installed:

```
./gradlew run
```

To try the released plugin instead, use the official [OpenSearch Docker image](https://hub.docker.com/r/opensearchproject/opensearch), which includes the Learning to Rank plugin since OpenSearch 2.19.

## Upgrading the OpenSearch Version

Version bumps are automated: a pull request updates the OpenSearch version (`opensearch.version` in `build.gradle`), from which the plugin version is derived. When reviewing such a pull request, build and test as described under [Building](#building), and fix any incompatibilities with the new OpenSearch version.

Release notes for each version are added to the [`release-notes`](release-notes) directory by an automated `[AUTO] Add release notes for <version>` pull request from the OpenSearch CI bot.

## Releasing

Since OpenSearch 2.19, this plugin is released as part of the OpenSearch release train and ships in the default OpenSearch distribution. Each OpenSearch release `X.Y.Z` has a matching tag `X.Y.Z.0` in this repository, which the [opensearch-build](https://github.com/opensearch-project/opensearch-build) release process builds into the distribution. Running `./gradlew build` as described under [Building](#building) produces the same plugin zip locally, in `build/distributions`.

Each release line has its own branch (for example `3.9`, `2.19`). To get a change merged to `main` onto a release branch, add a `backport <branch>` label to the pull request; the [Backport](.github/workflows/backport.yml) workflow then opens the backport pull request automatically.

Releases before 2.19 were published as plugin zips on the [GitHub releases page](https://github.com/opensearch-project/opensearch-learning-to-rank-base/releases).

## Integration tests with Security

Run the YAML REST suite against a local cluster with the Security plugin installed:

```sh
./gradlew integTest -Dsecurity.enabled=true --max-workers=2
```

This mode uses HTTPS and the demo `admin`/`admin` user for both requests and cleanup.
It also runs `SecurityEnabledIT`, which checks the authenticated identity and verifies
that invalid credentials are rejected. The in-process Java integration tests and the
two-cluster snapshot suite are not included in this mode; they continue to run in the
ordinary integration test tasks. This is compatibility coverage, not a restricted-user
permissions suite.

The Security plugin version follows the OpenSearch version by default. Override it
with `-Dsecurity.version=<version>` when testing a different compatible build.
The `Integration tests with Security` workflow runs this command for pull requests
and publishes test reports and cluster logs, including on failures.

The node certificate, private key, and CA in `src/test/resources/security/` are public
test fixtures copied from the Security repository's `bwc-test/src/test/resources/security/`
at commit `35f0035312b48090d666247fd46d1bef085a4d4b`. The node certificate expires in
February 2034. These fixtures and credentials are only for local test clusters.
