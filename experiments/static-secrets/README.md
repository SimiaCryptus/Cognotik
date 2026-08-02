# Static Secrets

Ordinarily, "private content on the web" means a server that checks who you
are, decides what you're allowed to see, and hands you the plaintext. The
server is the gatekeeper — and, inconveniently, the server also _sees
everything_. It has your content, and often your keys, and you're trusting
it to behave.

Static Secrets flips that arrangement inside out. You encrypt your content
ahead of time into an opaque blob — a `.ssec` file — and publish it
anywhere that will host a plain file: GitHub Pages, an S3 bucket, a cheap
web host, a USB stick. That host never sees anything but scrambled bytes;
it can't read your content because it never holds the key.

The app itself is the clever part. It runs entirely in _your_ browser,
fetches the encrypted blob, and decrypts it locally using a password you
hold. Only after decryption does it discover what the content even _is_ —
a web page, a Markdown note, or a downloadable file — and render it
accordingly. The gatekeeper, in other words, moves from the server to your
own machine; the host is demoted to a dumb pipe.

## How it feels to use

The experience is deliberately quiet. You point the app at a blob (the
address lives right in the page's URL, so links and bookmarks work the way
you'd expect), and one of three things happens:

1. **You already have the key.** The app keeps a local _keychain_ of
   passwords and quietly tries each one against the blob. If one fits, the
   content simply appears — no prompt, no ceremony. It even promotes the
   successful key to the front of the list, so the next matching blob
   decrypts a little faster.
2. **You don't have the key yet.** The app asks for a password, adds it to
   your keychain, and tries again. Enter the right one and you're in.
3. **It's the wrong key.** Here's the elegant bit: the app doesn't _guess_
   whether decryption worked by squinting at the result. The encryption
   scheme (AES-GCM) carries a built-in authentication tag, so a wrong key
   fails cleanly and unambiguously. Right key or no content — there's no
   fuzzy middle ground.

Decrypted content is rendered thoughtfully. Web pages run inside a
sandboxed frame so they can't reach back and rummage through your keychain;
Markdown is rendered to formatted text; anything else is offered as a
download with its original filename. It behaves, in short, like a very
small, very private browser.

## A little background

The building blocks here are not new; that's rather the point. Browsers
have shipped a capable cryptography toolkit (the Web Crypto API) for years,
Progressive Web Apps can be installed and run offline, and static hosting
has become nearly free and effectively infinite. What's interesting is how
little glue it takes to combine them into something that genuinely changes
the trust model. Static Secrets is that glue — a static site plus a small
command-line tool for encrypting files before you publish them.

## Why I find it interesting

A few things kept me building:

- **The trust boundary is honest.** All the decryption happens on the
  client, so "trust the host" simply isn't part of the story. You can
  publish through a service you don't trust at all and lose nothing but
  bandwidth.
- **The content type is a secret too.** Whether a blob is a web page, a
  note, or a binary is hidden _inside_ the encrypted payload, not stamped
  on the outside. An observer can see that you published _something_, but
  not what kind of something.
- **It's resilient by being boring.** There's no backend to run, patch, or
  pay for; no database to breach. The attack surface is small precisely
  because the moving parts are few.

I'll be candid about the edges, in the spirit of not overclaiming: by
default the keychain lives in the browser's `localStorage`, unencrypted,
which is convenient but vulnerable to a determined attacker with access to
your machine or a cross-site scripting hole. Encrypting the keychain behind
a master passphrase is a planned hardening step rather than a shipped
guarantee. And this is a tool for hiding _contents_, not for hiding the
_existence_ of content — it makes no attempt at plausible deniability.

## Who might find it useful

- **Writers and small teams** who want to share private notes, drafts, or
  documentation without standing up (and securing) a server.
- **Privacy-minded publishers** who'd like to hand a link and a password to
  a trusted circle and let a free static host do the heavy lifting.
- **The self-hosting and offline-first crowd**, since the whole app installs
  as a PWA and runs without a network once cached.
- **The tinkerers and the curious** — anyone who enjoys seeing a familiar
  problem (access control) solved from an unfamiliar direction (client-side
  only).

It won't replace a full content platform, and it isn't trying to. But as a
demonstration that "public host, private content" need not be a
contradiction, I think it's a genuinely fun idea — and it was more
rewarding to build than I expected. I'm looking forward to feedback, and
there's more I'd like to explore. Enjoy!
