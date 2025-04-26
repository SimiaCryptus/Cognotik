import {createSlice, PayloadAction} from '@reduxjs/toolkit';
import {UserInfo} from '../../types';

const initialState: UserInfo = {
    name: '',
    isAuthenticated: false,
    preferences: {} as Record<string, unknown>,
};

const logStateChange = (actionName: string, prevState: UserInfo, newState: UserInfo) => {
    // Only log authentication state changes and critical preference updates
    if (actionName === 'login' || actionName === 'logout') {
        console.log(`Auth State Change [${actionName}] ${new Date().toISOString()}:`, {
            user: newState.name,
            authenticated: newState.isAuthenticated
        });
    } else if (actionName === 'updatePreferences') {
        // Log only if critical preferences are changed
        const criticalPrefs = ['theme', 'notifications', 'privacy'];
        const criticalChanges = Object.keys(newState.preferences ?? {})
            .filter(key => criticalPrefs.includes(key))
            .reduce((acc, key) => ({
                ...acc, [key]: newState.preferences?.[key]
            }), {});
        if (Object.keys(criticalChanges).length > 0) {
            console.log(`Critical Preferences Updated ${new Date().toISOString()}:`, criticalChanges);
        }
    }
};


const userSlice = createSlice({
    name: 'user',
    initialState,
    reducers: {
        setUser: (state: UserInfo, action: PayloadAction<UserInfo>) => {
            const newState = {...state, ...action.payload};
            logStateChange('setUser', state, newState);
            return newState;
        },
        login: (state: UserInfo, action: PayloadAction<{ name: string }>) => {
            const prevState = {...state};
            state.name = action.payload.name;
            state.isAuthenticated = true;
            logStateChange('login', prevState, state);
        },
        logout: (state: UserInfo) => {
            const prevState = {...state};
            state.name = '';
            state.isAuthenticated = false;
            state.preferences = {};
            logStateChange('logout', prevState, state);
        },
        updatePreferences: (state: UserInfo, action: PayloadAction<Record<string, unknown>>) => {
            const prevState = {...state};
            state.preferences = {...(state.preferences ?? {}), ...action.payload};
            logStateChange('updatePreferences', prevState, state);
        },
    },
});

export const {setUser, login, logout, updatePreferences} = userSlice.actions;

export default userSlice.reducer;