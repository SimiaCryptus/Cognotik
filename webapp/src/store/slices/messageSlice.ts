import {createSlice, PayloadAction} from '@reduxjs/toolkit';
import {Message, MessageState} from '../../types/messages';
import DOMPurify from 'dompurify';
import mermaid from "mermaid";

mermaid.initialize({
    startOnLoad: false,
    securityLevel: 'loose',
    theme: 'default',
    logLevel: 3,
});

const initialState: MessageState = {
    messages: [],
    pendingMessages: [],
    messageQueue: [],
    isProcessing: false,
    messageVersions: {},
    pendingUpdates: [],

};

const sanitizeHtmlContent = (content: string): string => {
    if (!content || typeof content !== 'string') {
        console.warn('[MessageSlice] Attempted to sanitize invalid content:', content);
        return '';
    }

    // Preserve math delimiters by temporarily replacing them
    const mathPlaceholders: string[] = [];

    // Helper function to create placeholder
    const createPlaceholder = (match: string): string => {
        const placeholder = `__MATH_PLACEHOLDER_${mathPlaceholders.length}__`;
        mathPlaceholders.push(match);
        return placeholder;
    };

    let processedContent = content;

    // Process display math first ($$...$$) to avoid conflicts with inline math
    processedContent = processedContent.replace(/\$\$([\s\S]*?)\$\$/g, (match) => createPlaceholder(match));

    // Process \[...\] display math
    processedContent = processedContent.replace(/\\\[([\s\S]*?)\\\]/g, (match) => createPlaceholder(match));

    // Process \(...\) inline math
    processedContent = processedContent.replace(/\\\(([\s\S]*?)\\\)/g, (match) => createPlaceholder(match));

    // Process inline math $...$ (single dollars, not already processed)
    // This regex matches $ followed by non-empty content (not starting with $) and ending with $
    processedContent = processedContent.replace(/\$([^\\$\n]+?)\$/g, (match) => createPlaceholder(match));

    // Restore math expressions from placeholders
    let result = processedContent;
    mathPlaceholders.forEach((math, index) => {
        const placeholder = `__MATH_PLACEHOLDER_${index}__`;
        result = result.replace(placeholder, math);
    });
    return result;
};

const messageSlice = createSlice({
    name: 'messages',
    initialState,
    reducers: {
        addMessage: (state: MessageState, action: PayloadAction<Message>) => {
            const messageId = action.payload.id;
            const messageVersion = action.payload.version;
            if (!messageVersion) {
                action.payload.version = Date.now();
            }
            if (state.pendingUpdates && state.pendingUpdates.length > 0) {
                state.pendingUpdates.push(action.payload);
                return;
            }
            const existingVersion = state.messageVersions[messageId];
            state.messageVersions[messageId] = messageVersion || Date.now();
            if (existingVersion) {

                const existingIndex = state.messages.findIndex(msg => msg.id === messageId);
                if (existingIndex !== -1) {
                    if (action.payload.isHtml && action.payload.rawHtml && !action.payload.sanitized) {

                        action.payload.content = typeof action.payload.rawHtml === 'string'

                            ? sanitizeHtmlContent(action.payload.rawHtml)
                            : '';
                        action.payload.sanitized = true;

                    }
                    state.messages[existingIndex] = action.payload;

                    if (messageId.startsWith('z')) {
                        action.payload.version = Date.now();
                    }
                    return;
                }
            }
            if (action.payload.isHtml && action.payload.rawHtml && !action.payload.sanitized) {
                action.payload.content = sanitizeHtmlContent(action.payload.rawHtml);
                action.payload.sanitized = true;
            }
            state.messages.push(action.payload);
        },
    },
});


export const {
    addMessage,
} = messageSlice.actions;
export default messageSlice.reducer;
