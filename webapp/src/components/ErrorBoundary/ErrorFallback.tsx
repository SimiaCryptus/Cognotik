import React, {useEffect} from 'react';

interface ErrorFallbackProps {
    error: Error;
}

const ErrorFallback: React.FC<ErrorFallbackProps> = ({error}) => {
    useEffect(() => {

        console.error('[Critical Error]', {
            timestamp: new Date().toISOString(),
            message: error.message,
            name: error.name,
            stack: process.env.NODE_ENV === 'development' ? error.stack : undefined,
            componentStack: error.cause || 'No component stack available'
        });
    }, [error]);

    return (
        <div role="alert" className="error-boundary-fallback">
            <h2>Something went wrong:</h2>
            <pre className="error-message">{error.message}</pre>
            {process.env.NODE_ENV === 'development' && (
                <pre className="error-stack" style={{whiteSpace: 'pre-wrap'}}>{error.stack}</pre>
            )}
        </div>
    );
};

export default ErrorFallback;