import {configureStore, Middleware} from '@reduxjs/toolkit';
import configReducer from './slices/configSlice';
import messageReducer from './slices/messageSlice';
import uiReducer from './slices/uiSlice';
import userReducer from './slices/userSlice';
import connectionReducer from './slices/connectionSlice';

const getTimestamp = () => {
    const now = new Date();
    return `${now.toLocaleTimeString()}.${now.getMilliseconds().toString().padStart(3, '0')}`;
};

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

                    ignoredActions: ['your-action-type-to-ignore'],

                    ignoredActionPaths: ['meta.arg', 'payload.timestamp'],

                    ignoredPaths: ['items.dates'],
                },
            }).concat(logger)
            : getDefaultMiddleware().concat(logger),

});

export type RootState = ReturnType<typeof store.getState>;

console.info(`Redux Store Initialized in ${process.env.NODE_ENV} Mode`);