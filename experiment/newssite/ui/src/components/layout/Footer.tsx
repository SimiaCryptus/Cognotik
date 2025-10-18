import './Footer.css';

/**
 * Application footer
 */
const Footer = () => {
  const currentYear = new Date().getFullYear();

  return (
    <footer className="footer">
      <div className="footer__container">
        <div className="footer__content">
          <p className="footer__text">
            © {currentYear} News App. Built with React & TypeScript.
          </p>
          <div className="footer__links">
            <a
              href="https://github.com"
              target="_blank"
              rel="noopener noreferrer"
              className="footer__link"
            >
              GitHub
            </a>
            <span className="footer__separator">•</span>
            <a
              href="/api"
              target="_blank"
              rel="noopener noreferrer"
              className="footer__link"
            >
              API Docs
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
};

export default Footer;