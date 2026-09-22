## Signed user identities

Configure a shared secret on every server that exchanges identities:

```bash
  export COGNOTIK_USER_SIGNING_KEY="$(openssl rand -hex 32)"
  # or: -Dcognotik.user.signingKey=...
```

Sender:

```kotlin
  val token = user.toSignedToken(ttlSeconds = 60)
  request.setHeader("X-Cognotik-User", token)
```

Receiver:

```kotlin
  val user = User.fromSignedToken(request.getHeader("X-Cognotik-User"))
    ?: throw SecurityException("Invalid or expired user token")
```

Field-level check (e.g. a JSON body that already carries `signature`):

```kotlin
  require(user.isSignatureValid(incoming.signature)) { "Signature mismatch" }
```

Log a warning at startup when `User.isUsingDefaultSigningKey` is true.