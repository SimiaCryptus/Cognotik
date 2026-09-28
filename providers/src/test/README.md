# Provider tests

`CoreProvidersRegistrationTest` is hermetic and always runs.

`CoreProvidersLiveTest` makes real network calls and only runs when credentials are
supplied. Credential resolution order:

1. `-Dcognotik.test.apiKeys=/path/to/keys.json`
2. `COGNOTIK_TEST_API_KEYS=/path/to/keys.json`
3. `resources/test-api-keys.json.disable` (gitignored)
4. `./test-api-keys.json` or `~/.cognotik/test-api-keys.json`

To enable:

```bash
  cp providers/src/test/resources/test-api-keys.json.example \
     providers/src/test/resources/test-api-keys.json
  # edit, set "enabled": true and a real "apiKey" for the providers you want to exercise
```

When no file is found the test factories emit a single passing placeholder test and
log why they were skipped, so CI stays green without credentials.

## Required build configuration

If the `providers` module does not already have a test source set, add:

```kotlin
  dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.5.6")
  }
  tasks.test {
    useJUnitPlatform()
    testLogging { showStandardStreams = true }
  }
```