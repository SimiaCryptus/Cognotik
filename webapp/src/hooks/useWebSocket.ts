import {useEffect, useRef, useState} from 'react';
 import {useDispatch} from 'react-redux';
 import {addMessage} from '../store/slices/messageSlice';
 import WebSocketService from '../services/websocket';
 import {debounce} from '../utils/tabHandling';
 import {Message} from "../types/messages";
 export const useWebSocket = (sessionId: string) => {
     const RECONNECT_MAX_DELAY = 60000;
     const RECONNECT_BASE_DELAY = 1000;
     const CONNECTION_TIMEOUT = 5000;
     const connectionStatus = useRef({attempts: 0, lastAttempt: 0});
     const [isConnected, setIsConnected] = useState(false);
     const [error, setError] = useState<Error | null>(null);
     const [isReconnecting, setIsReconnecting] = useState(false);
     const dispatch = useDispatch();
     const connectionAttemptRef = useRef(0);
     useEffect(() => {
         let connectionTimeout: NodeJS.Timeout;
         let isCleanedUp = false;

         const getReconnectDelay = () => {
             return Math.min(RECONNECT_BASE_DELAY * Math.pow(2, connectionStatus.current.attempts), RECONNECT_MAX_DELAY);
         };

         const attemptConnection = debounce(() => {
             if (isCleanedUp) return;
             clearTimeout(connectionTimeout);
             const now = Date.now();
             if (now - connectionStatus.current.lastAttempt < RECONNECT_BASE_DELAY) {
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

                 connectionStatus.current.attempts = 0;
                 console.log(`[WebSocket] Connected successfully at ${new Date().toISOString()}`);
             } else if (!isCleanedUp) {

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

             const delay = getReconnectDelay();
             console.log(`[WebSocket] Attempting reconnection in ${delay / 1000} seconds`);
             setTimeout(attemptConnection, delay);
             setIsReconnecting(true);
         };
         WebSocketService.addMessageHandler(handleMessage);
         WebSocketService.addConnectionHandler(handleConnectionChange);
         WebSocketService.addErrorHandler(handleError);
         WebSocketService.on('reconnecting', handleReconnecting);

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
     }, [sessionId, dispatch]);

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