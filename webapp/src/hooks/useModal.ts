import {useDispatch} from 'react-redux';
import WebSocketService from '../services/websocket';
import {setModalContent, showModal as showModalAction} from '../store/slices/uiSlice';
import {logger} from '../utils/logger';
import Prism from 'prismjs';
export const useModal = () => {
    const dispatch = useDispatch();
    const highlightCode = () => {
        if (typeof window !== 'undefined') {
            requestAnimationFrame(() => {
                const modalElement = document.querySelector('.modal-content');
                if (modalElement) {
                    Prism.highlightAllUnder(modalElement);
                }
            });
        }
    };
    const getModalUrl = (endpoint: string) => {
        const protocol = window.location.protocol;
        const host = window.location.hostname;
        const port = window.location.port;
        const path = window.location.pathname;
        let url: string;
        if (endpoint.startsWith("/")) {
            url = `${protocol}//${host}:${port}${endpoint}`;
        } else {
            url = `${protocol}//${host}:${port}${path}${endpoint}`;
        }
        if (endpoint.endsWith("/")) {
            url = url + WebSocketService.getSessionId() + '/';
        } else {
            const separator = endpoint.includes('?') ? '&' : '?';
            url = url + separator + 'sessionId=' + WebSocketService.getSessionId();
        }
        return url;
    };
    const openModal = (endpoint: string, event?: React.MouseEvent) => {
        if (event) {
            logger.debug(
                'Modal open prevented default event',
                {endpoint}
            );
            event.preventDefault();
            event.stopPropagation();
        }
        dispatch(showModalAction(endpoint));
        // Set initial loading message for all modal openings
        dispatch(setModalContent('<div class="loading">Loading...</div>'));
        if (endpoint === 'fileIndex/') {
            const iframeSrc = getModalUrl(endpoint);
            // Use requestAnimationFrame to ensure the loading message is rendered before setting iframe
            requestAnimationFrame(() => {
                const iframeContent = `<iframe src="${iframeSrc}" style="width:100%; height:100%; border:none; min-height: 450px;" title="File Index"></iframe>`;
                dispatch(setModalContent(iframeContent));
                // highlightCode() is not called here as content is sandboxed in an iframe
            });
        } else {
            fetch(getModalUrl(endpoint), {
                mode: 'cors',
                credentials: 'include',
                headers: {
                    'Accept': 'text/html,application/json,*/*'
                }
            })
                .then(response => {
                    if (!response.ok) {
                        logger.error('Modal fetch failed', {
                            status: response.status, endpoint
                        });
                        throw new Error(`HTTP error! status: ${response.status}`);
                    }
                    return response.text();
                })
                .then(content => {
                    requestAnimationFrame(() => {
                        dispatch(setModalContent(content));
                        highlightCode(); // Highlight for non-iframe fetched content
                    });
                })
                .catch(error => {
                    logger.error('Modal content load failed', {
                        error: error.message,
                        endpoint,
                        stack: error.stack
                    });
                    dispatch(setModalContent(`<div class="error">Error loading content: ${error.message}</div>`));
                });
        }
    };
    return {openModal, getModalUrl};
};