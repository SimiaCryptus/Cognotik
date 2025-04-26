import React, {useEffect, useRef} from 'react';
import {useSelector} from 'react-redux';
import {useTheme} from '../hooks/useTheme';
import {RootState} from '../store';
import {isArchive} from '../services/appConfig';

import {debounce, resetTabState, updateTabs} from '../utils/tabHandling';
import WebSocketService from "../services/websocket";
import Prism from 'prismjs';
import {Message} from "../types/messages";
import Spinner from './common/Spinner';
import './MessageList.css';

const DEBUG_LOGGING = process.env.NODE_ENV === 'development';
const CONTAINER_ID = 'message-list-' + Math.random().toString(36).substr(2, 9);

/**
 * Extracts message ID and action from clicked elements
 * Supports both data attributes and class-based detection
 */
const extractMessageAction = (target: HTMLElement): { messageId: string | undefined, action: string | undefined } => {
    const messageId = target.getAttribute('data-message-id') ??
        target.getAttribute('data-id') ??
        undefined;
    let action = target.getAttribute('data-message-action') ??
        target.getAttribute('data-action') ??
        undefined;
    if (!action) {
        if (target.classList.contains('href-link')) action = 'link';
        else if (target.classList.contains('play-button')) action = 'run';
        else if (target.classList.contains('regen-button')) action = 'regen';
        else if (target.classList.contains('cancel-button')) action = 'stop';
        else if (target.classList.contains('text-submit-button')) action = 'text-submit';
    }
    return {messageId, action};
};

const handleClick = (e: React.MouseEvent) => {
    const target = e.target as HTMLElement;
    const { messageId, action } = extractMessageAction(target);
    if (messageId && action) {
        if (DEBUG_LOGGING) {
            console.debug('[MessageList] Action clicked:', { messageId, action });
        }
        e.preventDefault();
        e.stopPropagation();
        handleMessageAction(messageId, action);
    }
};

export const handleMessageAction = (messageId: string, action: string) => {
    if (DEBUG_LOGGING) {
        console.debug('[MessageList] Processing action:', {messageId, action});
    }

    if (action === 'text-submit') {
        const input = document.querySelector(`.reply-input[data-id="${messageId}"]`) as HTMLTextAreaElement;
        if (input) {
            const text = input.value;
            if (!text.trim()) return; // Don't send empty messages
            const escapedText = encodeURIComponent(text);
            const message = `!${messageId},userTxt,${escapedText}`;
            WebSocketService.send(message);
            input.value = '';
            // Optional: Add visual feedback
            input.style.height = 'auto';
        }
        return;
    }
    /**
     * Recursively expands referenced messages within content
     * Prevents infinite loops using processedRefs Set
     */
    if (action === 'link') {
        WebSocketService.send(`!${messageId},link`);
        return;
    }
    if (action === 'run') {
        WebSocketService.send(`!${messageId},run`);
        return;
    }
    if (action === 'regen') {
        WebSocketService.send(`!${messageId},regen`);
        return;
    }
    if (action === 'stop') {
        WebSocketService.send(`!${messageId},stop`);
        return;
    }
    WebSocketService.send(`!${messageId},${action}`);
};

interface MessageListProps {
    messages?: Message[];
}

export const expandMessageReferences = (
    content: string,
    messages: Message[],
    processedRefs: Set<string> = new Set<string>()
): string => {

  if (!content) return '';
  const tempDiv = document.createElement('div');
  tempDiv.innerHTML = content;
  // Use an iterative approach with a queue to avoid recursion
  const queue: HTMLElement[] = [tempDiv];
  while (queue.length > 0) {
    const currentNode = queue.shift();
    if (!currentNode) continue;
    const messageID = currentNode.getAttribute("message-id");
    if (messageID && !processedRefs.has(messageID) && messageID.startsWith('z')) {
      processedRefs.add(messageID);
      const referencedMessage = messages.find(m => m.id === messageID);
      if (referencedMessage) {
        // Replace the inner content with the referenced message content
        currentNode.innerHTML = referencedMessage.content;
      } else {
        if (DEBUG_LOGGING) {
          console.warn('[MessageList] Referenced message not found:', messageID);
        }
      }
    }
    // Add all children for further processing (they might contain nested refs)
    Array.from(currentNode.children).forEach(child => {
      if (child instanceof HTMLElement) {
        queue.push(child);
      }
    });
  }
  return tempDiv.innerHTML;
};

