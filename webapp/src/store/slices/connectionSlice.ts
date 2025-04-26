import { createSlice, PayloadAction } from '@reduxjs/toolkit';

interface ConnectionState {
  isConnected: boolean;
  isReconnecting: boolean;
  error: Error | null;
  readyState: number | null;
}

const initialState: ConnectionState = {
  isConnected: false,
  isReconnecting: false,
  error: null,
  readyState: null
};

const connectionSlice = createSlice({
  name: 'connection',
  initialState,
  reducers: {
    setConnectionStatus(state, action: PayloadAction<boolean>) {
      state.isConnected = action.payload;
      if (action.payload) {
        state.isReconnecting = false;
        state.error = null;
      }
    },
    setConnectionError(state, action: PayloadAction<Error | null>) {
      state.error = action.payload;
    },
  }
});

export const {
  setConnectionStatus,
  setConnectionError,
} = connectionSlice.actions;

export default connectionSlice.reducer;