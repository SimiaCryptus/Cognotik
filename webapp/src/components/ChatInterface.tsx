import React, {useEffect, useState} from 'react';
import {useDispatch, useSelector} from 'react-redux';
import styled from 'styled-components';
import {fetchAppConfig} from '../services/appConfig';
import {isArchive} from '../utils/constants';
import {logger} from '../utils/logger';
import {useWebSocket} from '../hooks/useWebSocket';
import {addMessage} from '../store/slices/messageSlice';
import MessageList from './MessageList';
import InputArea from './InputArea';
import Spinner from './common/Spinner';
import {Message, MessageType} from '../types/messages';
import {WebSocketService} from '../services/websocket';
import {RootState} from '../store';


const LOG_PREFIX = '[ChatInterface]';

interface WebSocketMessage {
    data: string;
    isHtml: boolean;
    timestamp: number;
}

interface ChatInterfaceProps {
    sessionId?: string;
    websocket: WebSocketService;
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
        logger.debug(`${LOG_PREFIX} ${message}`, data);
    };
    const [messages, setMessages] = useState<Message[]>([]);
    const [sessionId] = useState(() => propSessionId || window.location.hash.slice(1) || 'new');
    const dispatch = useDispatch();
    const ws = useWebSocket(sessionId);
    const appConfig = useSelector((state: RootState) => state.config);


    useEffect(() => {
        // Skip effect in archive mode
        if (isArchive) return;

        let mounted = true;
        const loadAppConfig = async () => {
            if (!sessionId) return;
            try {
                // Fix: Use the correct endpoint path for app config
                const config = await fetchAppConfig(sessionId);
                if (mounted && config) {
                    console.info(`${LOG_PREFIX} App config loaded successfully`, config);
                } else {
                    console.warn(`${LOG_PREFIX} Could not load app config, using defaults`);
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

        // Add cleanup flag to prevent state updates after unmount
        let isComponentMounted = true;

        const handleMessage = (data: WebSocketMessage) => {
            if (!isComponentMounted) return;
            if (data.isHtml) {
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
                return;
            }
            // Ignore connect messages
            if (data.data.includes('"type":"connect"')) {
                return;
            }

            // Fix message parsing to properly handle commas in content
            const firstCommaIndex = data.data.indexOf(',');
            const secondCommaIndex = firstCommaIndex > -1 ? data.data.indexOf(',', firstCommaIndex + 1) : -1;
            
            if (firstCommaIndex === -1 || secondCommaIndex === -1) {
                console.error(`${LOG_PREFIX} Invalid message format received:`, data.data);
                return;
            }
            
            const id = data.data.substring(0, firstCommaIndex);
            const version = data.data.substring(firstCommaIndex + 1, secondCommaIndex);
            const content = data.data.substring(secondCommaIndex + 1);
            const timestamp = Date.now();
            const messageObject = {
                id: `${id}-${timestamp}`,
                content: content,
                version: parseInt(version, 10) || timestamp,
                type: id.startsWith('u') ? 'user' : id.startsWith('s') ? 'system' : 'assistant' as MessageType,
                timestamp,
                isHtml: false,
                rawHtml: null,
                sanitized: false
            };
            dispatch(addMessage(messageObject));
        };

        websocket.addMessageHandler(handleMessage);
        return () => {
            isComponentMounted = false;
            websocket.removeMessageHandler(handleMessage);
        };
    }, [DEBUG, dispatch, isConnected, sessionId, websocket, ws.readyState]);

    const handleSendMessage = (msg: string) => {
        console.info(`${LOG_PREFIX} Sending message - length: ${msg.length}`, {
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
                    <Spinner size="small" aria-label="Connecting..."/>
                    <span>Connecting...</span>
                </div>
            )}
        </ChatContainer>
    ) : (
        <ChatContainer data-testid="chat-container" id="chat-container">
            <MessageList/>
            <InputArea onSendMessage={handleSendMessage} isWebSocketConnected={ws.isConnected}/>
        </ChatContainer>
    );
};

export default ChatInterface;