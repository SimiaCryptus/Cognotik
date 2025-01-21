import {createSlice, PayloadAction} from '@reduxjs/toolkit';
import {ThemeName} from '../../themes/themes';
// Helper function to safely interact with localStorage
const safeStorage = {
    setItem(key: string, value: string) {
        try {
            localStorage.setItem(key, value);
            return true;
        } catch (error: unknown) {
            console.warn('[UI Slice] localStorage save failed:', {
                error,
                key
            });
            // Try to clear old items if storage is full
            if (error instanceof Error && error.name === 'QuotaExceededError') {
                this.clearOldItems();
                try {
                    localStorage.setItem(key, value);
                    return true;
                } catch (retryError: unknown) {
                    console.error('[UI Slice] Still failed after clearing storage:', retryError);
                }
            }
            return false;
        }
    },
    getUsedSpace() {
        let total = 0;
        for (const key in localStorage) {
            if (Object.prototype.hasOwnProperty.call(localStorage, key)) {
                total += localStorage[key].length + key.length;
            }
        }
        return (total * 2) / 1024 / 1024; // Approximate MB used
    },
    clearOldItems() {
        const themeKey = 'theme';
        // Keep theme but clear other items
        const currentTheme = localStorage.getItem(themeKey);
        localStorage.clear();
        if (currentTheme) {
            localStorage.setItem(themeKey, currentTheme);
        }
    }
};

interface UiState {
    theme: ThemeName;
    modalOpen: boolean;
    modalType: string | null;
    modalContent: string;
    verboseMode: boolean;
    activeTab: string;
    lastUpdate?: number;
}

const initialState: UiState = {
    theme: 'main',
    modalOpen: false,
    modalType: null,
    modalContent: '',
    verboseMode: localStorage.getItem('verboseMode') === 'true',
    activeTab: 'chat', // Set default tab
    lastUpdate: Date.now()
};

// Only log meaningful state changes
const logStateChange = (action: string, payload: any = null, prevState: any = null) => {
    if (prevState !== null && JSON.stringify(payload) !== JSON.stringify(prevState)) {
        console.log(`[UI Slice] ${action}:`, {
            change: {from: prevState, to: payload}
        });
    }
};

export const uiSlice = createSlice({
    name: 'ui',
    initialState,
    reducers: {
        setActiveTab: (state, action: PayloadAction<string>) => {
            logStateChange('Active tab', action.payload, state.activeTab);
            state.activeTab = action.payload;
        },
        setTheme: (state, action: PayloadAction<ThemeName>) => {
            logStateChange('Theme', action.payload, state.theme);
            state.theme = action.payload;
            safeStorage.setItem('theme', action.payload);
        },
        setDarkMode: (state, action: PayloadAction<boolean>) => {
            const newTheme = action.payload ? 'night' : 'main';
            logStateChange('Dark mode theme', newTheme, state.theme);
            state.theme = newTheme;
            safeStorage.setItem('theme', newTheme);
        },
        showModal: (state, action: PayloadAction<string>) => {
            logStateChange('Modal', action.payload, state.modalType);
            state.modalOpen = true;
            state.modalType = action.payload;
        },
        hideModal: (state) => {
            logStateChange('Modal', null, state.modalType);
            state.modalOpen = false;
            state.modalType = null;
            state.modalContent = '';
        },
        setModalContent: (state, action) => {
            state.modalContent = action.payload;
        },
        toggleVerbose: (state) => {
            const newVerboseState = !state.verboseMode;
            logStateChange('Verbose mode', newVerboseState, state.verboseMode);
            safeStorage.setItem('verboseMode', newVerboseState.toString());
            // Add class to body to allow global CSS targeting
            if (typeof document !== 'undefined') {
                document.body.classList.toggle('verbose-mode', newVerboseState);
            }
            state.verboseMode = !state.verboseMode;
        }
    },
});

export const {setTheme, showModal, hideModal, toggleVerbose, setActiveTab, setModalContent} = uiSlice.actions;

export default uiSlice.reducer;