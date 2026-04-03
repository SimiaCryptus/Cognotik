# Patreon Auth Lambda

Stateless Patreon OAuth + subscription verification service.  
No database required – subscription state is encoded in a short-lived signed JWT.

## Endpoints

| Method | Path           | Description                                            |
|--------|----------------|--------------------------------------------------------|
| GET    | `/auth`        | Redirect user to Patreon OAuth consent screen          |
| GET    | `/callback`    | Handle OAuth callback, issue JWT, redirect to frontend |
| GET    | `/verify`      | Validate a JWT and return subscription status (cached) |
| GET    | `/verify/live` | Real-time membership check via Patreon API             |

---

## Flow

```
User → GET /auth
         │
         └─► Patreon consent screen
                    │
                    └─► GET /callback?code=…
                              │
                              ├─ exchange code for Patreon tokens
                              ├─ fetch identity + memberships
                              ├─ issue signed JWT (24 h)
                              └─► redirect to FRONTEND_REDIRECT_URI?token=<jwt>

Your app → GET /verify
  Authorization: Bearer <jwt>
  ← { valid, patron, sub, email, name, exp }

Your app → GET /verify/live
  Authorization: Bearer <jwt>
  ← { patron, status, entitled_cents }   (real-time Patreon API call)
```

## JWT payload

```json
{
  "sub": "<patreon-user-id>",
  "email": "user@example.com",
  "name": "Jane Doe",
  "patron": true,
  "iat": 1700000000,
  "exp": 1700086400
}
```

## Environment variables

| Variable                          | Encrypted? | Description                             |
|-----------------------------------|------------|-----------------------------------------|
| `PATREON_CLIENT_ID`               | No         | Patreon app client ID                   |
| `PATREON_REDIRECT_URI`            | No         | Must match Patreon app settings         |
| `PATREON_CAMPAIGN_ID`             | No         | Optional – scope checks to one campaign |
| `FRONTEND_REDIRECT_URI`           | No         | Where to send the user after login      |
| `ALLOWED_ORIGINS`                 | No         | CORS origins (comma-separated or `*`)   |
| `ENCRYPTED_PATREON_CLIENT_SECRET` | **Yes**    | KMS-encrypted client secret             |
| `ENCRYPTED_PATREON_CREATOR_TOKEN` | **Yes**    | KMS-encrypted creator access token      |
| `ENCRYPTED_JWT_SECRET`            | **Yes**    | KMS-encrypted JWT signing secret        |

## First-time setup

```bash
# 1. Deploy infrastructure (creates KMS key)
cd terraform && terraform init && terraform apply

# 2. Grab the KMS key ID
KMS_KEY_ID=$(terraform output -raw kms_key_id)

# 3. Encrypt your secrets
./scripts/encrypt_secret.sh $KMS_KEY_ID 'your-patreon-client-secret'
./scripts/encrypt_secret.sh $KMS_KEY_ID 'your-patreon-creator-token'
./scripts/encrypt_secret.sh $KMS_KEY_ID 'your-random-jwt-secret-min-32-chars'

# 4. Paste the base64 outputs into terraform.tfvars, then:
./scripts/deploy.sh
```

## Calling /verify from your app

```js
// Any backend or trusted client
const res = await fetch(`${PATREON_AUTH_ENDPOINT}/verify`, {
  headers: { Authorization: `Bearer ${userJwt}` }
});
const { valid, patron } = await res.json();
if (!valid || !patron) throw new Error('Not a subscriber');
```
