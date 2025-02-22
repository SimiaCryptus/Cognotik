import {render, screen} from '@testing-library/react';
import App from './App';

console.log('🚀 Starting App Component Tests');
beforeAll(() => {
    console.log('📋 Test environment ready');
});

test('renders learn react link', () => {
    render(<App/>);
    const linkElement = screen.getByText(/learn react/i);
    expect(linkElement).toBeInTheDocument();
});

afterAll(() => {
    console.log('✅ App Component Tests completed');
});