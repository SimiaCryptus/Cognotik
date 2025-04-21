import { createSlice, PayloadAction } from '@reduxjs/toolkit';

// Instead of storing the Error object directly, store a serializable error shape
interface SerializableError {
  message: string;
  name?: string;
  stack?: string;
}

interface ConnectionState {
  isConnected: boolean;
  isReconnecting: boolean;
  error: SerializableError | null;
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
    setReconnectingStatus(state, action: PayloadAction<boolean>) {
      state.isReconnecting = action.payload;
    },
    // Accept either Error, string, or null, but store as serializable object
    setConnectionError(
      state,
      action: PayloadAction<Error | SerializableError | string | null>
    ) {
      if (!action.payload) {
        state.error = null;
      } else if (typeof action.payload === 'string') {
        state.error = { message: action.payload };
      } else if (action.payload instanceof Error) {
        state.error = {
          message: action.payload.message,
          name: action.payload.name,
          stack: action.payload.stack
        };
      } else {
        // Already serializable error shape
        state.error = action.payload;
      }
    },
    setReadyState(state, action: PayloadAction<number | null>) {
      state.readyState = action.payload;
    }
  }
});

export const {
  setConnectionStatus,
  setReconnectingStatus,
  setConnectionError,
  setReadyState
} = connectionSlice.actions;

export default connectionSlice.reducer;