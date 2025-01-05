import React, {ButtonHTMLAttributes, useEffect, useState} from 'react';
import {useDispatch, useSelector} from 'react-redux';
import styled from 'styled-components';
import {RootState} from '../../store';
import {updateWebSocketConfig} from '../../store/slices/configSlice';
import WebSocketService from '../../services/websocket';

interface StyledButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
    variant?: 'primary' | 'secondary' | 'danger' | 'success';
}

const StyledButton = styled.button<StyledButtonProps>`
  padding: 8px 16px;
  border-radius: 4px;
  border: none;
  cursor: pointer;
  font-weight: 500;
  transition: all 0.2s;
  ${({variant, theme}) => {
    switch (variant) {
        case 'primary':
            return `
          background: ${theme.colors.primary};
          color: white;
          &:hover { background: ${theme.colors.primaryDark || theme.colors.primary}; }
        `;
        case 'secondary':
            return `
          background: ${theme.colors.secondary};
          color: white;
          &:hover { background: ${theme.colors.secondaryDark || theme.colors.secondary}; }
        `;
        case 'danger':
            return `
          background: ${theme.colors.error};
          color: white;
          &:hover { background: ${theme.colors.errorDark || theme.colors.error}; }
        `;
        case 'success':
            return `
          background: ${theme.colors.success};
          color: white;
          &:hover { background: ${theme.colors.successDark || theme.colors.success}; }
        `;
        default:
            return `
          background: ${theme.colors.surface};
          color: ${theme.colors.text.primary};
          &:hover { background: ${theme.colors.hover}; }
        `;
    }
}}
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const logPrefix = '[WebSocketMenu]';

const MenuContainer = styled.div`
    padding: 1rem;
    background: ${({theme}) => theme.colors.surface};
    border-radius: ${({theme}) => theme.sizing.borderRadius.md};
    border: 1px solid ${({theme}) => theme.colors.border};
`;
const StatusContainer = styled.div`
    margin-bottom: 1rem;
    padding: 0.5rem;
    border-radius: ${({theme}) => theme.sizing.borderRadius.sm};
    display: flex;
    align-items: center;
    gap: 0.5rem;
`;
const StatusIndicator = styled.div<{ status: 'connected' | 'disconnected' | 'connecting' | 'error' }>`
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background-color: ${({status, theme}) => {
    switch (status) {
        case 'connected':
            return theme.colors.success;
        case 'disconnected':
            return theme.colors.error;
        case 'connecting':
            return theme.colors.warning;
        case 'error':
            return theme.colors.error;
        default:
            return theme.colors.disabled;
    }
}};
`;
const StatusText = styled.span`
    color: ${({theme}) => theme.colors.text.secondary};
    font-size: 0.9rem;
`;
const ConnectionDetails = styled.div`
    margin-top: 0.5rem;
    font-size: 0.8rem;
    color: ${({theme}) => theme.colors.text.secondary};
`;
const ButtonGroup = styled.div`
    display: flex;
    gap: 0.5rem;
    margin-top: 1rem;
`;

const FormGroup = styled.div`
    margin-bottom: 1rem;
`;

const Label = styled.label`
    display: block;
    margin-bottom: 0.5rem;
    color: ${({theme}) => theme.colors.text.secondary};
`;

const Input = styled.input`
    width: 100%;
    padding: 0.5rem;
    border: 1px solid ${({theme}) => theme.colors.border};
    border-radius: ${({theme}) => theme.sizing.borderRadius.sm};
    background: ${({theme}) => theme.colors.background};
    color: ${({theme}) => theme.colors.text.primary};
