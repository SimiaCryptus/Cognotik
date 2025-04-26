import React from 'react';
import Prism from 'prismjs';
import { Provider, useDispatch, useSelector } from 'react-redux';
import { RootState, store } from './store';
import { isArchive } from './services/appConfig';
import { setConnectionStatus } from './store/slices/connectionSlice';
import ErrorBoundary from './components/ErrorBoundary/ErrorBoundary';
import ErrorFallback from './components/ErrorBoundary/ErrorFallback';
import './App.css';
import websocket from './services/websocket';
import { setConnectionError } from './store/slices/connectionSlice';
import ChatInterface from './components/ChatInterface';
import ThemeProvider from './themes/ThemeProvider';
import { Menu } from './components/Menu/Menu';
import { Modal } from './components/Modal/Modal';
import { setupUIHandlers } from './utils/uiHandlers';
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
import { addMessage } from './store/slices/messageSlice';
import { Message } from './types/messages';
const APP_VERSION = '1.0.0'; 
const LOG_PREFIX = '[Cognotik]';
Prism.manual = true;


// Add function to extract archived messages
const getArchivedMessages = () => {
    if (!isArchive) return null;
    try {
        const messagesEl = document.getElementById('archived-messages');
        if (!messagesEl || !messagesEl.textContent) {
            console.warn(`[Cognotik] No archived messages found in DOM`);
            return [];
        }
        return JSON.parse(messagesEl.textContent || '[]');
    } catch (err) {
        console.error(`[Cognotik] Critical: Failed to parse archived messages:`, err);
        return [];
    }
};



// Create a separate component for the app content
const AppContent: React.FC = () => {
    if (!isArchive) {
        console.info(`${LOG_PREFIX} Initializing application v${APP_VERSION}`);
    } else {
        console.info(`${LOG_PREFIX} Initializing application v${APP_VERSION} in archive mode`);
    }
    const appConfig = useSelector((state: RootState) => state.config);
    const dispatch = useDispatch();
    // Only load archived messages once on mount
    const [archivedMessagesLoaded, setArchivedMessagesLoaded] = React.useState(false);
    // Use the useWebSocket hook instead of direct websocket access
    const { isConnected, error } = useSelector((state: RootState) => state.connection);
    // Update connection status in Redux store when websocket status changes
    React.useEffect(() => {
        const handleConnectionChange = (connected: boolean) => {
            dispatch(setConnectionStatus(connected));
        };
        // Accept any error, but always dispatch a serializable error
        const handleError = (error: any) => {
            if (error instanceof Error) {
                dispatch(setConnectionError({
                    message: error.message,
                    name: error.name,
                    stack: error.stack
                }));
            } else if (typeof error === 'string') {
                dispatch(setConnectionError({ message: error }));
            } else if (error && typeof error === 'object' && 'message' in error) {
                dispatch(setConnectionError({
                    message: error.message,
                    name: error.name,
                    stack: error.stack
                }));
            } else {
                dispatch(setConnectionError({ message: String(error) }));
            }
        };
        websocket.addConnectionHandler(handleConnectionChange);
        websocket.addErrorHandler(handleError);
        return () => {
            websocket.removeConnectionHandler(handleConnectionChange);
            websocket.removeErrorHandler(handleError);
        };
    }, [dispatch]);

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


    
    // Only get sessionId if not in archive mode
    const sessionId = React.useMemo(() => {
        if (isArchive) return '';
        return websocket.getSessionId();
    }, [isArchive]);

    React.useEffect(() => {
        // Skip websocket setup if loading from archive
        if (isArchive) {
            return;
        }

        if (appConfig.applicationName) {
            document.title = appConfig.applicationName;
        }
    }, [appConfig.applicationName]);

    // Only log websocket disconnected if not in archive mode
    React.useEffect(() => {
        if (!isArchive && !isConnected) {
            console.warn(`${LOG_PREFIX} WebSocket disconnected - sessionId: ${sessionId}`);
        }
    }, [isConnected, sessionId]);
    // Log WebSocket errors for debugging
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
    // Add debug information to help diagnose rendering issues
    React.useEffect(() => {
        console.log(`${LOG_PREFIX} Rendering AppContent component. isArchive:`, isArchive);
        console.log(`${LOG_PREFIX} Connection status:`, isConnected ? 'Connected' : 'Disconnected');
        // Check if the DOM is properly rendering
        const rootElement = document.getElementById('root');
        console.log(`${LOG_PREFIX} Root element:`, rootElement);
        console.log(`${LOG_PREFIX} Root element children:`, rootElement?.childNodes?.length);
    }, [isArchive, isConnected]);


    return (
        <ThemeProvider>
            <div className="App">
                <div style={{ padding: '20px', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                    <h1>SimiaCryptus AI Assistant</h1>
                </div>
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
    // Add console log to verify App component is rendering
    console.log(`${LOG_PREFIX} Rendering App component`);
    return (
        <ErrorBoundary FallbackComponent={ErrorFallback}>
            <Provider store={store}>
                <AppContent/>
            </Provider>
        </ErrorBoundary>
    );
};

console.info(`${LOG_PREFIX} Application initialized successfully`);


export default App;