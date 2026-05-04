---
documents: ../scripts/publish.sh
specifies: ../scripts/publish.sh
---

# Secret Setup

## Create the key alias/maven-central if it does not exist

```shell
aws kms create-alias --alias-name alias/maven-central --target-key-id $(aws kms create-key --query KeyMetadata.KeyId --output text)
```

## Read a secret from console in and return the AWS encrypted secret

```shell
read_secret_aws() {
  read -s -p "Enter secret: " secret
  echo
  echo -n "$secret" | aws kms encrypt --key-id alias/maven-central --plaintext fileb:///dev/stdin --output text --query CiphertextBlob
}
```

## Use AWS to decrypt $1 and return it as a string

```bash
decrypt_aws() {
  aws kms decrypt --ciphertext-blob fileb://<(echo "$1" | base64 --decode) --output text --query Plaintext | base64 --decode
}
```

The MVN_CENTRAL_KEY environment variable should be set to the AWS encrypted secret, which can be obtained by running the `read_secret_aws` function:

```
MVN_CENTRAL_KEY='AQICAHidpAxjUnec2+y6zMst5ZAtSqAHG3cILsI2tm2DVIIvlAFZUxBH2ZJcW+Bzc/rHoJh/AAAAhzCBhAYJKoZIhvcNAQcGoHcwdQIBADBwBgkqhkiG9w0BBwEwHgYJYIZIAWUDBAEuMBEEDHPtV625FyskyHYbqQIBEIBDtT8Ic17uo9CTG0vNOPAsocpEv35T4sDmJMS4aMmfrgEX0l701yjHIpEx4rzzrUsVDtwkS75BvRx9UxMrwJs+33tkoA=='
```

# Publishing to Maven Central

Review existing repos, ensuring a clean slate before each publish. You can use the following shell function to list staging repositories for the com.cognotik profile:

```bash
list_repos() {
curl -u `decrypt_aws $MVN_CENTRAL_KEY` \
  'https://ossrh-staging-api.central.sonatype.com/manual/search/repositories?ip=any&profile_id=com.cognotik' | jq
}
```
or
```bash
list_repos_keys() {
  list_repos | jq -r '.repositories[] | .key'
}
```

To clean up failed staging repositories, you can use the following shell function that takes in the guid of the staging repository:

```bash
close_staging_repo() {
    local repo_key="$1"
    curl -u `decrypt_aws $MVN_CENTRAL_KEY` -X DELETE \
        "https://ossrh-staging-api.central.sonatype.com/manual/drop/repository/${repo_key}" \
        -H 'Content-Type: application/json' -d '{"data": {"description": "Close failed staging repo from CI build"}}'
}
```
or
```bash
close_all_repos() {
  for repo_key in $(list_repos_keys); do
    close_staging_repo "$repo_key"
  done
}
```

## Deploy Build

```shell
./gradlew build publish
```

Then use the `list_repos` function to find the guid of the staging repository that was just created, and promote it using the `promote_staging_repo` function:

```bash
promote_staging_repo() {
  local repo_key="$1"
  curl -u `decrypt_aws $MVN_CENTRAL_KEY` -X POST \
    "https://ossrh-staging-api.central.sonatype.com/manual/upload/repository/${repo_key}" \
    -H 'Content-Type: application/json' -d '{"data": {"description": "Promote from CI build"}}'
}
```



All functions:
```
read_secret_aws() {
  read -s -p "Enter secret: " secret
  echo
  echo -n "$secret" | aws kms encrypt --key-id alias/maven-central --plaintext fileb:///dev/stdin --output text --query CiphertextBlob
}
decrypt_aws() {
  aws kms decrypt --ciphertext-blob fileb://<(echo "$1" | base64 --decode) --output text --query Plaintext | base64 --decode
}
list_repos() {
curl -u `decrypt_aws $MVN_CENTRAL_KEY` \
  'https://ossrh-staging-api.central.sonatype.com/manual/search/repositories?ip=any&profile_id=com.cognotik' | jq
}
list_repos_keys() {
  list_repos | jq -r '.repositories[] | .key'
}
close_staging_repo() {
    local repo_key="$1"
    curl -u `decrypt_aws $MVN_CENTRAL_KEY` -X DELETE \
        "https://ossrh-staging-api.central.sonatype.com/manual/drop/repository/${repo_key}" \
        -H 'Content-Type: application/json' -d '{"data": {"description": "Close failed staging repo from CI build"}}'
}
close_all_repos() {
  for repo_key in $(list_repos_keys); do
    close_staging_repo "$repo_key"
  done
}
promote_staging_repo() {
  local repo_key="$1"
  curl -u `decrypt_aws $MVN_CENTRAL_KEY` -X POST \
    "https://ossrh-staging-api.central.sonatype.com/manual/upload/repository/${repo_key}" \
    -H 'Content-Type: application/json' -d '{"data": {"description": "Promote from CI build"}}'
}
```

Then deploy and promote the build with:
```
MVN_CENTRAL_KEY='AQICAHidpAxjUnec2+y6zMst5ZAtSqAHG3cILsI2tm2DVIIvlAFZUxBH2ZJcW+Bzc/rHoJh/AAAAhzCBhAYJKoZIhvcNAQcGoHcwdQIBADBwBgkqhkiG9w0BBwEwHgYJYIZIAWUDBAEuMBEEDHPtV625FyskyHYbqQIBEIBDtT8Ic17uo9CTG0vNOPAsocpEv35T4sDmJMS4aMmfrgEX0l701yjHIpEx4rzzrUsVDtwkS75BvRx9UxMrwJs+33tkoA=='
git stash
close_all_repos && ./gradlew build publish && list_repos_keys
git stash pop
```