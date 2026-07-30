import './styles/base.css';

/* Renderers must be initialised before any content is mounted (§1.2 step 2). */
import './render/prism.js';
import {initMermaid} from './render/mermaid.js';
import {initMathJax} from './render/mathjax.js';

import {APP_VERSION, IS_DEV} from './config/env.js';
import {createLogger, renderFatalError} from './util/logger.js';
import {resolveSessionId} from './core/session.js';
import {MessageStore} from './core/store.js';
import {Transport} from './core/transport.js';
import {bus, Events} from './core/bus.js';

import {mountShell} from './ui/shell.js';
import {Composer} from './ui/composer.js';
import {installModal, installModalTriggers} from './ui/modal.js';

import {MessageListView} from './render/message-list.js';
import {installPipeline, runPipeline} from './render/pipeline.js';
import {observeForTabs} from './render/tabs.js';
import {installVerboseShortcut, syncVerboseDom} from './render/verbose.js';

import {applyAppConfig, fetchAppInfo, loadDevWebSocketConfig} from './config/app-config.js';
import {applyArchiveBodyClass, isArchive, loadArchivedMessages} from './config/archive.js';

const log = createLogger('App');

/**
 * Boot sequence, normative order (reverse-spec §1.2).
 * Exported so the client can also be embedded with an explicit sessionId.
 */
export function boot(options = {}) {
    const root = options.root || document.getElementById('root');
    if (!root) throw new Error('Failed to find the root element');

    // 1. session identity
    const sessionId = resolveSessionId(options.sessionId);
    log.info(`Starting Cognotik chat v${APP_VERSION}`, {sessionId, archive: isArchive, dev: IS_DEV});

    // 2. renderers
    initMermaid();
    initMathJax();

    // 3. shell DOM
    const shell = mountShell(root);
    applyArchiveBodyClass();

    const store = new MessageStore();
    const transport = new Transport({
        sessionId,
        getLastMessageTime: () => store.lastMessageTime,
        ...(loadDevWebSocketConfig() || {})
    });

    // 4. global listeners / post-processing hooks
    installPipeline();
    installVerboseShortcut(document);
    syncVerboseDom(document);
    installModal({mount: shell.modalRoot, getSessionId: () => sessionId});
    installModalTriggers(document);
    observeForTabs(shell.list);

    const view = new MessageListView({
        scroller: shell.scroller,
        list: shell.list,
        store,
        transport,
        interactive: !isArchive
    });

    transport.on('message', (event) => store.upsertMany(event.detail));

    /* 7. archive mode short-circuits the socket and appInfo entirely (§13). */
    if (isArchive) {
        store.hydrate(loadArchivedMessages());
        view.render();
        runPipeline();
        return {sessionId, store, view, transport: null};
    }

    const composer = new Composer({host: shell.composerHost, transport, store});
    transport.on('open', () => {
        composer.setConnected(true);
        bus.emit(Events.CONNECTION_CHANGED, true);
    });
    transport.on('close', () => {
        composer.setConnected(false);
        bus.emit(Events.CONNECTION_CHANGED, false);
    });
    transport.on('reconnecting', (event) => log.warn('Reconnecting', {attempt: event.detail}));
    transport.on('error', (event) => {
        log.error('Transport error', event.detail?.message);
        bus.emit(Events.CONNECTION_ERROR, event.detail);
    });
    bus.on(Events.MESSAGES_RENDERED, () => composer.updateVisibility());
     bus.on(Events.PENDING_CHANGED, (event) => composer.setPending(event.detail?.pending || 0));

    // 5. open the socket
    transport.connect(sessionId);

    // 6. appInfo — non-blocking; failures fall back to defaults
    fetchAppInfo(sessionId).then((config) => {
        applyAppConfig(config);
        composer.updateVisibility();
        if (IS_DEV && config.websocket) {
            transport.configure(config.websocket);
            transport.reconnect();
        }
    });

    return {sessionId, store, view, transport, composer};
}

try {
    boot();
    log.info('Application started successfully');
} catch (error) {
    log.error('Critical: failed to start application', error);
    renderFatalError(error);
}