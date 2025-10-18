import { useState, useCallback } from 'react';

export interface ErrorState {
  error: Error | null;
  hasError: boolean;
  errorMessage: string;
}

export interface UseErrorHandlerReturn extends ErrorState {
  setError: (error: Error | string | null) => void;
  clearError: () => void;
  handleError: (error: unknown) => void;
}

export const useErrorHandler = (): UseErrorHandlerReturn => {
  const [errorState, setErrorState] = useState<ErrorState>({
    error: null,
    hasError: false,
    errorMessage: '',
  });

  const setError = useCallback((error: Error | string | null) => {
    if (error === null) {
      setErrorState({
        error: null,
        hasError: false,
        errorMessage: '',
      });
      return;
    }

    const errorObj = typeof error === 'string' ? new Error(error) : error;
    const message = errorObj.message || 'An unexpected error occurred';

    setErrorState({
      error: errorObj,
      hasError: true,
      errorMessage: message,
    });
  }, []);

  const clearError = useCallback(() => {
    setErrorState({
      error: null,
      hasError: false,
      errorMessage: '',
    });
  }, []);

  const handleError = useCallback(
    (error: unknown) => {
      console.error('Error caught:', error);

      if (error instanceof Error) {
        setError(error);
      } else if (typeof error === 'string') {
        setError(new Error(error));
      } else if (error && typeof error === 'object' && 'message' in error) {
        setError(new Error(String(error.message)));
      } else {
        setError(new Error('An unexpected error occurred'));
      }
    },
    [setError]
  );

  return {
    ...errorState,
    setError,
    clearError,
    handleError,
  };
};