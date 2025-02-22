// Logging utility for menu components
const logMenuComponent = (componentName: string) => {
    // Only log in development mode
    if (process.env.NODE_ENV === 'development') {
        console.debug(`[Menu] ${componentName} initialized`);
    } else {
        // In production, only log if component fails to load
        try {
            require(`./${componentName}`);
        } catch (error) {
            console.error(`[Menu] Failed to load ${componentName}:`, error);
        }
    }
};

// Log component initialization
logMenuComponent('ThemeMenu');
logMenuComponent('WebSocketMenu');

export {ThemeMenu} from './ThemeMenu';
export {WebSocketMenu} from './WebSocketMenu';