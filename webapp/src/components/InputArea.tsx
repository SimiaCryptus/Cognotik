import React, {memo, useCallback, useState} from 'react';
import styled from 'styled-components';
import {useSelector} from 'react-redux';
import {RootState} from '../store';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import Prism from 'prismjs';
import FormatBoldIcon from '@mui/icons-material/FormatBold';
import FormatItalicIcon from '@mui/icons-material/FormatItalic';
import CodeIcon from '@mui/icons-material/Code';
import FormatListBulletedIcon from '@mui/icons-material/FormatListBulleted';
import FormatQuoteIcon from '@mui/icons-material/FormatQuote';
import LinkIcon from '@mui/icons-material/Link';
import TitleIcon from '@mui/icons-material/Title';
import TableChartIcon from '@mui/icons-material/TableChart';
import CheckBoxIcon from '@mui/icons-material/CheckBox';
import ImageIcon from '@mui/icons-material/Image';
import VisibilityIcon from '@mui/icons-material/Visibility';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import EditIcon from '@mui/icons-material/Edit';
import {debounce} from '../utils/tabHandling';

const CollapseButton = styled.button`
    position: absolute;
    top: -12px;
    right: 24px;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: ${({theme}) => theme.colors.surface};
    border: 1px solid ${({theme}) => theme.colors.border};
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    color: ${({theme}) => theme.colors.text};
    transition: all 0.2s ease;
    &:hover {
        background: ${({theme}) => theme.colors.hover};
        transform: translateY(-1px);
    }
`;
const CollapsedPlaceholder = styled.div`
    padding: 0.75rem;
    background: ${({theme}) => theme.colors.surface}dd;
    border-top: 1px solid ${({theme}) => theme.colors.border};
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    position: sticky;
    bottom: 0;
    backdrop-filter: blur(16px);
    &:hover {
        background: ${({theme}) => theme.colors.hover};
    }
`;
// Add preview container styles
const PreviewContainer = styled.div`
    padding: 0.5rem;
    border: 1px solid ${props => props.theme.colors.border};
    border-radius: 0 0 ${props => props.theme.sizing.borderRadius.md} ${props => props.theme.sizing.borderRadius.md};
    background: ${props => props.theme.colors.background};
    min-height: 120px;
    max-height: ${({theme}) => theme.sizing.console.maxHeight};
    overflow-y: auto;
    pre {
        background: ${props => props.theme.colors.surface};
        padding: 1rem;
        border-radius: ${props => props.theme.sizing.borderRadius.sm};
        overflow-x: auto;
    }
    code {
        font-family: monospace;
    }
`;

// Debug logging utility
const DEBUG = process.env.NODE_ENV === 'development';
const log = (message: string, data?: unknown) => {
    if (DEBUG) {
        if (data) {
            console.debug(`[InputArea] ${message}`, data);
        } else {
            console.debug(`[InputArea] ${message}`);
        }
    }
};
// Critical error logging
const logError = (message: string, error?: unknown) => {
    console.error(`[InputArea] ${message}`, error);
};

interface InputContainerProps {
    $hide?: boolean;
}

const InputContainer = styled.div<InputContainerProps>`
    padding: 1.5rem;
    background-color: ${(props) => props.theme.colors.surface};
    /* Add test id */
    &[data-testid] {
      outline: none; 
    }
    border-top: 1px solid ${(props) => props.theme.colors.border};
    display: ${({theme, $hide}) => $hide ? 'none' : 'block'};
    position: sticky;
    bottom: 0;
    z-index: 10;
    backdrop-filter: blur(16px) saturate(180%);
    box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.15);
    background: ${({theme}) => `linear-gradient(to top, 
        ${theme.colors.surface}dd,
        ${theme.colors.background}aa
    )`};
`;
const StyledForm = styled.form`
    display: flex;
    gap: 1rem;
    align-items: flex-start;
`;
const EditorToolbar = styled.div`
    display: flex;
    gap: 0.25rem;
    padding: 0.5rem;
    flex-wrap: wrap;
    background: ${({theme}) => theme.colors.surface};
    border: 1px solid ${({theme}) => theme.colors.border};
    border-bottom: none;
    border-radius: ${({theme}) => theme.sizing.borderRadius.md} 
                  ${({theme}) => theme.sizing.borderRadius.md} 0 0;
    /* Toolbar sections */
    .toolbar-section {
        display: flex;
        gap: 0.25rem;
        padding: 0 0.5rem;
        border-right: 1px solid ${({theme}) => theme.colors.border};
        &:last-child {
            border-right: none;
        }
    }
`;
const ToolbarButton = styled.button`
    padding: 0.5rem;
    background: transparent;
    border: none;
    border-radius: ${({theme}) => theme.sizing.borderRadius.sm};
    cursor: pointer;
    color: ${({theme}) => theme.colors.text};
    &:hover {
        background: ${({theme}) => theme.colors.hover};
    }
    &.active {
        color: ${({theme}) => theme.colors.primary};
    }
`;


