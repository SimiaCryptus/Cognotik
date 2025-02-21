import React, {useEffect, useState} from 'react';
import {useDispatch} from 'react-redux';
import styled from 'styled-components';
import {fetchAppConfig, isArchive} from '../services/appConfig';
import {useWebSocket} from '../hooks/useWebSocket';
import {addMessage} from '../store/slices/messageSlice';
import MessageList from './MessageList';
import InputArea from './InputArea';
import Spinner from './common/Spinner';
import {Message, MessageType} from '../types/messages';
import websocket from '@services/websocket';


const LOG_PREFIX = '[ChatInterface]';

interface WebSocketMessage {
    data: string;
    isHtml: boolean;
    timestamp: number;
}

interface ChatInterfaceProps {
    sessionId?: string;
    websocket: typeof websocket;
    isConnected: boolean;
}

const ChatContainer = styled.div`
    display: flex;
    flex-direction: column;
    height: 100vh;
    /* Add test id */
    &[data-testid] {
      outline: none;
    }
`;

 const ChatInterface: React.FC<ChatInterfaceProps> = ({
                                                         sessionId: propSessionId,
                                                         websocket,
                                                         isConnected,
                                                     }) => {
    const DEBUG = process.env.NODE_ENV === 'development';
    const debugLog = (message: string, data?: any) => {
        if (DEBUG) {
            console.debug(`${LOG_PREFIX} ${message}`, data);
        }
    };
    const [messages, setMessages] = React.useState<Message[]>([]);
    const [sessionId] = useState(() => propSessionId || window.location.hash.slice(1) || 'new');
    const dispatch = useDispatch();
    const ws = useWebSocket(sessionId);
     debugLog('Initializing chat interface', {sessionId, isConnected});


    useEffect(() => {
        // Skip effect in archive mode
        if (isArchive) return;

        let mounted = true;
        const loadAppConfig = async () => {
            if (!sessionId) return;
            try {
                const config = await fetchAppConfig(sessionId);
                if (mounted && config) {
                    debugLog('App config loaded successfully');
                } else {
                    console.warn('Could not load app config, using defaults');
                }
            } catch (error) {
                console.error('Failed to fetch app config:', error);
            }
        };
        loadAppConfig();
        return () => {
            mounted = false;
        };
    }, [sessionId]); // Only depend on sessionId

    useEffect(() => {
        // Skip effect in archive mode
        if (isArchive) return;

        debugLog('Setting up message handler', {
            wsReadyState: ws.readyState
        });
        // Add cleanup flag to prevent state updates after unmount
        let isComponentMounted = true;

        const handleMessage = (data: WebSocketMessage) => {
            if (!isComponentMounted) return;


            // Handle HTML messages differently
            if (data.isHtml) {
                debugLog('Processing HTML message');
                const newMessage = {
                    id: `${Date.now()}`,
                    content: data.data || '',
                    type: 'assistant' as MessageType, // Changed from 'response' to 'assistant'
                    timestamp: data.timestamp,
                    isHtml: true,
                    rawHtml: data.data,
                    version: data.timestamp,
                    sanitized: false
                };
                if (isComponentMounted) {
                    setMessages(prev => [...prev, newMessage]);
                }
                dispatch(addMessage(newMessage));
                return;
            }
            // Handle regular messages
            if (!data.data || typeof data.data !== 'string') {
                console.warn(`${LOG_PREFIX} Invalid message format received:`, data);
                return;
            }
            // Ignore connect messages
            if (data.data.includes('"type":"connect"')) {
                console.debug(`${LOG_PREFIX} Ignoring connect message`);
                console.groupEnd();
                return;
            }

            const [id, version, content] = data.data.split(',');
            const timestamp = Date.now();
            const messageObject = {
                id: `${id}-${timestamp}`,
                content: content,
                version: parseInt(version, 10) || timestamp,
                type: id.startsWith('u') ? 'user' as MessageType : 'assistant' as MessageType,
                timestamp,
                isHtml: false,
                rawHtml: null,
                sanitized: false
            };
            console.groupEnd();

            dispatch(addMessage(messageObject));
        };

        websocket.addMessageHandler(handleMessage);
        return () => {
            isComponentMounted = false;
            debugLog('Cleaning up message handler');
            websocket.removeMessageHandler(handleMessage);
        };
    }, [DEBUG, dispatch, isConnected, sessionId, websocket, ws.readyState]);

    const handleSendMessage = (msg: string) => {
        console.log(`${LOG_PREFIX} Sending message`, {
            messageLength: msg.length,
            sessionId,
            isConnected
        });
        ws.send(msg);
    };

    return isArchive ? (
        <ChatContainer data-testid="chat-container" id="chat-container">
            <MessageList/>
            {!isConnected && (
                <div className="connection-status">
                    <Spinner size="small" aria-label="Connecting..." />
                    <span>Connecting...</span>
                </div>
            )}
        </ChatContainer>
    ) : (
        <ChatContainer data-testid="chat-container" id="chat-container">
            <MessageList/>
            <InputArea onSendMessage={handleSendMessage}/>
        </ChatContainer>
    );
};

export default ChatInterface;