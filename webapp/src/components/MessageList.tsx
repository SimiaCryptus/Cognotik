import React, {useEffect, useRef} from 'react';
import {useSelector} from 'react-redux';
import {useTheme} from '../hooks/useTheme';
import {RootState} from '../store';

import { isArchive } from '../services/appConfig';

import { debounce, updateTabs, restoreTabStates, getAllTabStates, initNewCollapsibleElements } from '../utils/tabHandling';
import WebSocketService from "../services/websocket";
import Prism from 'prismjs';
import mermaid from 'mermaid';
import { Message } from "../types/messages";
import Spinner from './common/Spinner';
import './MessageList.css';

const DEBUG_LOGGING = process.env.NODE_ENV === 'development';
const DEBUG_TAB_SYSTEM = process.env.NODE_ENV === 'development';
const CONTAINER_ID = 'message-list-' + Math.random().toString(36).substr(2, 9);

const extractMessageAction = (target: HTMLElement): { messageId: string | undefined, action: string | undefined } => {
    const messageId = target.getAttribute('data-message-id') ??
        target.closest('[data-message-id]')?.getAttribute('data-message-id') ?? // Check parents
        target.getAttribute('data-id') ??
        undefined;
    let action = target.getAttribute('data-message-action') ??
        target.getAttribute('data-action') ??
        undefined;
    if (!action) {
        if (target.classList.contains('href-link')) action = 'link';
        else if (target.closest('.href-link')) action = 'link'; // Check parents
        else if (target.classList.contains('play-button')) action = 'run';
        else if (target.classList.contains('regen-button')) action = 'regen';
        else if (target.classList.contains('cancel-button')) action = 'stop';
        else if (target.classList.contains('text-submit-button')) action = 'text-submit';
    }
    return {messageId, action};
};

const handleClick = (e: React.MouseEvent) => {
    const target = e.target as HTMLElement;
    const {messageId, action} = extractMessageAction(target);
    if (messageId && action) {
        if (DEBUG_LOGGING) {
            console.debug('[MessageList] Action clicked:', {messageId, action});
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
            if (!text.trim()) return;

            const escapedText = encodeURIComponent(text);
            const message = `!${messageId},userTxt,${escapedText}`;
            WebSocketService.send(message);
            input.value = '';

            input.style.height = 'auto';
        }
        return;
    }
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

    const queue: HTMLElement[] = [tempDiv];
    while (queue.length > 0) {
        const currentNode = queue.shift();
        if (!currentNode) continue;
        const messageID = currentNode.getAttribute("message-id");
        if (messageID && !processedRefs.has(messageID) && messageID.startsWith('z')) {
            processedRefs.add(messageID);
            const referencedMessage = messages.find(m => m.id === messageID);
            if (referencedMessage) {

                currentNode.innerHTML = referencedMessage.content;
            } else {
                if (DEBUG_LOGGING) {
                    console.warn('[MessageList] Referenced message not found:', messageID);
                }
            }
        }

        Array.from(currentNode.children).forEach(child => {
            if (child instanceof HTMLElement) {
                queue.push(child);
            }
        });
    }
    return tempDiv.innerHTML;
};
const renderMermaidDiagrams = (container: HTMLElement | null) => {
    if (!container) return;
    try {
        const mermaidElements = container.querySelectorAll('.mermaid:not(.mermaid-processed)');
        if (mermaidElements.length > 0) {
            if (DEBUG_LOGGING) console.debug(`[Mermaid] Found ${mermaidElements.length} diagrams to render.`);
            mermaidElements.forEach((el, index) => {
                // Check if element is visible (basic check)
                if (el instanceof HTMLElement && el.offsetParent !== null) {
                    const id = `mermaid-${Date.now()}-${index}`;
                    const source = el.textContent || '';
                    // Clear existing content before rendering
                    el.innerHTML = '';
                    mermaid.render(id, source)
                        .then(({ svg }) => {
                            el.innerHTML = svg;
                            el.classList.add('mermaid-processed');
                        })
                        .catch(err => {
                            console.warn('[Mermaid] Failed to render diagram:', err?.message, el);
                            el.classList.add('mermaid-error');
                        });
                }
            });
        }
    } catch (error) {
        console.error('[Mermaid] Failed to render mermaid diagrams:', error);
    }
};


