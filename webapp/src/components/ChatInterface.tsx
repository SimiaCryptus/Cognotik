import React, {useEffect, useState} from 'react';
import {useDispatch, useSelector} from 'react-redux';
import styled from 'styled-components';
import {fetchAppConfig} from '../services/appConfig';
import {isArchive, APP_NAME} from '../utils/constants';
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
                const config = await fetchAppConfig(sessionId, '/appInfo');
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