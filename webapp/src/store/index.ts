import {configureStore, Middleware} from '@reduxjs/toolkit';
import configReducer from './slices/configSlice';
import messageReducer from './slices/messageSlice';
import uiReducer from './slices/uiSlice';
import userReducer from './slices/userSlice';
import connectionReducer from './slices/connectionSlice';

// Utility function to get formatted timestamp with milliseconds
const getTimestamp = () => {
    const now = new Date();
    return `${now.toLocaleTimeString()}.${now.getMilliseconds().toString().padStart(3, '0')}`;
};
// Define critical action types that should always be logged
const CRITICAL_ACTIONS = [
    'user/login',
    'user/logout',
    'config/update',
    'messages/error'
];

const logger: Middleware = (store) => (next) => (action: unknown) => {
    return next(action);
};

export const store = configureStore({
    reducer: {
        ui: uiReducer,
        config: configReducer,
        messages: messageReducer,
        user: userReducer,
        connection: connectionReducer,
    },
    middleware: (getDefaultMiddleware) =>
        process.env.NODE_ENV === 'development'
            ? getDefaultMiddleware({
                serializableCheck: {
                    // Ignore these action types
                    ignoredActions: ['your-action-type-to-ignore'],
                    // Ignore these field paths in all actions
                    ignoredActionPaths: ['meta.arg', 'payload.timestamp'],
                    // Ignore these paths in the state
                    ignoredPaths: ['items.dates'],
                },
            }).concat(logger)
            : getDefaultMiddleware().concat(logger), // Always include logger for critical actions
});

export type RootState = ReturnType<typeof store.getState>;
// Log store initialization
console.info(`Redux Store Initialized in ${process.env.NODE_ENV} Mode`);