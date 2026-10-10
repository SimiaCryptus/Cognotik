# Cognotik command line tools

Requires Node 18+. Optional: `npm i qrcode-terminal` to show a QR code during login.

## Sign in

    node js/cognotik-chat.mjs login                # opens the browser approval page
    node js/cognotik-chat.mjs login --no-open      # just prints the URL / QR code

The token is stored in `~/.cognotik/token.json`. It is sent as the auth cookie on every request.

## Usage

    node js/cognotik-chat.mjs status                         # verify config + authentication
    node js/cognotik-chat.mjs models claude                  # list / filter models
    node js/cognotik-chat.mjs -m <model-id> "Hello there"    # one-shot prompt
    git diff | node js/cognotik-chat.mjs ask "Summarize this diff"
    node js/cognotik-chat.mjs chat -m <model-id> --save convo.json

Interactive commands include `/model`, `/system`, `/temp`, `/file`, `/image`, `/history`,
`/undo`, `/retry`, `/save` and `/load`. Type `/help` for the full list.
Use `"""` on its own line to start and end multi-line input.

## Persistent defaults

    node js/cognotik-chat.mjs config set model <model-id>
    node js/cognotik-chat.mjs config set url https://hosted.cognotik.com
    node js/cognotik-chat.mjs config set providers Anthropic,Gemini

Environment overrides: `COGNOTIK_URL`, `COGNOTIK_TOKEN`, `COGNOTIK_TOKEN_FILE`, `COGNOTIK_COOKIE`,
`COGNOTIK_API_KEY`, `COGNOTIK_MODEL`, `COGNOTIK_PROVIDERS`, `COGNOTIK_TEMPERATURE`, `COGNOTIK_TIMEOUT`,
`COGNOTIK_DEBUG`, `NO_COLOR`.
## Client library (Node + browser)
`js/cognotik-client.mjs` is a dependency-free ES module used by the CLI and usable directly.
Node:
     import { readFile } from 'node:fs/promises';
     import { CognotikClient, Conversation, buildRequest, extractText } from './cognotik-client.mjs';
     const { token, cookieName, baseUrl } = JSON.parse(await readFile(`${process.env.HOME}/.cognotik/token.json`, 'utf8'));
     const client = new CognotikClient({ baseUrl, token, cookieName });
     const models = await client.listModels();
     const { text } = await client.ask('Explain monads briefly', { model: models[0].modelId });
     // multi-turn
     const conv = new Conversation({ system: 'You are terse.' });
     conv.addUser([{ type: 'text', text: 'Hi' }]);
     const resp = await client.chat(buildRequest(conv, { model: models[0].modelId }));
     conv.addAssistant(extractText(resp));
Browser (the page's own session cookie is used; `baseUrl: ''` means same origin):
     <script type="module">
       import { CognotikClient, imagePartFromBlob } from './cognotik-client.mjs';
       const client = new CognotikClient({ baseUrl: '' });
       const ctrl = new AbortController();
       const { text } = await client.ask('Hello!', {
         model: 'claude-sonnet-4',
         signal: ctrl.signal,
         onStatus: (s) => console.log(s),
       });
     </script>
Notes:
- In browsers the `Cookie` header cannot be set, so `token` is ignored and requests use
   `credentials: 'include'`. Cross-origin use requires the server to send the appropriate
   CORS headers (including `Access-Control-Allow-Credentials`), and `X-API-Key` triggers a preflight.
- Pass `fetch` to use a custom implementation, `logger` + `verbose` for diagnostics.
- Errors are `CognotikError` instances with `status`, `auth`, `retryable` and `aborted` fields.