import React, {Component, ErrorInfo, ReactNode} from 'react';

interface Props {
    children: ReactNode;
    FallbackComponent: React.ComponentType<{ error: Error }>;
}

interface State {
    hasError: boolean;
    error: Error | null;
}

class ErrorBoundary extends Component<Props, State> {
    public state: State = {
        hasError: false,
        error: null
    };

    public static getDerivedStateFromError(error: Error): State {
        return {hasError: true, error};
    }

    public componentDidCatch(error: Error, errorInfo: ErrorInfo) {

        console.error({
            timestamp: new Date().toISOString(),
            errorType: 'React Error Boundary',
            errorName: error.name,
            error: {
                message: error.message,

                stack: error.stack ? error.stack.split('\n').slice(0, 3).join('\n') : 'No stack trace available'
            },

            componentStack: errorInfo.componentStack
                ? errorInfo.componentStack
                    .split('\n')
                    .filter(line => line.trim())
                    .slice(0, 3)
                    .join('\n')
                : 'No component stack available',

            environment: process.env.NODE_ENV,
            userAgent: typeof window !== 'undefined' ? window.navigator.userAgent : 'SSR'
        });
    }

    public render() {
        if (this.state.hasError && this.state.error) {
            return <this.props.FallbackComponent error={this.state.error}/>;
        }

        return this.props.children;
    }
}

export default ErrorBoundary;