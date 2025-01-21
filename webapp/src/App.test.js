import {render, screen} from '@testing-library/react';
import App from './App';

console.log('🚀 App Component Test Suite');
beforeAll(() => {
    console.log('📋 Test environment initialized');
});


beforeEach(() => {
    // Remove redundant per-test start logging
});

afterEach(() => {
    // Remove redundant per-test end logging
});

test('renders learn react link', () => {
    console.group('🧪 Test: Learn React Link');
    render(<App/>);
    const linkElement = screen.getByText(/learn react/i);
    expect(linkElement).toBeInTheDocument();
    console.groupEnd();
});

afterAll(() => {
    console.log('✅ Test suite completed');
});