const MessageList: React.FC<MessageListProps> = ({messages: propMessages}) => {
    // Add archive mode class to container in archive mode
    const currentTheme = useSelector((state: RootState) => state.ui.theme);
    const containerClassName = `message-list-container${isArchive ? ' archive-mode' : ''} theme-${currentTheme}`;
    // Apply theme class to container
    React.useEffect(() => {
        if (messageListRef.current) {
            messageListRef.current.setAttribute('data-theme', currentTheme);
        }
    }, [currentTheme]);
    // Memoize processMessages function
    const processMessages = React.useCallback((msgs: Message[]) => {
        return msgs
            .filter((message) => message.id && !message.id.startsWith("z"))
            .filter((message) => message.content?.length > 0);
    }, []);

    const verboseMode = useSelector((state: RootState) => state.ui.verboseMode);
    // Add selector memoization
    const storeMessages = useSelector((state: RootState) => state.messages.messages,
        (prev, next) => prev?.length === next?.length &&
            prev?.every((msg, i) => msg.id === next[i].id && msg.version === next[i].version)
    );
    // Optimize messages memo
    const messages = React.useMemo(() => {
        if (Array.isArray(propMessages)) return propMessages;
        if (Array.isArray(storeMessages)) return storeMessages;
        return [];
    }, [propMessages, storeMessages]);
    const messageListRef = useRef<HTMLDivElement>(null);
    const referencesVersions = React.useMemo(() => {
        const versions: Record<string, number> = {};
        messages.forEach(msg => {
            if (msg.id?.startsWith('z')) {
                versions[msg.id] = msg.version || 0;
            }
        });
        return versions;
    }, [messages]);

    const finalMessages = React.useMemo(() => {
            const filteredMessages = processMessages(messages);
            return filteredMessages.map((message) => {
                let content = expandMessageReferences(message.content, messages);
                // Handle verbose content using DOM manipulation
                const tempDiv = document.createElement('div');
                tempDiv.innerHTML = content;
                const verboseElements = tempDiv.querySelectorAll('[class*="verbose"]');
                verboseElements.forEach(element => {
                    const wrapper = document.createElement('span');
                    wrapper.className = `verbose-wrapper${verboseMode ? ' verbose-visible' : ''}`;
                    element.parentNode?.insertBefore(wrapper, element);
                    wrapper.appendChild(element);
                });
                content = tempDiv.innerHTML;
                return {
                    ...message,
                    content
                };
            });
        },
        [messages, referencesVersions, verboseMode]); // Add referencesVersions as dependency

    useEffect(() => {
        let mounted = true;
        let observer: IntersectionObserver | null = null;
        
        if (messageListRef.current) {
            // Use intersection observer for visible elements only
            observer = new IntersectionObserver((entries) => {
                if (!mounted) return;
                entries.forEach(entry => {
                    if (entry.isIntersecting) {
                        const element = entry.target;
            if (element.tagName === 'CODE') {
                // Batch highlighting via requestIdleCallback and ensure one reflow per batch
                requestIdleCallback(() => {
                    if (!mounted) return;
                    Prism.highlightElement(element);
                });
            }
                        if (observer) {
                            observer.unobserve(element);
                        }
                    }
                });
            });
            // Observe code blocks and verbose wrappers
            messageListRef.current.querySelectorAll('pre code').forEach(block => {
                if (observer) {
                    observer.observe(block);
                }
            });
            return () => {
                mounted = false;
                if (observer) {
                    observer.disconnect();
                    observer = null;
                }
            };
        }
        return () => {
            mounted = false;
        };
    }, [messages, verboseMode]);
    const debouncedUpdateTabs = React.useCallback(
        debounce(() => {
            try {
                updateTabs();
            } catch (error) {
                console.error(`[MessageList ${CONTAINER_ID}] Failed to update tabs`, error);
                resetTabState();
            }
        }, 250),
        []
    );

    useTheme();
    console.log('MessageList', 'Rendering component', {hasPropMessages: !!propMessages});

    React.useEffect(() => {
        debouncedUpdateTabs();
    }, [finalMessages]);

    return (
        <div
            data-testid="message-list"
            id="message-list-container"
            ref={messageListRef}
            className={containerClassName}
        >
            {messages.length === 0 && (
                <div className="message-list-loading">
                    <Spinner size="large" aria-label="Loading messages..." />
                </div>
            )}
            {finalMessages.map((message) => {
                return <div
                    key={message.id}
                    className={`message-item ${message.type}`}
                    data-testid={`message-${message.id}`}
                    id={`message-${message.id}`}
                >
                    {<div
                        className="message-content message-body"
                        onClick={!isArchive ? handleClick : undefined}
                        data-testid={`message-content-${message.id}`}
                        dangerouslySetInnerHTML={{
                            __html: message.content
                        }}
                    />}
                    {message.type === 'assistant' && (
                        <div className="reply-form">
                            <textarea
                                className="reply-input"
                                data-id={message.id}
                                placeholder="Type your reply..."
                                onKeyDown={(e) => {
                                    if (e.key === 'Enter' && !e.shiftKey) {
                                        e.preventDefault();
                                        handleMessageAction(message.id, 'text-submit');
                                    }
                                }}
                            />
                            <button
                                className="text-submit-button"
                                data-id={message.id}
                                data-message-action="text-submit"
                            >
                                Send
                            </button>
                        </div>
                    )}
                </div>
            })}
        </div>
    );
};


export default MessageList;