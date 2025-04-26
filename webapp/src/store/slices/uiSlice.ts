import {createSlice, PayloadAction} from '@reduxjs/toolkit';
import {ThemeName} from '../../themes/themes';
// Helper function to safely interact with localStorage
const safeStorage = {
    setItem(key: string, value: string) {
        try {
            localStorage.setItem(key, value);
            return true;
        } catch (error: unknown) {
            console.error('[UI Slice] localStorage save failed:', {
                error: error instanceof Error ? error.message : String(error),
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
    // Only log critical state changes
    const criticalChanges = ['theme', 'verboseMode'];
    if (!criticalChanges.includes(action.toLowerCase())) {
        return;
    }
    if (prevState !== null && JSON.stringify(payload) !== JSON.stringify(prevState)) {
        console.debug(`[UI Slice] ${action}:`, 
            `${prevState} → ${payload}`
        );
    }
};

export const uiSlice = createSlice({
    name: 'ui',
    initialState,
    reducers: {
        setTheme: (state, action: PayloadAction<ThemeName>) => {
            logStateChange('Theme', action.payload, state.theme);
            state.theme = action.payload;
            safeStorage.setItem('theme', action.payload);
        },
        showModal: (state, action: PayloadAction<string>) => {
            state.modalOpen = true;
            state.modalType = action.payload;
        },
        hideModal: (state) => {
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

export const {setTheme, showModal, hideModal, toggleVerbose, setModalContent} = uiSlice.actions;

export default uiSlice.reducer;