// Logging utility for menu components
const logMenuComponent = (componentName: string) => {
    console.log(`[Menu] Loaded ${componentName}`);
    if (process.env.NODE_ENV === 'development') {
        console.debug(`[Menu] ${componentName} initialized in ${process.env.NODE_ENV} mode`);
    }
};

// Log component initialization
logMenuComponent('ThemeMenu');
logMenuComponent('WebSocketMenu');

export {ThemeMenu} from './ThemeMenu';
export {WebSocketMenu} from './WebSocketMenu';