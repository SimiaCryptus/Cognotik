import { Link, NavLink, useNavigate } from 'react-router-dom';
import { ROUTES } from '../../routes';
import './Header.css';

/**
 * Application header with navigation
 */
const Header = () => {
  const navigate = useNavigate();

  const handleSearchSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const formData = new FormData(e.currentTarget);
    const query = formData.get('q') as string;
    
    if (query.trim()) {
      navigate(`${ROUTES.SEARCH}?q=${encodeURIComponent(query.trim())}`);
      e.currentTarget.reset();
    }
  };

  return (
    <header className="header">
      <div className="header__container">
        <div className="header__brand">
          <Link to={ROUTES.HOME} className="header__logo">
            <span className="header__logo-icon">📰</span>
            <span className="header__logo-text">News</span>
          </Link>
        </div>

        <nav className="header__nav">
          <NavLink
            to={ROUTES.HOME}
            className={({ isActive }) =>
              `header__nav-link ${isActive ? 'header__nav-link--active' : ''}`
            }
            end
          >
            Top
          </NavLink>
          <NavLink
            to={ROUTES.NEW}
            className={({ isActive }) =>
              `header__nav-link ${isActive ? 'header__nav-link--active' : ''}`
            }
          >
            New
          </NavLink>
          <NavLink
            to={ROUTES.SEARCH}
            className={({ isActive }) =>
              `header__nav-link ${isActive ? 'header__nav-link--active' : ''}`
            }
          >
            Search
          </NavLink>
        </nav>

        <form className="header__search" onSubmit={handleSearchSubmit}>
          <input
            type="search"
            name="q"
            placeholder="Search stories..."
            className="header__search-input"
            aria-label="Search stories"
          />
          <button type="submit" className="header__search-button" aria-label="Submit search">
            🔍
          </button>
        </form>
      </div>
    </header>
  );
};

export default Header;