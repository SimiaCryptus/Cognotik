import {store} from '../store';
import {showModal, toggleVerbose} from '../store/slices/uiSlice';
import WebSocketService from '../services/websocket';

export const setupUIHandlers = () => {

    const handleKeyboardShortcut = (event: KeyboardEvent) => {
        if ((event.ctrlKey || event.metaKey) && event.shiftKey && event.key === 'V') {
            event.preventDefault();
            store.dispatch(toggleVerbose());

            console.info('Verbose mode toggled via keyboard shortcut');
        }
    };

    document.addEventListener('keydown', handleKeyboardShortcut);

    return () => {
        document.removeEventListener('keydown', handleKeyboardShortcut);
    };

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

    console.info(`WebSocket action triggered: ${action} for message ${messageId}`);
    WebSocketService.send(`!${messageId},${action}`);
};