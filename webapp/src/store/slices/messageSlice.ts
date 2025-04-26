import {createSlice, PayloadAction} from '@reduxjs/toolkit';
import {Message, MessageState, MessageUpdate} from '../../types/messages';
import DOMPurify from 'dompurify';
import {debounce, getAllTabStates, restoreTabStates, updateTabs} from '../../utils/tabHandling';
import Prism from "prismjs";
import mermaid from "mermaid";


const initialState: MessageState = {
    messages: [],
    pendingMessages: [],
    messageQueue: [],
    isProcessing: false,
    messageVersions: {},
    pendingUpdates: [], // Initialize pendingUpdates
};

const sanitizeHtmlContent = (content: string): string => {
    // Sanitizing HTML content to prevent XSS attacks
    return DOMPurify.sanitize(content, {
        ALLOWED_TAGS: ['div', 'span', 'p', 'br', 'b', 'i', 'em', 'strong', 'a', 'ul', 'ol', 'li', 'code', 'pre', 'table', 'tr', 'td', 'th', 'thead', 'tbody',
            'button', 'input', 'label', 'select', 'option', 'textarea', 'code', 'pre', 'div', 'section', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'img', 'figure', 'figcaption',],
        ALLOWED_ATTR: ['class', 'href', 'target', 'data-tab', 'data-for-tab', 'style', 'type', 'value', 'id', 'name',
            'data-message-id', 'data-id', 'data-message-action', 'data-action', 'data-ref-id', 'data-version', 'role', 'message-id'],
    });
};

const debouncedUpdate = debounce(() => {
    restoreTabStates(getAllTabStates());
    updateTabs();
    Prism.highlightAll();
    try {
        mermaid.run();
        document.querySelectorAll('.mermaid').forEach(el => el.classList.add('mermaid-processed'));
    } catch (error) {
        console.error('Failed to render mermaid diagram:', error);
    }
}, 100);

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
            state.messageVersions[messageId] = messageVersion;
            if (existingVersion) {
                // Update the message in place instead of removing and re-adding
                const existingIndex = state.messages.findIndex(msg => msg.id === messageId);
                if (existingIndex !== -1) {
                    if (action.payload.isHtml && action.payload.rawHtml && !action.payload.sanitized) {
                        debouncedUpdate();
                        action.payload.content = sanitizeHtmlContent(action.payload.rawHtml);
                        action.payload.sanitized = true;
                        // console.debug(` HTML content sanitized for message ${action.payload.id}`);
                    }
                    state.messages[existingIndex] = action.payload;
                    // Force version update for reference messages
                    if (messageId.startsWith('z')) {
                        action.payload.version = Date.now();
                    }
                    return;
                }
            }

            if (action.payload.isHtml && action.payload.rawHtml && !action.payload.sanitized) {
                action.payload.content = sanitizeHtmlContent(action.payload.rawHtml);
                action.payload.sanitized = true;
                debouncedUpdate();
            }
            state.messages.push(action.payload);
        },
    },
});

export const {
    addMessage,
} = messageSlice.actions;

export default messageSlice.reducer;