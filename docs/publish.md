---
documents: ../scripts/publish.sh
specifies: ../scripts/publish.sh
---

# Create the key alias/maven-central if it does not exist

```shell
aws kms create-alias --alias-name alias/maven-central --target-key-id $(aws kms create-key --query KeyMetadata.KeyId --output text)
```

# Read a secret from console in and return the AWS encrypted secret

```shell
read_secret_aws() {
  read -s -p "Enter secret: " secret
  echo
  echo -n "$secret" | aws kms encrypt --key-id alias/maven-central --plaintext fileb:///dev/stdin --output text --query CiphertextBlob
}
```

# Use AWS to decrypt $1 and return it as a string

```bash
decrypt_aws() {
  aws kms decrypt --ciphertext-blob fileb://<(echo "$1" | base64 --decode) --output text --query Plaintext | base64 --decode
}
```

# Example usage to query Sonatype OSSRH staging repositories

```bash
curl -u `decrypt_aws 'AQICAHidpAxjUnec2+y6zMst5ZAtSqAHG3cILsI2tm2DVIIvlAFZUxBH2ZJcW+Bzc/rHoJh/AAAAhzCBhAYJKoZIhvcNAQcGoHcwdQIBADBwBgkqhkiG9w0BBwEwHgYJYIZIAWUDBAEuMBEEDHPtV625FyskyHYbqQIBEIBDtT8Ic17uo9CTG0vNOPAsocpEv35T4sDmJMS4aMmfrgEX0l701yjHIpEx4rzzrUsVDtwkS75BvRx9UxMrwJs+33tkoA=='` \
  'https://ossrh-staging-api.central.sonatype.com/manual/search/repositories?ip=any&profile_id=com.cognotik' | jq
```

# Example usage to promote a staging repository in Sonatype OSSRH

```bash
curl -u `decrypt_aws 'AQICAHidpAxjUnec2+y6zMst5ZAtSqAHG3cILsI2tm2DVIIvlAFZUxBH2ZJcW+Bzc/rHoJh/AAAAhzCBhAYJKoZIhvcNAQcGoHcwdQIBADBwBgkqhkiG9w0BBwEwHgYJYIZIAWUDBAEuMBEEDHPtV625FyskyHYbqQIBEIBDtT8Ic17uo9CTG0vNOPAsocpEv35T4sDmJMS4aMmfrgEX0l701yjHIpEx4rzzrUsVDtwkS75BvRx9UxMrwJs+33tkoA=='` -X POST \
  'https://ossrh-staging-api.central.sonatype.com/manual/upload/repository/HyHqQM/any/com.cognotik--9dc094fb-8a67-483c-a07c-294a2e985ca6' \
  -H 'Content-Type: application/json' -d '{"data": {"description": "Promote from CI build"}}'

```
