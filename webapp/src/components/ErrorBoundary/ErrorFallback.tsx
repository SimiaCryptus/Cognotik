import React, {useEffect} from 'react';

interface ErrorFallbackProps {
    error: Error;
}

const ErrorFallback: React.FC<ErrorFallbackProps> = ({error}) => {
    useEffect(() => {
        // Log critical error information while avoiding sensitive data
        console.error('Application error:', {
            message: error.message,
            name: error.name,
            stack: process.env.NODE_ENV === 'development' ? error.stack : undefined
        });
    }, [error]);

    return (
        <div role="alert">
            <h2>Something went wrong:</h2>
            <pre>{error.message}</pre>
            {process.env.NODE_ENV === 'development' && (
                <pre style={{whiteSpace: 'pre-wrap'}}>{error.stack}</pre>
            )}
        </div>
    );
};

export default ErrorFallback;