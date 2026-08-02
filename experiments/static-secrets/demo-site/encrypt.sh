#!/usr/bin/env bash
  set -euo pipefail
  pushd "$(dirname "$0")"

  echo "==> Encrypting index.ssec (recipients: secret, sprinkles)"
  node ../tools/encrypt.js index.md --out index.ssec \
      --recipient "You:secret" \
      --recipient "Chef Bignez:sprinkles"

  echo "==> Encrypting recipe.ssec (recipients: sprinkles, custard)"
  node ../tools/encrypt.js recipe.md --out recipe.ssec \
      --recipient "Chef Bignez:sprinkles" \
      --recipient "Auditor Crumb:custard"

  echo "==> Encrypting roster.ssec (recipients: admin, secret)"
  node ../tools/encrypt.js roster.md --out roster.ssec \
      --recipient "The Boss:admin" \
      --recipient "You:secret"

  echo "==> Encrypting vault.ssec (recipient: sprinkles ONLY)"
  node ../tools/encrypt.js vault.md --out vault.ssec \
      --recipient "Chef Bignez:sprinkles"

  echo
  echo "==> Sanity checks: envelope metadata"
  node ../tools/decrypt.js index.ssec  --info
  node ../tools/decrypt.js recipe.ssec --info
  node ../tools/decrypt.js roster.ssec --info
  node ../tools/decrypt.js vault.ssec  --info

  echo
  echo "==> Decryption demo: the master 'secret' opens the front door"
  node ../tools/decrypt.js index.ssec -p secret

  echo
  echo "==> Multi-user demo: 'sprinkles' opens index, recipe, AND vault"
  node ../tools/decrypt.js index.ssec  -p sprinkles
  node ../tools/decrypt.js recipe.ssec -p sprinkles
  node ../tools/decrypt.js vault.ssec  -p sprinkles

  echo
  echo "==> 'custard' opens the recipe but should FAIL on the vault"
  node ../tools/decrypt.js recipe.ssec -p custard
  if node ../tools/decrypt.js vault.ssec -p custard 2>/dev/null; then
      echo "!! Unexpected: custard opened the vault (this should not happen)"
  else
      echo "   Good: 'custard' correctly denied at the vault. No donut for Crumb."
  fi

  echo
  echo "==> 'admin' opens the roster but NOT the vault"
  node ../tools/decrypt.js roster.ssec -p admin
  if node ../tools/decrypt.js vault.ssec -p admin 2>/dev/null; then
      echo "!! Unexpected: admin opened the vault"
  else
      echo "   Good: even 'admin' can't raid the vault. Bignez sleeps soundly."
  fi

  echo
  echo "==> All demos complete. Have a donut. 🍩"
  popd