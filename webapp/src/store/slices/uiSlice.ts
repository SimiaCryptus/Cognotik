import {createSlice, PayloadAction} from '@reduxjs/toolkit';
import {ColorThemeName, LayoutThemeName} from '../../themes/themes';

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

        // Preserve essential settings like theme, layoutTheme, and verboseMode
        const keysToPreserve = ['theme', 'layoutTheme', 'verboseMode'];
        const preservedItems: Record<string, string | null> = {};

        keysToPreserve.forEach(key => {
            preservedItems[key] = localStorage.getItem(key);
        });

        localStorage.clear();

        keysToPreserve.forEach(key => {
            if (preservedItems[key] !== null) {
                localStorage.setItem(key, preservedItems[key]!);
            }
        });
    }
};

interface UiState {
    theme: ColorThemeName;
    layoutTheme: LayoutThemeName;
    modalOpen: boolean;
    modalType: string | null;
    modalContent: string;
    verboseMode: boolean;
    activeTab: string;
    lastUpdate?: number;
}

const initialState: UiState = {
    theme: (localStorage.getItem('theme') as ColorThemeName | null) || 'main',
    layoutTheme: (localStorage.getItem('layoutTheme') as LayoutThemeName | null) || 'default',
    modalOpen: false,
    modalType: null,
    modalContent: '',
    verboseMode: localStorage.getItem('verboseMode') === 'true',
    activeTab: 'chat',

    lastUpdate: Date.now()
};

const logStateChange = (action: string, payload: any = null, prevState: any = null) => {

    const criticalActions = ['theme', 'verbosemode', 'layouttheme']; // Use consistent casing for comparison
    if (!criticalActions.includes(action.toLowerCase().replace(/\s+/g, ''))) {
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
        setTheme: (state, action: PayloadAction<ColorThemeName>) => {
            logStateChange('Theme', action.payload, state.theme);
            state.theme = action.payload;
            safeStorage.setItem('theme', action.payload);
        },
        setLayoutTheme: (state, action: PayloadAction<LayoutThemeName>) => {
            logStateChange('LayoutTheme', action.payload, state.layoutTheme);
            state.layoutTheme = action.payload;
            safeStorage.setItem('layoutTheme', action.payload);
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
            logStateChange('VerboseMode', newVerboseState, state.verboseMode); // Matched to criticalActions
            safeStorage.setItem('verboseMode', newVerboseState.toString());

            if (typeof document !== 'undefined') {
                document.body.classList.toggle('verbose-mode', newVerboseState);
            }
            state.verboseMode = !state.verboseMode;
        }
    },
});

export const {setTheme, setLayoutTheme, showModal, hideModal, toggleVerbose, setModalContent} = uiSlice.actions;

export default uiSlice.reducer;