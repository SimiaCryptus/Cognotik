import {createSlice, PayloadAction} from '@reduxjs/toolkit';
import {UserInfo} from '../../types';

const initialState: UserInfo = {
    name: '',
    isAuthenticated: false,
    preferences: {},
};

const logStateChange = (actionName: string, prevState: UserInfo, newState: UserInfo) => {
    const changes = Object.keys(newState).reduce((acc: Record<string, { old: any, new: any }>, key) => {
        if (JSON.stringify(prevState[key as keyof UserInfo]) !== JSON.stringify(newState[key as keyof UserInfo])) {
            acc[key] = {
                old: prevState[key as keyof UserInfo],
                new: newState[key as keyof UserInfo]
            };
        }
        return acc;
    }, {});
    console.log(`User State [${actionName}] ${new Date().toISOString()}:`, changes);
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
            state.preferences = {...state.preferences, ...action.payload};
            logStateChange('updatePreferences', prevState, state);
        },
    },
});

export const {setUser, login, logout, updatePreferences} = userSlice.actions;

export default userSlice.reducer;