const MessageList: React.FC<MessageListProps> = ({messages: propMessages}) => {

    const currentTheme = useSelector((state: RootState) => state.ui.theme);
    const containerClassName = `message-list-container${isArchive ? ' archive-mode' : ''} theme-${currentTheme}`;

    React.useEffect(() => {
        if (messageListRef.current) {
            messageListRef.current.setAttribute('data-theme', currentTheme);
        }
    }, [currentTheme]);

    const processMessages = React.useCallback((msgs: Message[]) => {
        return msgs
            .filter((message) => message.id && !message.id.startsWith("z"))
            .filter((message) => message.content?.length > 0);
    }, []);

    const verboseMode = useSelector((state: RootState) => state.ui.verboseMode);

    const storeMessages = useSelector((state: RootState) => state.messages.messages,
        (prev, next) => prev?.length === next?.length &&
            prev?.every((msg, i) => msg.id === next[i].id && msg.version === next[i].version)
    );

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

                let content = message.content || '';
                if (content && message.id && !message.id.startsWith('z')) {
                    content = expandMessageReferences(content, messages);
                }

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
        [messages, referencesVersions, verboseMode]);


    useEffect(() => {
        let mounted = true;
        let observer: IntersectionObserver | null = null;

        if (messageListRef.current) {

            observer = new IntersectionObserver((entries) => {
                if (!mounted) return;
                entries.forEach(entry => {
                    if (entry.isIntersecting) {
                        const element = entry.target;
                        if (element.tagName === 'CODE') {

                            requestIdleCallback(() => {
                                if (!mounted) return;
                                // Check if already highlighted to avoid double processing
                                if (!element.classList.contains('language-none') && !element.closest('.token')) {
                                    Prism.highlightElement(element);
                                }
                            });
                        }
                        if (observer) {
                            observer.unobserve(element);
                        }
                    }
                });
            });
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
    }, [finalMessages]);

    const debouncedPostRenderUpdate = React.useCallback(
        debounce(() => {
            try {
                if (!messageListRef.current) return;
                if (DEBUG_TAB_SYSTEM) {
                    console.debug(`[MessageList ${CONTAINER_ID}] Running debounced post-render update`);
                }
                restoreTabStates(getAllTabStates());
                updateTabs();
                initNewCollapsibleElements();
                requestIdleCallback(() => {
                    if (messageListRef.current) {
                        messageListRef.current.querySelectorAll('pre code:not(.prismjs-processed)').forEach(block => {
                             if (block instanceof HTMLElement && block.offsetParent !== null) {
                                 Prism.highlightElement(block);
                                 block.classList.add('prismjs-processed'); // Mark as processed
                             }
                        });
                    }
                });
                renderMermaidDiagrams(messageListRef.current);
            } catch (updateError) {
                console.error(`[MessageList ${CONTAINER_ID}] Error during debounced post-render update:`, updateError);
            }
        }, 250),
        []
    );

    useTheme();
    if (DEBUG_LOGGING) {
        console.debug('[MessageList] Rendering component', {hasPropMessages: !!propMessages});
    }

    React.useEffect(() => {
        if (DEBUG_LOGGING) {
             console.debug(`[MessageList ${CONTAINER_ID}] Scheduling post-render update due to finalMessages change`, {
                 messageCount: finalMessages.length
             });
        }
        debouncedPostRenderUpdate();
    }, [finalMessages, debouncedPostRenderUpdate]);

    React.useEffect(() => {
        if (!messageListRef.current) return;
        const observer = new MutationObserver((mutations) => {
            let tabsAdded = false;
            mutations.forEach(mutation => {
                if (mutation.type === 'childList') {
                    mutation.addedNodes.forEach(node => {
                        if (node instanceof HTMLElement) {
                            if (node.querySelector('.tabs-container') || node.classList.contains('tabs-container')) {
                                tabsAdded = true;
                            }
                        }
                    });
                }
            });
            if (tabsAdded) {
                if (DEBUG_TAB_SYSTEM) {
                    console.debug(`[MessageList ${CONTAINER_ID}] Tabs added via DOM mutation, scheduling post-render update`);
                }
                debouncedPostRenderUpdate();
            }
        });
        observer.observe(messageListRef.current, {
            childList: true,
            subtree: true
        });
        return () => observer.disconnect();
   }, [debouncedPostRenderUpdate]);
    const handleMessageClick = React.useCallback((e: React.MouseEvent) => {
        const target = e.target as HTMLElement;
        if (target.closest('.tab-button') && target.closest('.tabs')) {
            return; // Let the tab system handle this
        }
        handleClick(e);
    }, []);


    return (
        <div
            data-testid="message-list"
            id="message-list-container"
            ref={messageListRef}
            className={containerClassName}
        >
            {messages.length === 0 && (
                <div className="message-list-loading">
                    <Spinner size="large" aria-label="Loading messages..."/>
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
                        onClick={!isArchive ? handleMessageClick : undefined}
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