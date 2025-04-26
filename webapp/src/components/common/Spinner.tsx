import React, {useEffect} from 'react';
import styled from 'styled-components';

export type SpinnerSize = 'small' | 'medium' | 'large';

interface SpinnerProps {
    size?: SpinnerSize;
    className?: string;
    'aria-label'?: string;
}

const SpinnerWrapper = styled.div`
  display: inline-flex;
  align-items: center;
  justify-content: center;
`;

const Spinner: React.FC<SpinnerProps> = ({
                                             size = 'medium',
                                             className = '',
                                             'aria-label': ariaLabel = 'Loading...'
                                         }) => {
    useEffect(() => {
        // Log only in development environment
        if (process.env.NODE_ENV === 'development') {
            console.debug(`Spinner mounted with size: ${size}`);
        }
        return () => {
            if (process.env.NODE_ENV === 'development') {
                console.debug('Spinner unmounted');
            }
        };
    }, [size]);

    const sizeClass = size !== 'medium' ? size : '';

    return (
        <SpinnerWrapper>
            <div
                role="status"
                className={`spinner-border ${sizeClass} ${className}`.trim()}
            >
                <span className="sr-only">{ariaLabel}</span>
            </div>
        </SpinnerWrapper>
    );
};

export default Spinner;