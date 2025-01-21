import {configureStore, Middleware} from '@reduxjs/toolkit';
import configReducer from './slices/configSlice';
import messageReducer from './slices/messageSlice';
import uiReducer from './slices/uiSlice';
import userReducer from './slices/userSlice';

// Utility function to get formatted timestamp
const getTimestamp = () => new Date().toISOString().split('T')[1].slice(0, -1);

const logger: Middleware = (store) => (next) => (action: unknown) => {
    const timestamp = getTimestamp();
    const actionObj = action as any;
    // Only log in development environment
    if (process.env.NODE_ENV === 'development') {
        console.group(`Redux Action @ ${timestamp}`);
        console.log('Action:', {
            type: actionObj.type,
            payload: actionObj.payload
        });
    }

    const result = next(action);

    process.env.NODE_ENV === 'development' && console.groupEnd();
    return result;
};

export const store = configureStore({
    reducer: {
        ui: uiReducer,
        config: configReducer,
        messages: messageReducer,
        user: userReducer,
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
            : getDefaultMiddleware(),
});

export type RootState = ReturnType<typeof store.getState>;
// Add development-only warning
if (process.env.NODE_ENV === 'development') {
    console.info('Redux Store Initialized in Development Mode');
}