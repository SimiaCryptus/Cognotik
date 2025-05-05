// Main JS for Cognotik website: simplified, consolidated
document.addEventListener('DOMContentLoaded', function() {
    // Logo animation
    if (typeof initLogoAnimation === 'function') initLogoAnimation();
    // Download UI
    if (typeof updateDownloadUI === 'function') updateDownloadUI();
    // Smooth scroll for nav links
    document.querySelectorAll('nav a, a.scroll-link').forEach(link => {
        link.addEventListener('click', function(e) {
            const href = this.getAttribute('href');
            if (href && href.startsWith('#')) {
                const target = document.querySelector(href);
                if (target) {
                    e.preventDefault();
                    target.scrollIntoView({ behavior: 'smooth', block: 'start' });
                }
            }
        });
    });
    // Mobile menu toggle
    const menuToggle = document.querySelector('.mobile-menu-toggle');
    const mainNav = document.querySelector('.main-nav ul');
    if (menuToggle && mainNav) {
        menuToggle.addEventListener('click', function() {
            mainNav.classList.toggle('active');
            menuToggle.classList.toggle('active');
        });
        document.addEventListener('click', function(e) {
            if (!mainNav.contains(e.target) && !menuToggle.contains(e.target)) {
                mainNav.classList.remove('active');
                menuToggle.classList.remove('active');
            }
        });
    }
    // Scroll effect for navbar
    const navbar = document.querySelector('.fixed-top');
    window.addEventListener('scroll', function() {
        if (navbar) {
            if (window.scrollY > 50) navbar.classList.add('scrolled');
            else navbar.classList.remove('scrolled');
        }
    });
});