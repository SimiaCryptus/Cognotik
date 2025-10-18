import { RouterProvider } from 'react-router-dom';
 import { router } from './routes';
import { ErrorBoundary } from './components/common/ErrorBoundary';
import './styles/global.css';

/**
 * Root application component
 * Sets up routing and global error boundary
 */
function App() {
  return (
    <ErrorBoundary>
      <RouterProvider router={router} />
    </ErrorBoundary>
  );
}

export default App;