`;

export const WebSocketMenu: React.FC = () => {
    const dispatch = useDispatch();
    const wsConfig = useSelector((state: RootState) => state.config.websocket);
    const [connectionStatus, setConnectionStatus] = useState<'connected' | 'disconnected' | 'connecting' | 'error'>('disconnected');
    const [lastError, setLastError] = useState<string | null>(null);
    const [reconnectAttempts, setReconnectAttempts] = useState(0);
    console.log(`${logPrefix} Initial websocket config:`, wsConfig);


    const [config, setConfig] = useState({
        url: process.env.NODE_ENV === 'development' ? wsConfig.url : window.location.hostname,
        port: process.env.NODE_ENV === 'development' ? wsConfig.port : window.location.port,
        protocol: wsConfig.protocol
    });
    // Add connection status monitoring
    useEffect(() => {
        const handleConnectionChange = (connected: boolean) => {
            setConnectionStatus(connected ? 'connected' : 'disconnected');
            if (connected) {
                setLastError(null);
                setReconnectAttempts(0);
            }
        };
        const handleError = (error: Error) => {
            setConnectionStatus('error');
            setLastError(error.message);
        };
        const handleReconnecting = (attempts: number) => {
            setConnectionStatus('connecting');
            setReconnectAttempts(attempts);
        };
        WebSocketService.addConnectionHandler(handleConnectionChange);
        WebSocketService.addErrorHandler(handleError);
        WebSocketService.on('reconnecting', handleReconnecting);
        return () => {
            WebSocketService.removeConnectionHandler(handleConnectionChange);
            WebSocketService.removeErrorHandler(handleError);
            WebSocketService.off('reconnecting', handleReconnecting);
        };
    }, []);

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        console.log(`${logPrefix} Submitting WebSocket configuration:`, config);
        dispatch(updateWebSocketConfig(config));
        WebSocketService.disconnect();
        WebSocketService.connect({
            url: config.url,
            port: config.port,
            protocol: config.protocol,
            retryAttempts: 3,
            timeout: 5000
        });
        console.log(`${logPrefix} Configuration updated successfully`);
    };
    const handleReconnect = () => {
        setConnectionStatus('connecting');
        WebSocketService.reconnect();
    };
    const handleDisconnect = () => {
        WebSocketService.disconnect();
    };

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const {name, value} = e.target;
        console.log(`${logPrefix} Field "${name}" changed to:`, value);
        if (name === 'port' && !/^\d*$/.test(value)) {
            console.warn(`${logPrefix} Invalid port value entered:`, value);
            return;
        }

        setConfig(prev => ({...prev, [name]: value}));
    };

    return (
        <MenuContainer>
            <h3>WebSocket Configuration</h3>
            <StatusContainer>
                <StatusIndicator status={connectionStatus}/>
                <StatusText>
                    {connectionStatus === 'connected' && 'Connected'}
                    {connectionStatus === 'disconnected' && 'Disconnected'}
                    {connectionStatus === 'connecting' && `Connecting (Attempt ${reconnectAttempts})`}
                    {connectionStatus === 'error' && 'Connection Error'}
                </StatusText>
            </StatusContainer>
            {lastError && (
                <ConnectionDetails>
                    Last Error: {lastError}
                </ConnectionDetails>
            )}
            <form onSubmit={handleSubmit}>
                <FormGroup>
                    <Label htmlFor="protocol">Protocol</Label>
                    <Input
                        id="protocol"
                        name="protocol"
                        value={config.protocol}
                        onChange={handleChange}
                        placeholder="ws:// or wss://"
                    />
                </FormGroup>
                <FormGroup>
                    <Label htmlFor="url">Host URL</Label>
                    <Input
                        id="url"
                        name="url"
                        value={config.url}
                        onChange={handleChange}
                        placeholder="localhost or your server URL"
                    />
                </FormGroup>
                <FormGroup>
                    <Label htmlFor="port">Port</Label>
                    <Input
                        id="port"
                        name="port"
                        value={config.port}
                        onChange={handleChange}
                        placeholder="8080"
                    />
                </FormGroup>
                <ButtonGroup>
                    <StyledButton type="submit" variant="primary">
                        Save Configuration
                    </StyledButton>
                    <StyledButton
                        type="button"
                        variant="secondary"
                        onClick={handleReconnect}
                        disabled={connectionStatus === 'connected' || connectionStatus === 'connecting'}
                    >
                        Reconnect
                    </StyledButton>
                    <StyledButton
                        type="button"
                        variant="danger"
                        onClick={handleDisconnect}
                        disabled={connectionStatus === 'disconnected'}
                    >
                        Disconnect
                    </StyledButton>
                </ButtonGroup>
            </form>
            {connectionStatus === 'connected' && (
                <ConnectionDetails>
            Connected to: {`${config.protocol}//${config.url}:${config.port}`}
                </ConnectionDetails>
            )}
            {connectionStatus === 'connecting' && (
                <ConnectionDetails>
                    Attempting to connect... (Attempt {reconnectAttempts}/5)
                </ConnectionDetails>
            )}
        </MenuContainer>
    );
};