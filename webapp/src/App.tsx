import React from 'react';
import {Provider, useDispatch, useSelector} from 'react-redux';
import {RootState, store} from './store';
import {isArchive} from './utils/constants';
import './App.css';
import websocket from './services/websocket';
import {setConnectionError, setConnectionStatus} from './store/slices/connectionSlice';
import ChatInterface from './components/ChatInterface';
import ThemeProvider from './themes/ThemeProvider';
import {Menu} from "./components/Menu/Menu";
import {Modal} from "./components/Modal/Modal";
import {setupUIHandlers} from './utils/uiHandlers';

import Prism from 'prismjs';



import 'prismjs/components/prism-javascript';
import 'prismjs/components/prism-css';
import 'prismjs/components/prism-markup';
import 'prismjs/components/prism-typescript';
import 'prismjs/components/prism-jsx';
import 'prismjs/components/prism-tsx';
import 'prismjs/components/prism-diff';
import 'prismjs/components/prism-markdown';
import 'prismjs/components/prism-kotlin';
import 'prismjs/components/prism-java';
import 'prismjs/components/prism-mermaid';
import 'prismjs/components/prism-scala';
import 'prismjs/components/prism-python';

import 'prismjs/plugins/toolbar/prism-toolbar';
import 'prismjs/plugins/toolbar/prism-toolbar.css';
import 'prismjs/plugins/copy-to-clipboard/prism-copy-to-clipboard';
import 'prismjs/plugins/line-numbers/prism-line-numbers';
import 'prismjs/plugins/line-numbers/prism-line-numbers.css';
import 'prismjs/plugins/line-highlight/prism-line-highlight';
import 'prismjs/plugins/line-highlight/prism-line-highlight.css';
import 'prismjs/plugins/diff-highlight/prism-diff-highlight';
import 'prismjs/plugins/diff-highlight/prism-diff-highlight.css';
import 'prismjs/plugins/show-language/prism-show-language';
import 'prismjs/plugins/normalize-whitespace/prism-normalize-whitespace';

import QRCode from 'qrcode-generator';
import {addMessage} from "./store/slices/messageSlice";
import {Message} from './types/messages';
import ErrorBoundary from './components/ErrorBoundary/ErrorBoundary';
import ErrorFallback from './components/ErrorBoundary/ErrorFallback';

const getArchivedMessages = () => {
    if (!isArchive) return null;
    try {
        const messagesEl = document.getElementById('archived-messages');
        if (!messagesEl) return null;
        return JSON.parse(messagesEl.textContent || '[]');
    } catch (err) {
        console.error(`${LOG_PREFIX} Critical: Failed to parse archived messages:`, err);
        return null;
    }
};

const APP_VERSION = '1.0.0';
const LOG_PREFIX = '[SkyeNet]';
Prism.manual = true;

const AppContent: React.FC = () => {
    if (!isArchive) {
        console.info(`${LOG_PREFIX} Initializing application v${APP_VERSION}`);
    }
    const appConfig = useSelector((state: RootState) => state.config);
    const dispatch = useDispatch();

    const [archivedMessagesLoaded, setArchivedMessagesLoaded] = React.useState(false);

    const {isConnected, error} = useSelector((state: RootState) => state.connection);

    React.useEffect(() => {
        const handleConnectionChange = (connected: boolean) => {
            dispatch(setConnectionStatus(connected));
        };
        const handleError = (error: Error) => {
            dispatch(setConnectionError(error));
        };
        websocket.addConnectionHandler(handleConnectionChange);
        websocket.addErrorHandler(handleError);
        return () => {
            websocket.removeConnectionHandler(handleConnectionChange);
            websocket.removeErrorHandler(handleError);
        };
    }, [dispatch]);

    React.useEffect(() => {
        if (isArchive && !archivedMessagesLoaded) {
            const archivedMessages = getArchivedMessages();
            if (archivedMessages) {
                archivedMessages.forEach((msg: Message) => dispatch(addMessage(msg)));
                setArchivedMessagesLoaded(true);
            }
        }
    }, [dispatch, archivedMessagesLoaded]);

    const sessionId = websocket.getSessionId();
    React.useEffect(() => {

        if (isArchive) {
            return;
        }

        if (appConfig.applicationName) {
            document.title = appConfig.applicationName;
        }
    }, [appConfig.applicationName]);

    if (!isConnected) {
        console.warn(`${LOG_PREFIX} WebSocket disconnected - sessionId: ${sessionId}`);
    }

    React.useEffect(() => {
        if (error) {
            console.error(`${LOG_PREFIX} WebSocket error:`, error.message);
        }
    }, [error]);

    React.useEffect(() => {
        const cleanup = setupUIHandlers();
        return () => {
            cleanup();
        };
    }, []);

    React.useEffect(() => {
        const qr = QRCode(0, 'L');
        qr.addData('https://example.com');
        qr.make();

    }, []);

    return (
        <ThemeProvider>
            <div className={`App`}>
                <Menu/>
                <ChatInterface
                    sessionId={sessionId}
                    websocket={websocket}
                    isConnected={isConnected}
                />
                <Modal/>
            </div>
        </ThemeProvider>
    );
};

const App: React.FC = () => {
    return (
        <Provider store={store}>
            <ErrorBoundary FallbackComponent={ErrorFallback}>
                <AppContent/>
            </ErrorBoundary>
        </Provider>
    );
};

console.info(`${LOG_PREFIX} Application initialized successfully`);

export default App;