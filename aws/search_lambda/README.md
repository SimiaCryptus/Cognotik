# Search Lambda Proxy

A AWS Lambda function that acts as a secure proxy for the Google Custom Search API. It uses AWS KMS to encrypt and
protect API credentials, and exposes a simple REST endpoint via API Gateway.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Setup & Deployment](#setup--deployment)
- [API Usage](#api-usage)
- [Environment Variables](#environment-variables)
- [Security](#security)

---

## Overview

This Lambda function:

- Accepts search queries via HTTP GET requests
- Decrypts KMS-encrypted Google API credentials at runtime
- Proxies requests to the Google Custom Search API
- Returns results to the caller with CORS headers enabled

---

## Architecture

```
Client → API Gateway → Lambda Function → Google Custom Search API
                          ↑
                       AWS KMS
                  (credential decryption)
```

---

## Prerequisites

- [AWS CLI](https://aws.amazon.com/cli/) configured with appropriate permissions
- [Terraform](https://www.terraform.io/) installed
- A [Google Custom Search Engine](https://programmablesearchengine.google.com/) with:
    - An API Key
    - A Search Engine ID (cx)
- Python 3.x (for Lambda runtime)

---

## Setup & Deployment

### 1. Package the Lambda Function

```bash
cd lambda
zip lambda_function.zip lambda_function.py
```

### 2. Deploy Infrastructure with Terraform

```bash
cd ../terraform
terraform init
terraform plan
terraform apply
```

### 3. Retrieve the KMS Key ID

```bash
KMS_KEY_ID=$(terraform output -raw kms_key_id)
```

### 4. Encrypt Your Google API Credentials

```bash
./encrypt_credentials.sh
```

This script will use the KMS key to encrypt your `GOOGLE_API_KEY` and `SEARCH_ENGINE_ID`.

### 5. Update Terraform Variables

Add the encrypted values to `terraform.tfvars`:

```hcl
encrypted_google_api_key  = "<base64-encrypted-value>"
encrypted_search_engine_id = "<base64-encrypted-value>"
```

### 6. Re-apply Terraform

```bash
terraform apply
```

### 7. Get the API Endpoint

```bash
terraform output api_endpoint
```

Use this endpoint in your application configuration (see `application.yml`).

---

## API Usage

### Endpoint

```
GET https://<api-id>.execute-api.<region>.amazonaws.com/search
```

### Query Parameters

| Parameter | Required | Description                                        |
|-----------|----------|----------------------------------------------------|
| `q`       | ✅ Yes    | The search query string                            |
| `num`     | ❌ No     | Number of results to return (default: 10, max: 10) |
| `start`   | ❌ No     | Starting index for pagination (default: 1)         |

### Example Request

```bash
curl "https://<api-id>.execute-api.us-east-1.amazonaws.com/search?q=aws+lambda&num=5"
```

### Example Response

```json
{
  "kind": "customsearch#search",
  "items": [
    {
      "title": "AWS Lambda – Serverless Compute",
      "link": "https://aws.amazon.com/lambda/",
      "snippet": "AWS Lambda lets you run code without provisioning or managing servers..."
    }
  ]
}
```

### Error Responses

| Status Code | Reason                              |
|-------------|-------------------------------------|
| `400`       | Missing required `q` parameter      |
| `500`       | Internal Lambda or decryption error |
| `4xx/5xx`   | Errors returned from Google API     |

---

## Environment Variables

These are set automatically by Terraform after encryption:

| Variable                     | Description                                    |
|------------------------------|------------------------------------------------|
| `ENCRYPTED_GOOGLE_API_KEY`   | KMS-encrypted, base64-encoded Google API key   |
| `ENCRYPTED_SEARCH_ENGINE_ID` | KMS-encrypted, base64-encoded Search Engine ID |

---

## Application Configuration

In your Spring Boot or other application, configure the proxy endpoint via `application.yml`:

```yaml
google:
  search:
    proxy:
      endpoint: ${GOOGLE_SEARCH_PROXY_ENDPOINT:https://your-api-id.execute-api.us-east-1.amazonaws.com/search}
```

The endpoint can be overridden at runtime using the `GOOGLE_SEARCH_PROXY_ENDPOINT` environment variable.

---

## Security

- **KMS Encryption**: API credentials are never stored in plaintext. They are encrypted using AWS KMS and decrypted only
  at Lambda invocation time.
- **IAM Roles**: The Lambda execution role should have the minimum required KMS `Decrypt` permission scoped to the
  specific key.
- **CORS**: Currently set to `*` (allow all origins). Restrict `Access-Control-Allow-Origin` in production to your
  specific domain.
- **No credential logging**: Decrypted credentials are used in-memory only and never logged.