const TextArea = styled.textarea`
    width: 100%;
    padding: 0.5rem;
    border-radius: ${(props) => props.theme.sizing.borderRadius.md};
    border: 1px solid ${(props) => props.theme.colors.border};
    font-family: inherit;
    resize: vertical;
    min-height: 40px;
    max-height: ${({theme}) => theme.sizing.console.maxHeight};
    border-radius: 0 0 ${(props) => props.theme.sizing.borderRadius.md} ${(props) => props.theme.sizing.borderRadius.md};
    transition: all 0.3s ease;
    background: ${({theme}) => theme.colors.background};

    &:focus {
        outline: none;
        border-color: ${(props) => props.theme.colors.primary};
        box-shadow: 0 0 0 2px ${({theme}) => `${theme.colors.primary}40`};
        transform: translateY(-1px);
    }
    &:disabled {
        background-color: ${(props) => props.theme.colors.disabled};
        cursor: not-allowed;
    }
`;
const SendButton = styled.button`
    padding: 0.75rem 1.5rem;
    background: ${({theme}) => `linear-gradient(135deg, 
        ${theme.colors.primary}, 
        ${theme.colors.primaryDark}
    )`};
    color: white;
    border: none;
    border-radius: ${(props) => props.theme.sizing.borderRadius.md};
    cursor: pointer;
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    font-weight: ${({theme}) => theme.typography.fontWeight.medium};
    text-transform: uppercase;
    letter-spacing: 0.5px;
    position: relative;
    overflow: hidden;
    min-width: 120px;

    &:disabled {
        opacity: 0.5;
        cursor: not-allowed;
    }
    &:hover:not(:disabled) {
        background: ${({theme}) => `linear-gradient(135deg,
            ${theme.colors.primaryDark},
            ${theme.colors.primary}
        )`};
        transform: translateY(-2px);
        box-shadow: 0 8px 16px ${({theme}) => theme.colors.primary + '40'};
    }

    &:active:not(:disabled) {
        transform: translateY(0);
    }

    &:after {
        content: '';
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: linear-gradient(rgba(255, 255, 255, 0.2), transparent);
        pointer-events: none;
    }
`;

interface InputAreaProps {
    onSendMessage: (message: string) => void;
    isWebSocketConnected?: boolean;
}

