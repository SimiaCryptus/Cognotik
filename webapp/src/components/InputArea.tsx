import React, {memo, useCallback, useState} from 'react';
import styled from 'styled-components';
import {useSelector} from 'react-redux';
import {RootState} from '../store';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import Prism from 'prismjs';
import { 
    FaBold, 
    FaItalic, 
    FaCode, 
    FaListUl, 
    FaQuoteRight, 
    FaLink, 
    FaHeading,
    FaTable,
    FaCheckSquare,
    FaImage,
    FaEye,
    FaChevronUp,
    FaChevronDown,
    FaEdit
} from 'react-icons/fa';
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
            console.log(`[InputArea] ${message}`, data);
        } else {
            console.log(`[InputArea] ${message}`);
        }
    }
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
}

const InputArea = memo(function InputArea({onSendMessage}: InputAreaProps) {
    log('Initializing component');
    const [message, setMessage] = useState('');
    const [isPreviewMode, setIsPreviewMode] = useState(false);
    const [isCollapsed, setIsCollapsed] = useState(false);
    const config = useSelector((state: RootState) => state.config);
    const messages = useSelector((state: RootState) => state.messages.messages);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const handleToggleCollapse = useCallback(() => {
        setIsCollapsed(prev => !prev);
        if (isCollapsed) {
            // Focus textarea when expanding
            setTimeout(() => textAreaRef.current?.focus(), 0);
        }
    }, [isCollapsed]);
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
        if (isSubmitting) return;

        log('Submit attempt');
        if (message.trim()) {
            setIsSubmitting(true);
            log('Sending message', {
                messageLength: message.length,
                message: message.substring(0, 100) + (message.length > 100 ? '...' : '')
            });
            Promise.resolve(onSendMessage(message)).finally(() => {
                setMessage('');
                setIsSubmitting(false);
                log('Message sent and form reset');
            });
        } else {
            log('Empty message, not sending');
        }
    }, [message, onSendMessage]);

    const handleMessageChange = useCallback((e: React.ChangeEvent<HTMLTextAreaElement>) => {
        const newMessage = e.target.value;
        log('Message changed', {
            length: newMessage.length,
            isEmpty: newMessage.trim().length === 0
        });
        setMessage(newMessage);
    }, []);

    const handleKeyPress = useCallback((e: React.KeyboardEvent<HTMLTextAreaElement>) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSubmit(e);
        }
    }, [handleSubmit]);

    React.useEffect(() => {
        log('Component mounted', {configState: config});
        // Focus the textarea when component mounts
        textAreaRef.current?.focus();
        return () => {
            log('Component unmounting');
        };
    }, [config]);


    return (
        <>
        {isCollapsed ? (
            <CollapsedPlaceholder 
                onClick={handleToggleCollapse}
                data-testid="expand-input"
            >
                <FaChevronUp /> Click to expand input area
            </CollapsedPlaceholder>
        ) : (
        <InputContainer 
            $hide={shouldHideInput}
            data-testid="input-container"
            id="chat-input-container"
        >
            <CollapseButton
                onClick={handleToggleCollapse}
                title="Collapse input area"
                data-testid="collapse-input"
            >
                <FaChevronDown />
            </CollapseButton>
            <StyledForm onSubmit={handleSubmit}>
                <div style={{ width: '100%' }}>
                    <EditorToolbar>
                        <div className="toolbar-section">
                            <ToolbarButton
                                type="button"
                                onClick={() => setIsPreviewMode(!isPreviewMode)}
                                title={isPreviewMode ? "Edit" : "Preview"}
                                className={isPreviewMode ? 'active' : ''}
                            >
                                {isPreviewMode ? <FaEdit /> : <FaEye />}
                            </ToolbarButton>
                        </div>
                        <div className="toolbar-section">
                            <ToolbarButton 
                                type="button"
                                onClick={() => insertMarkdown('# $1')}
                                title="Heading"
                            >
                                <FaHeading />
                            </ToolbarButton>
                        <ToolbarButton 
                            type="button"
                            onClick={() => insertMarkdown('**$1**')}
                            title="Bold"
                        >
                            <FaBold />
                        </ToolbarButton>
                        <ToolbarButton 
                            type="button"
                            onClick={() => insertMarkdown('*$1*')}
                            title="Italic"
                        >
                            <FaItalic />
                        </ToolbarButton>
                        </div>
                        <div className="toolbar-section">
                        <ToolbarButton 
                            type="button"
                            onClick={() => insertMarkdown('`$1`')}
                            title="Inline Code"
                        >
                            <FaCode />
                        </ToolbarButton>
                        <ToolbarButton 
                            type="button"
                            onClick={() => insertMarkdown('```\n$1\n```')}
                            title="Code Block"
                        >
                            <FaCode style={{ marginRight: '2px' }} /><FaCode />
                        </ToolbarButton>
                        </div>
                        <div className="toolbar-section">
                            <ToolbarButton 
                                type="button"
                                onClick={() => insertMarkdown('- $1')}
                                title="Bullet List"
                            >
                                <FaListUl />
                            </ToolbarButton>
                            <ToolbarButton 
                                type="button"
                                onClick={() => insertMarkdown('> $1')}
                                title="Quote"
                            >
                                <FaQuoteRight />
                            </ToolbarButton>
                            <ToolbarButton 
                                type="button"
                                onClick={() => insertMarkdown('- [ ] $1')}
                                title="Task List"
                            >
                                <FaCheckSquare />
                            </ToolbarButton>
                        </div>
                        <div className="toolbar-section">
                            <ToolbarButton 
                                type="button"
                                onClick={() => insertMarkdown('[$1](url)')}
                                title="Link"
                            >
                                <FaLink />
                            </ToolbarButton>
                            <ToolbarButton 
                                type="button"
                                onClick={() => insertMarkdown('![$1](image-url)')}
                                title="Image"
                            >
                                <FaImage />
                            </ToolbarButton>
                            <ToolbarButton 
                                type="button"
                                onClick={insertTable}
                                title="Table"
                            >
                                <FaTable />
                            </ToolbarButton>
                        </div>
                    </EditorToolbar>
                {isPreviewMode ? (
                    <PreviewContainer>
                        <ReactMarkdown 
                            remarkPlugins={[remarkGfm]}
                            components={{
                                code({node, className, children, ...props}) {
                                    return <pre className={className}>
                                            <code {...props}>{children}</code>
                                        </pre>;
                                }
                            }}
                        >
                            {message}
                        </ReactMarkdown>
                    </PreviewContainer>
                ) : (
                    <TextArea
                        ref={textAreaRef}
                        data-testid="chat-input"
                        id="chat-input"
                        value={message}
                        onChange={handleMessageChange}
                        onKeyPress={handleKeyPress}
                        placeholder="Type a message... (Markdown supported)"
                        rows={3}
                        aria-label="Message input"
                        disabled={isSubmitting}
                    />
                )}
                <SendButton
                    type="submit"
                    data-testid="send-button" 
                    id="send-message-button"
                    disabled={isSubmitting || !message.trim()}
                    aria-label="Send message"
                >
                    Send
                </SendButton>
                </div>
            </StyledForm>
        </InputContainer>
        )}
        </>
    );
});


export default InputArea;