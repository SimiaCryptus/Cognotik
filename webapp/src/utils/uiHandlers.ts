import {store} from '../store';
import {showModal, toggleVerbose} from '../store/slices/uiSlice';
import WebSocketService from '../services/websocket';
import {debounce} from './tabHandling';

export const setupUIHandlers = () => {

    // Create debounced handler outside event listener
    const handleKeyboardShortcut = debounce((event: KeyboardEvent) => {
        if ((event.ctrlKey || event.metaKey) && event.shiftKey && event.key === 'V') {
            event.preventDefault();
            store.dispatch(toggleVerbose());
            // Log state changes that affect app behavior
            console.info('Verbose mode toggled via keyboard shortcut');
        }
    }, 250);

    // Keyboard shortcuts
    document.addEventListener('keydown', handleKeyboardShortcut);
    // Cleanup function to remove event listeners
    return () => {
        document.removeEventListener('keydown', handleKeyboardShortcut);
    };

    // Modal handlers
    document.addEventListener('click', (event) => {
        const target = event.target as HTMLElement;
        if (target.matches('[data-modal]')) {
            event.preventDefault();
            const modalType = target.getAttribute('data-modal');
            if (modalType) {
                store.dispatch(showModal(modalType));
            }
        }
    });

    // Message action handlers
    document.addEventListener('click', (event) => {
        const target = event.target as HTMLElement;
        const messageAction = target.getAttribute('data-message-action');
        const messageId = target.getAttribute('data-message-id');

        if (messageAction && messageId) {
            event.preventDefault();
            handleMessageAction(messageId, messageAction);
        }
    });

};

const handleMessageAction = (messageId: string, action: string) => {
    // Log important WebSocket operations that could affect system state
    console.info(`WebSocket action triggered: ${action} for message ${messageId}`);
    WebSocketService.send(`!${messageId},${action}`);
};