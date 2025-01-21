import React from 'react';
import {Provider, useDispatch, useSelector} from 'react-redux';
import {RootState, store} from './store';
import {isArchive} from './services/appConfig';
import ErrorBoundary from './components/ErrorBoundary/ErrorBoundary';
import ErrorFallback from './components/ErrorBoundary/ErrorFallback';
import './App.css';
import websocket from './services/websocket';
import ChatInterface from './components/ChatInterface';
import ThemeProvider from './themes/ThemeProvider';
import {Menu} from "./components/Menu/Menu";
import {Modal} from "./components/Modal/Modal";
import {setupUIHandlers} from './utils/uiHandlers';
// Import Prism core
import Prism from 'prismjs';
// Import base CSS
// import 'prismjs/themes/prism.css';
// Import commonly used languages
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

// Import essential plugins
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
// import 'prismjs/plugins/autoloader/prism-autoloader';
import QRCode from 'qrcode-generator';
import {addMessage} from "./store/slices/messageSlice";
import {Message} from './types/messages';


// Add function to extract archived messages
const getArchivedMessages = () => {
    if (!isArchive) return null;
    try {
        const messagesEl = document.getElementById('archived-messages');
        if (!messagesEl) return null;
        return JSON.parse(messagesEl.textContent || '[]');
    } catch (err) {
        console.error('Failed to parse archived messages:', err);
        return null;
    }
};

const APP_VERSION = '1.0.0';
const LOG_PREFIX = '[App]';
Prism.manual = true;


// Create a separate component for the app content
const AppContent: React.FC = () => {
    if (!isArchive) {
        console.group(`${LOG_PREFIX} Initializing v${APP_VERSION}`);
    }
    const appConfig = useSelector((state: RootState) => state.config);
    const dispatch = useDispatch();
    // Only load archived messages once on mount
    const [archivedMessagesLoaded, setArchivedMessagesLoaded] = React.useState(false);

    // Load archived messages on mount if in archive mode
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
    const isConnected = websocket.isConnected();
    React.useEffect(() => {
        // Skip websocket setup if loading from archive
        if (appConfig.isArchive) {
            return;
        }

        if (appConfig.applicationName) {
            document.title = appConfig.applicationName;
        }
    }, [appConfig.applicationName]);
    console.debug('WebSocket state:', {
        sessionId,
        isConnected
    });

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

        return () => {
            console.debug(`${LOG_PREFIX} Cleanup complete`);
        };
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
// Create the main App component that provides the Redux store
const App: React.FC = () => {
    return (
        <ErrorBoundary FallbackComponent={ErrorFallback}>
            <Provider store={store}>
                <AppContent/>
            </Provider>
        </ErrorBoundary>
    );
};

console.groupEnd();
console.log(`${LOG_PREFIX} v${APP_VERSION} loaded successfully`);


export default App;