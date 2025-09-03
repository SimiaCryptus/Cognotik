import {configureStore, Middleware} from '@reduxjs/toolkit';
import configReducer from './slices/configSlice';
import messageReducer from './slices/messageSlice';
import uiReducer from './slices/uiSlice';
import userReducer from './slices/userSlice';
import connectionReducer from './slices/connectionSlice';



const logger: Middleware = () => (next) => (action: unknown) => {
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
export type AppDispatch = typeof store.dispatch;

console.info(`Redux Store Initialized in ${process.env.NODE_ENV} Mode`);