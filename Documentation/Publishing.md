# Publishing

Artifacts are signed and published to Maven Central. You can see the latest version at the maven central website https://search.maven.org/search?q=g:org.datatransferproject.

The publication process is mostly automated through Gradle and Github Actions; when you merge a commit to the `master` branch, a new version will be created in the Maven Central staging repository. You can then release the packages to the public Central Repository following the steps in the [Sonatype documentation](https://central.sonatype.org/publish/release/). \
We are on the legacy host so make sure to use https://oss.sonatype.org/. Only attempt to Close the staging repository once the Github action has finished.

## Automated Process

DTP's publication automation is done through a Github action in `.github/workflows/release.yml`. \
The action contains two jobs; one to bump the version number of the DTP packages and publish a git tag, and one to build, sign, and upload the artifacts to Maven Central.

### Semantic Versioning and Conventional Commits

DTP uses [Semantic Versioning](https://semver.org/) for published packages. We also enforce [Conventional Commits](https://conventionalcommits.org/) on the `master` branch through the `.github/workflows/commitlint.yml` Github action, which lets us automatically calculate version numbers.

Commitizen is a tool that supports automatically incrementing SemVer version numbers based on git commit history. Our usage of Commitizen is configured in `.cz.toml`, and uses the `commitizen-tools/commitizen-action` Github action to automatically bump the package version number and to tag the new version in git.

### Automated Publishing

Using the `maven` and `signing` gradle plugins adds the `sign` and `uploadArchives` targets, which together automate the process of publishing to Maven Central based on our configuration in `build.gradle`. \
The `release` job in the Github action runs these targets, using environment variables to inject secret values like GPG keys and the OSSRH User Token required for publishing.

### Release to the public repository

The only part of the process that isn't automated is promoting packages from the staging repository to the public repository in Maven Central.

This process is documented in [Sonatype's documentation](https://central.sonatype.org/publish/release/). The summary is:
1. Log in to https://oss.sonatype.org/
2. Go to 'Staging Repositories' under 'Build Promotion' in the nav panel
3. Select the repository (version) to promote
4. 'Close' the repository to freeze it - only do this once the Github action has finished
5. 'Release' the repository, which promotes the packages to the public repository

## Manual Process

If for some reason you need to publish manually, the steps are detailed below.

### 1. Setting properties
 First you must set the necessary properties in [gradle.properties](../gradle.properties). These are:
 - `projectVersion` - this is the new version you wish to publish.
 - `ossrhUsername` & `ossrhPassword` - These are your Sonatype [User Token](https://central.sonatype.org/publish/generate-token/#generate-a-token-on-ossrh-sonatype-nexus-repository-manager-servers) credentials. Your account must have been granted publishing permissions. Permissions are managed manually by Sonatype - see [Sonatype's documentation](https://central.sonatype.org/register/legacy/) for details.
 - `signing.keyId` - The GPG key being used for signing the artifacts. (More information about setting up GPG keys can be found [here](https://central.sonatype.org/publish/requirements/gpg/))
 - `signing.password` - The password for that GPG private key.
 - `signing.secretKeyRingFile` - The path to the file containing the GPG private key.

### 2. Sanity check
Make sure that the artifacts are building and running correctly. For example run the worker in the Docker container, see [Running Locally](RunningLocally.md) for instructions.

### 3. Sign and upload
To sign and publish the artifacts run the following Gradle command:

```
./gradlew sign uploadArchives --exclude-task :client-rest:uploadArchives
```

We exclude the client-rest archives as these are not a Java package.

### 4. Create github release and tag

1. Go to [/releases](https://github.com/google/data-transfer-project/releases);
2. Click 'Draft a new release';
3. Fill in the title and create a tag (the tag must have a `v` prefix, the title mustn't);
4. Click 'Generate release notes';
5. Publish release.

### 5. Update the project version

Go to `properties.gradle` and update the version, create a PR.
