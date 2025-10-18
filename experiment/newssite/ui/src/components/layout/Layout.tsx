import { Outlet } from 'react-router-dom';
import Header from './Header';
import Footer from './Footer';
import './Layout.css';

/**
 * Main layout component
 * Provides consistent structure across all pages
 */
const Layout = () => {
  return (
    <div className="layout">
      <Header />
      <main className="layout__main">
        <div className="layout__container">
          <Outlet />
        </div>
      </main>
      <Footer />
    </div>
  );
};

export default Layout;