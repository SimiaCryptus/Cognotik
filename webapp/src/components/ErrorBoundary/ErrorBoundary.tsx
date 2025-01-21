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
        // Structured error logging with key information
        console.error({
            type: 'React Error Boundary',
            error: {
                message: error.message,
                stack: error.stack
            },
            componentStack: errorInfo.componentStack
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