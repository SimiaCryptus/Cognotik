// Logging utility for menu components
const logMenuComponent = (componentName: string) => {

    if (process.env.NODE_ENV === 'development') {
        console.debug(`[Menu] ${componentName} initialized`);
    } else {

        try {
            require(`./${componentName}`);
        } catch (error) {
            console.error(`[Menu] Failed to load ${componentName}:`, error);
        }
    }
};

logMenuComponent('ThemeMenu');
logMenuComponent('WebSocketMenu');

export {ThemeMenu} from './ThemeMenu';
export {WebSocketMenu} from './WebSocketMenu';