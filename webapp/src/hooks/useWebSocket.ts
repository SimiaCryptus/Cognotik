import {useEffect, useRef, useState} from 'react';
import {useDispatch} from 'react-redux';
import {addMessage} from '../store/slices/messageSlice';
import WebSocketService from '../services/websocket';
import {debounce} from '../utils/tabHandling';
import {Message} from "../types/messages";

export const useWebSocket = (sessionId: string) => {
    const RECONNECT_MAX_DELAY = 60000; // Increased to 60 seconds
    const RECONNECT_BASE_DELAY = 1000;
    const CONNECTION_TIMEOUT = 5000;
    const connectionStatus = useRef({attempts: 0, lastAttempt: 0});
    const RECONNECT_DELAY = 1000; // 1 second delay between attempts
    const [isConnected, setIsConnected] = useState(false);
    const [error, setError] = useState<Error | null>(null);
    const [isReconnecting, setIsReconnecting] = useState(false);
    const dispatch = useDispatch();
    const connectionAttemptRef = useRef(0);
    const HEARTBEAT_INTERVAL = 30000; // 30 seconds

    useEffect(() => {
        let connectionTimeout: NodeJS.Timeout;
        let isCleanedUp = false;

        // Implement exponential backoff for reconnection
        const getReconnectDelay = () => {
            return Math.min(RECONNECT_BASE_DELAY * Math.pow(2, connectionStatus.current.attempts), RECONNECT_MAX_DELAY);
        };
        // Debounce connection attempts
        const attemptConnection = debounce(() => {
            if (isCleanedUp) return;

            clearTimeout(connectionTimeout);
            const now = Date.now();
            if (now - connectionStatus.current.lastAttempt < RECONNECT_DELAY) {
                return;
            }
            connectionStatus.current.lastAttempt = now;
            connectionStatus.current.attempts++;
            WebSocketService.connect(sessionId);
            connectionTimeout = setTimeout(() => {
                if (!isConnected && !isCleanedUp) {
                    handleError(new Error('Connection timeout'));
                }
            }, CONNECTION_TIMEOUT);
        }, 100);

        // Reset connection status when sessionId changes
        connectionStatus.current = {attempts: 0, lastAttempt: 0};
        connectionAttemptRef.current = 0;
        const handleReconnecting = (attempts: number) => {
            setIsReconnecting(true);
            connectionStatus.current = ({
                attempts: attempts,
                lastAttempt: Date.now()
            });
        };

        if (!sessionId) {
            console.error('[WebSocket] Critical: No sessionId provided, connection aborted');
            return;
        }

        const handleMessage = (message: Message) => {
            // Ensure message has required fields
            if (!message?.id || !message?.version) {
                return;
            }
            dispatch(addMessage(message));
        };

        const handleConnectionChange = (connected: boolean) => {
            setIsConnected(connected);
            if (connected) {
                setError(null);
                setIsReconnecting(false);
                connectionAttemptRef.current = 0;
                // Reset connection attempts on successful connection
                connectionStatus.current.attempts = 0;
                console.log(`[WebSocket] Connected successfully at ${new Date().toISOString()}`);
            } else if (!isCleanedUp) {
                // Try to reconnect if disconnected unexpectedly
                console.warn(`[WebSocket] Disconnected unexpectedly at ${new Date().toISOString()}`);
                setTimeout(attemptConnection, getReconnectDelay());
            }
        };

        const handleError = (err: Error) => {
            if (isCleanedUp) return;

            setError(err);
            if (connectionStatus.current.attempts >= 10) {
                console.error(
                    `[WebSocket] Maximum reconnection attempts reached (${connectionStatus.current.attempts})`
                );
                return;
            }
            console.error(
                `[WebSocket] Connection error (attempt ${connectionStatus.current.attempts}):`,
                err.message
            );

            // Calculate delay using exponential backoff and retry
            const delay = getReconnectDelay();
            console.log(`[WebSocket] Attempting reconnection in ${delay / 1000} seconds`);
            setTimeout(attemptConnection, delay);

            setIsReconnecting(true);
        };

        WebSocketService.addMessageHandler(handleMessage);
        WebSocketService.addConnectionHandler(handleConnectionChange);
        WebSocketService.addErrorHandler(handleError);
        WebSocketService.on('reconnecting', handleReconnecting);
        // Initiate WebSocket connection
        WebSocketService.connect(sessionId);

        return () => {
            isCleanedUp = true;
            clearTimeout(connectionTimeout);
            console.log(`[WebSocket] Disconnecting at ${new Date().toISOString()}`);
            WebSocketService.removeMessageHandler(handleMessage);
            WebSocketService.removeConnectionHandler(handleConnectionChange);
            WebSocketService.removeErrorHandler(handleError);
            WebSocketService.off('reconnecting', handleReconnecting);
            WebSocketService.disconnect();
        };
    }, [sessionId, dispatch]); // Add dispatch to dependency array

    return {
        error,
        isReconnecting,
        readyState: WebSocketService.ws?.readyState,
        send: (message: string) => {
            return WebSocketService.send(message);
        },
        isConnected
    };
};