const InputArea = memo(function InputArea({onSendMessage, isWebSocketConnected = true}: InputAreaProps) {
    // Remove non-critical initialization log
    const [message, setMessage] = useState('');
    // Debounce preview mode toggling to avoid rapid switching that can trigger flicker
    const [isPreviewMode, setIsPreviewMode] = useState(false);
    const [isCollapsed, setIsCollapsed] = useState(false);
    const config = useSelector((state: RootState) => state.config);
    const messages = useSelector((state: RootState) => state.messages.messages);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const handleToggleCollapse = useCallback(() => {
        setIsCollapsed(prev => {
            const newVal = !prev;
            // If the input area is being expanded (i.e. newVal is false), focus the textarea.
            if (!newVal) {
                setTimeout(() => textAreaRef.current?.focus(), 0);
            }
            return newVal;
        });
    }, []);
    const textAreaRef = React.useRef<HTMLTextAreaElement>(null);
    const shouldHideInput = config.singleInput && messages.length > 0;
    // Add syntax highlighting effect
    React.useEffect(() => {
        if (isPreviewMode) {
            Prism.highlightAll();
        }
    }, [isPreviewMode, message]);
    const insertMarkdown = useCallback((syntax: string) => {
        const textarea = textAreaRef.current;
        if (textarea) {
            const start = textarea.selectionStart;
            const end = textarea.selectionEnd;
            const selectedText = textarea.value.substring(start, end);
            const newText = syntax.replace('$1', selectedText || 'text');
            setMessage(prev => prev.substring(0, start) + newText + prev.substring(end));
            // Set cursor position inside the inserted markdown
            setTimeout(() => {
                const newCursorPos = start + newText.indexOf(selectedText || 'text');
                textarea.focus();
                textarea.setSelectionRange(newCursorPos, newCursorPos + (selectedText || 'text').length);
            }, 0);
        }
    }, []);
    const insertTable = useCallback(() => {
        const tableTemplate = `
| Header 1 | Header 2 | Header 3 |
|----------|----------|----------|
| Cell 1   | Cell 2   | Cell 3   |
| Cell 4   | Cell 5   | Cell 6   |
`.trim() + '\n';
        insertMarkdown(tableTemplate);
    }, [insertMarkdown]);


    const handleSubmit = useCallback((e: React.FormEvent) => {
        e.preventDefault();
        if (isSubmitting || !isWebSocketConnected) return;

        if (message.trim()) {
            setIsSubmitting(true);
            log('Sending message', {
                messageLength: message.length,
                message: message.substring(0, 100) + (message.length > 100 ? '...' : '')
            });
            Promise.resolve(onSendMessage(message)).finally(() => {
                setMessage('');
                setIsSubmitting(false);
            }).catch(error => {
                logError('Failed to send message', error);
            });
        } else {
            log('Empty message submission prevented');
        }
    }, [message, onSendMessage, isSubmitting, isWebSocketConnected]);

    const handleMessageChange = useCallback((e: React.ChangeEvent<HTMLTextAreaElement>) => {
        const newMessage = e.target.value;
        setMessage(newMessage);
    }, []);

    const handleKeyPress = useCallback((e: React.KeyboardEvent<HTMLTextAreaElement>) => {
        if (e.key === 'Enter' && !e.shiftKey && isWebSocketConnected) {
            e.preventDefault();
            handleSubmit(e);
        }
    }, [handleSubmit, isWebSocketConnected]);

    React.useEffect(() => {
        try {
            textAreaRef.current?.focus();
        } catch (error) {
            logError('Failed to focus input on mount', error);
        }
        return () => {
            // Remove non-critical unmounting log
        };
    }, [config]);
    // Create a message to show when disconnected
    const connectionStatusMessage = !isWebSocketConnected ? (
        <div style={{
            color: 'red',
            fontSize: '0.8rem',
            marginTop: '0.5rem',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
        }}>
            ⚠️ Connection lost. Reconnecting... (Your message will be preserved)
        </div>
    ) : null;


    if (isCollapsed) {
        return (
            <InputContainer
                $hide={shouldHideInput}
                data-testid="input-container"
                id="chat-input-container"
                className="collapsed"
            >
                <CollapseButton
                    onClick={handleToggleCollapse}
                    title="Expand input area"
                    data-testid="expand-input"
                >
                    <KeyboardArrowUpIcon fontSize="small"/>
                </CollapseButton>
                <CollapsedPlaceholder onClick={handleToggleCollapse}>
                    Click to expand input
                    {connectionStatusMessage}
                </CollapsedPlaceholder>
            </InputContainer>
        );
    }
    return (
        <InputContainer
            $hide={shouldHideInput}
            data-testid="input-container"
            id="chat-input-container"
            className="expanded"
        >
            <CollapseButton
                onClick={handleToggleCollapse}
                title="Collapse input area"
                data-testid="collapse-input"
            >
                <KeyboardArrowDownIcon fontSize="small"/>

            </CollapseButton>
            <div className="input-area-content">
                <StyledForm onSubmit={handleSubmit}>
                    <div style={{width: '100%'}}>
                        <EditorToolbar>
                            <div className="toolbar-section">
                                <ToolbarButton
                                    type="button"
                                    onClick={() => {
                                        const newValue = !isPreviewMode;
                                        debounce(() => setIsPreviewMode(newValue), 150)();
                                    }}
                                    title={isPreviewMode ? "Edit" : "Preview"}
                                    className={isPreviewMode ? 'active' : ''}
                                >
                                    {isPreviewMode ? <EditIcon fontSize="small"/> : <VisibilityIcon fontSize="small"/>}
                                </ToolbarButton>
                            </div>
                            <div className="toolbar-section">
                                <ToolbarButton
                                    type="button"
                                    onClick={() => insertMarkdown('# $1')}
                                    title="Heading"
                                >
                                    <TitleIcon fontSize="small"/>
                                </ToolbarButton>
                                <ToolbarButton
                                    type="button"
                                    onClick={() => insertMarkdown('**$1**')}
                                    title="Bold"
                                >
                                    <FormatBoldIcon fontSize="small"/>
                                </ToolbarButton>
                                <ToolbarButton
                                    type="button"
                                    onClick={() => insertMarkdown('*$1*')}
                                    title="Italic"
                                >
                                    <FormatItalicIcon fontSize="small"/>
                                </ToolbarButton>
                            </div>
                            <div className="toolbar-section">
                                <ToolbarButton
                                    type="button"
                                    onClick={() => insertMarkdown('`$1`')}
                                    title="Inline Code"
                                >
                                    <CodeIcon fontSize="small"/>
                                </ToolbarButton>
                                <ToolbarButton
                                    type="button"
                                    onClick={() => insertMarkdown('```\n$1\n```')}
                                    title="Code Block"
                                >
                                    <div style={{display: 'flex'}}>
                                        <CodeIcon fontSize="small" style={{marginRight: '2px'}}/>
                                        <CodeIcon fontSize="small"/>
                                    </div>
                                </ToolbarButton>
                            </div>
                            <div className="toolbar-section">
                                <ToolbarButton
                                    type="button"
                                    onClick={() => insertMarkdown('- $1')}
                                    title="Bullet List"
                                >
                                    <FormatListBulletedIcon fontSize="small"/>
                                </ToolbarButton>
                                <ToolbarButton
                                    type="button"
                                    onClick={() => insertMarkdown('> $1')}
                                    title="Quote"
                                >
                                    <FormatQuoteIcon fontSize="small"/>
                                </ToolbarButton>
                                <ToolbarButton
                                    type="button"
                                    onClick={() => insertMarkdown('- [ ] $1')}
                                    title="Task List"
                                >
                                    <CheckBoxIcon fontSize="small"/>
                                </ToolbarButton>
                            </div>
                            <div className="toolbar-section">
                                <ToolbarButton
                                    type="button"
                                    onClick={() => insertMarkdown('[$1](url)')}
                                    title="Link"
                                >
                                    <LinkIcon fontSize="small"/>
                                </ToolbarButton>
                                <ToolbarButton
                                    type="button"
                                    onClick={() => insertMarkdown('![$1](image-url)')}
                                    title="Image"
                                >
                                    <ImageIcon fontSize="small"/>
                                </ToolbarButton>
                                <ToolbarButton
                                    type="button"
                                    onClick={insertTable}
                                    title="Table"
                                >
                                    <TableChartIcon fontSize="small"/>
                                </ToolbarButton>
                            </div>
                        </EditorToolbar>
                        <div className="input-modes">
                            {isPreviewMode ? (
                                <div style={{display: 'block', transition: 'opacity 0.2s ease'}}>
                                    <PreviewContainer>
                                        <ReactMarkdown
                                            remarkPlugins={[remarkGfm]}
                                            components={{
                                                code({node, className, children, ...props}) {
                                                    return (
                                                        <pre className={className}>
                                                            <code {...props}>{children}</code>
                                                        </pre>
                                                    );
                                                }
                                            }}
                                        >
                                            {message}
                                        </ReactMarkdown>
                                    </PreviewContainer>
                                </div>
                            ) : (
                                <div style={{display: 'block', transition: 'opacity 0.2s ease'}}>
                                    <TextArea
                                        ref={textAreaRef}
                                        data-testid="chat-input"
                                        id="chat-input"
                                        value={message}
                                        onChange={handleMessageChange}
                                        onKeyPress={handleKeyPress}
                                        placeholder={isWebSocketConnected
                                            ? "Type a message... (Markdown supported)"
                                            : "Connection lost. Reconnecting..."}
                                        rows={3}
                                        aria-label="Message input"
                                        disabled={isSubmitting}
                                    />
                                </div>
                            )}
                        </div>
                        {connectionStatusMessage}
                        <SendButton
                            type="submit"
                            data-testid="send-button"
                            id="send-message-button"
                            disabled={isSubmitting || !message.trim() || !isWebSocketConnected}
                            aria-label="Send message"
                        >
                            {isWebSocketConnected ? 'Send' : 'Reconnecting...'}
                        </SendButton>
                    </div>
                </StyledForm>
            </div>
        </InputContainer>
    );
});


export default InputArea;