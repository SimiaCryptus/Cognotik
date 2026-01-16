# HTML Generation Transcript

## Creating HTML File: `index.html`

### Step 1: Generating HTML Structure

**Prompt:**
```
You are an expert web developer tasked with creating a complete, self-contained HTML file.

## Requirements:
Create a simple landing page for a coffee shop with a menu and contact section.

## Context from Related Files:


## Previous Task Results:


## Instructions:
1. Create a complete HTML5 document structure with proper semantic elements
2. Include appropriate meta tags (viewport, charset, etc.)
3. Add class names to elements that will need styling or JavaScript interaction
4. Use descriptive, semantic class names (e.g., "nav-menu", "hero-section", "card-container")
5. Include placeholder comments for where CSS and JavaScript will be added
6. Do NOT include any CSS or JavaScript yet - just the HTML structure with classes
7. Add comments to explain the purpose of major sections

## Output Format:
Provide the HTML structure within a code block:
```html
<!DOCTYPE html>
<html>
<head>
    <!-- CSS will be added here -->
</head>
<body>
    <!-- HTML structure with classes -->
</body>
<!-- JavaScript will be added here -->
</html>
```
```

**Response:**
```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Bean & Brew | Artisanal Coffee Shop</title>
    <!-- CSS will be added here -->
</head>
<body>

    <!-- Navigation Header -->
    <header class="site-header">
        <nav class="nav-container">
            <div class="nav-logo">
                <a href="#" class="logo-link">Bean & Brew</a>
            </div>
            <ul class="nav-menu">
                <li class="nav-item"><a href="#home" class="nav-link">Home</a></li>
                <li class="nav-item"><a href="#menu" class="nav-link">Menu</a></li>
                <li class="nav-item"><a href="#contact" class="nav-link">Contact</a></li>
            </ul>
            <button class="mobile-menu-toggle" aria-label="Toggle Menu">
                <span class="hamburger-icon"></span>
            </button>
        </nav>
    </header>

    <main>
        <!-- Hero Section -->
        <section id="home" class="hero-section">
            <div class="hero-content">
                <h1 class="hero-title">Wake Up to Perfection</h1>
                <p class="hero-subtitle">Hand-roasted beans, expertly brewed, served with a smile.</p>
                <a href="#menu" class="cta-button">View Our Menu</a>
            </div>
        </section>

        <!-- Menu Section -->
        <section id="menu" class="menu-section">
            <div class="section-header">
                <h2 class="section-title">Our Menu</h2>
                <p class="section-description">Freshly brewed coffee and handmade treats.</p>
            </div>

            <div class="menu-grid">
                <!-- Coffee Category -->
                <div class="menu-category">
                    <h3 class="category-title">Coffee Classics</h3>
                    <ul class="menu-list">
                        <li class="menu-item">
                            <div class="item-info">
                                <span class="item-name">Espresso</span>
                                <span class="item-price">$3.50</span>
                            </div>
                            <p class="item-description">Rich and bold double shot.</p>
                        </li>
                        <li class="menu-item">
                            <div class="item-info">
                                <span class="item-name">Cappuccino</span>
                                <span class="item-price">$4.50</span>
                            </div>
                            <p class="item-description">Equal parts espresso, steamed milk, and foam.</p>
                        </li>
                        <li class="menu-item">
                            <div class="item-info">
                                <span class="item-name">Caramel Macchiato</span>
                                <span class="item-price">$5.25</span>
                            </div>
                            <p class="item-description">Freshly steamed milk with vanilla-flavored syrup marked with espresso.</p>
                        </li>
                    </ul>
                </div>

                <!-- Pastries Category -->
                <div class="menu-category">
                    <h3 class="category-title">Fresh Pastries</h3>
                    <ul class="menu-list">
                        <li class="menu-item">
                            <div class="item-info">
                                <span class="item-name">Butter Croissant</span>
                                <span class="item-price">$3.75</span>
                            </div>
                            <p class="item-description">Flaky, golden-brown, and buttery.</p>
                        </li>
                        <li class="menu-item">
                            <div class="item-info">
                                <span class="item-name">Blueberry Muffin</span>
                                <span class="item-price">$4.00</span>
                            </div>
                            <p class="item-description">Bursting with fresh berries and a sugar crumble.</p>
                        </li>
                    </ul>
                </div>
            </div>
        </section>

        <!-- Contact Section -->
        <section id="contact" class="contact-section">
            <div class="section-header">
                <h2 class="section-title">Visit Us</h2>
                <p class="section-description">We'd love to hear from you or see you in person.</p>
            </div>

            <div class="contact-container">
                <!-- Contact Information -->
                <div class="contact-info">
                    <div class="info-block">
                        <h4 class="info-label">Location</h4>
                        <p class="info-text">123 Coffee Lane, Brewtown, BT 56789</p>
                    </div>
                    <div class="info-block">
                        <h4 class="info-label">Hours</h4>
                        <p class="info-text">Mon - Fri: 7am - 7pm</p>
                        <p class="info-text">Sat - Sun: 8am - 6pm</p>
                    </div>
                </div>

                <!-- Contact Form -->
                <form class="contact-form">
                    <div class="form-group">
                        <label for="name" class="form-label">Name</label>
                        <input type="text" id="name" name="name" class="form-input" required>
                    </div>
                    <div class="form-group">
                        <label for="email" class="form-label">Email</label>
                        <input type="email" id="email" name="email" class="form-input" required>
                    </div>
                    <div class="form-group">
                        <label for="message" class="form-label">Message</label>
                        <textarea id="message" name="message" class="form-textarea" rows="5" required></textarea>
                    </div>
                    <button type="submit" class="submit-button">Send Message</button>
                </form>
            </div>
        </section>
    </main>

    <!-- Footer -->
    <footer class="site-footer">
        <div class="footer-content">
            <p class="copyright">&copy; 2023 Bean & Brew Coffee Shop. All rights reserved.</p>
            <div class="social-links">
                <a href="#" class="social-link">Instagram</a>
                <a href="#" class="social-link">Facebook</a>
                <a href="#" class="social-link">Twitter</a>
            </div>
        </div>
    </footer>

</body>
<!-- JavaScript will be added here -->
</html>
```

### Step 2: Generating JavaScript

**Prompt:**
```
Based on the following HTML structure, generate the JavaScript code needed for interactivity.

## HTML Structure:
```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Bean & Brew | Artisanal Coffee Shop</title>
    <!-- CSS will be added here -->
</head>
<body>

    <!-- Navigation Header -->
    <header class="site-header">
        <nav class="nav-container">
            <div class="nav-logo">
                <a href="#" class="logo-link">Bean & Brew</a>
            </div>
            <ul class="nav-menu">
                <li class="nav-item"><a href="#home" class="nav-link">Home</a></li>
                <li class="nav-item"><a href="#menu" class="nav-link">Menu</a></li>
                <li class="nav-item"><a href="#contact" class="nav-link">Contact</a></li>
            </ul>
            <button class="mobile-menu-toggle" aria-label="Toggle Menu">
                <span class="hamburger-icon"></span>
            </button>
        </nav>
    </header>

    <main>
        <!-- Hero Section -->
        <section id="home" class="hero-section">
            <div class="hero-content">
                <h1 class="hero-title">Wake Up to Perfection</h1>
                <p class="hero-subtitle">Hand-roasted beans, expertly brewed, served with a smile.</p>
                <a href="#menu" class="cta-button">View Our Menu</a>
            </div>
        </section>

        <!-- Menu Section -->
        <section id="menu" class="menu-section">
            <div class="section-header">
                <h2 class="section-title">Our Menu</h2>
                <p class="section-description">Freshly brewed coffee and handmade treats.</p>
            </div>

            <div class="menu-grid">
                <!-- Coffee Category -->
                <div class="menu-category">
                    <h3 class="category-title">Coffee Classics</h3>
                    <ul class="menu-list">
                        <li class="menu-item">
                            <div class="item-info">
                                <span class="item-name">Espresso</span>
                                <span class="item-price">$3.50</span>
                            </div>
                            <p class="item-description">Rich and bold double shot.</p>
                        </li>
                        <li class="menu-item">
                            <div class="item-info">
                                <span class="item-name">Cappuccino</span>
                                <span class="item-price">$4.50</span>
                            </div>
                            <p class="item-description">Equal parts espresso, steamed milk, and foam.</p>
                        </li>
                        <li class="menu-item">
                            <div class="item-info">
                                <span class="item-name">Caramel Macchiato</span>
                                <span class="item-price">$5.25</span>
                            </div>
                            <p class="item-description">Freshly steamed milk with vanilla-flavored syrup marked with espresso.</p>
                        </li>
                    </ul>
                </div>

                <!-- Pastries Category -->
                <div class="menu-category">
                    <h3 class="category-title">Fresh Pastries</h3>
                    <ul class="menu-list">
                        <li class="menu-item">
                            <div class="item-info">
                                <span class="item-name">Butter Croissant</span>
                                <span class="item-price">$3.75</span>
                            </div>
                            <p class="item-description">Flaky, golden-brown, and buttery.</p>
                        </li>
                        <li class="menu-item">
                            <div class="item-info">
                                <span class="item-name">Blueberry Muffin</span>
                                <span class="item-price">$4.00</span>
                            </div>
                            <p class="item-description">Bursting with fresh berries and a sugar crumble.</p>
                        </li>
                    </ul>
                </div>
            </div>
        </section>

        <!-- Contact Section -->
        <section id="contact" class="contact-section">
            <div class="section-header">
                <h2 class="section-title">Visit Us</h2>
                <p class="section-description">We'd love to hear from you or see you in person.</p>
            </div>

            <div class="contact-container">
                <!-- Contact Information -->
                <div class="contact-info">
                    <div class="info-block">
                        <h4 class="info-label">Location</h4>
                        <p class="info-text">123 Coffee Lane, Brewtown, BT 56789</p>
                    </div>
                    <div class="info-block">
                        <h4 class="info-label">Hours</h4>
                        <p class="info-text">Mon - Fri: 7am - 7pm</p>
                        <p class="info-text">Sat - Sun: 8am - 6pm</p>
                    </div>
                </div>

                <!-- Contact Form -->
                <form class="contact-form">
                    <div class="form-group">
                        <label for="name" class="form-label">Name</label>
                        <input type="text" id="name" name="name" class="form-input" required>
                    </div>
                    <div class="form-group">
                        <label for="email" class="form-label">Email</label>
                        <input type="email" id="email" name="email" class="form-input" required>
                    </div>
                    <div class="form-group">
                        <label for="message" class="form-label">Message</label>
                        <textarea id="message" name="message" class="form-textarea" rows="5" required></textarea>
                    </div>
                    <button type="submit" class="submit-button">Send Message</button>
                </form>
            </div>
        </section>
    </main>

    <!-- Footer -->
    <footer class="site-footer">
        <div class="footer-content">
            <p class="copyright">&copy; 2023 Bean & Brew Coffee Shop. All rights reserved.</p>
            <div class="social-links">
                <a href="#" class="social-link">Instagram</a>
                <a href="#" class="social-link">Facebook</a>
                <a href="#" class="social-link">Twitter</a>
            </div>
        </div>
    </footer>

</body>
<!-- JavaScript will be added here -->
</html>
```

## Requirements:
Create a simple landing page for a coffee shop with a menu and contact section.

## Instructions:
1. Generate JavaScript that adds interactivity to the HTML elements
2. Use modern JavaScript (ES6+) features
3. Add event listeners for user interactions
4. Include any necessary DOM manipulation
5. Add comments to explain the functionality
6. Ensure the code is efficient and follows best practices

## Output Format:
Provide only the JavaScript code within a code block:
```javascript
// JavaScript code here
```
```

**Response:**
```javascript
/**
 * Bean & Brew - Interactive Functionality
 */

document.addEventListener('DOMContentLoaded', () => {
    // --- Element Selectors ---
    const header = document.querySelector('.site-header');
    const mobileMenuToggle = document.querySelector('.mobile-menu-toggle');
    const navMenu = document.querySelector('.nav-menu');
    const navLinks = document.querySelectorAll('.nav-link, .cta-button');
    const contactForm = document.querySelector('.contact-form');

    /**
     * Mobile Navigation Toggle
     * Handles opening and closing the mobile menu drawer
     */
    const toggleMobileMenu = () => {
        const isOpen = navMenu.classList.toggle('active');
        mobileMenuToggle.classList.toggle('is-active');
        
        // Update accessibility attribute
        mobileMenuToggle.setAttribute('aria-expanded', isOpen);
        
        // Prevent body scroll when menu is open
        document.body.style.overflow = isOpen ? 'hidden' : '';
    };

    mobileMenuToggle.addEventListener('click', toggleMobileMenu);

    /**
     * Smooth Scrolling
     * Intercepts internal anchor links for a smooth transition to sections
     */
    navLinks.forEach(link => {
        link.addEventListener('click', (e) => {
            const targetId = link.getAttribute('href');
            
            // Only process internal links
            if (targetId.startsWith('#')) {
                e.preventDefault();
                const targetElement = document.querySelector(targetId);

                if (targetElement) {
                    // Close mobile menu if it's open
                    if (navMenu.classList.contains('active')) {
                        toggleMobileMenu();
                    }

                    // Calculate header height for offset
                    const headerOffset = header.offsetHeight;
                    const elementPosition = targetElement.getBoundingClientRect().top;
                    const offsetPosition = elementPosition + window.pageYOffset - headerOffset;

                    window.scrollTo({
                        top: offsetPosition,
                        behavior: 'smooth'
                    });
                }
            }
        });
    });

    /**
     * Sticky Header Effect
     * Adds a class to the header when the user scrolls down
     */
    const handleHeaderScroll = () => {
        if (window.scrollY > 50) {
            header.classList.add('header-scrolled');
        } else {
            header.classList.remove('header-scrolled');
        }
    };

    window.addEventListener('scroll', handleHeaderScroll);

    /**
     * Contact Form Submission
     * Handles form validation and provides user feedback
     */
    if (contactForm) {
        contactForm.addEventListener('submit', (e) => {
            e.preventDefault();

            // Get form data
            const formData = new FormData(contactForm);
            const data = Object.fromEntries(formData.entries());

            // Simple UI Feedback (Simulating an API call)
            const submitBtn = contactForm.querySelector('.submit-button');
            const originalBtnText = submitBtn.textContent;
            
            submitBtn.disabled = true;
            submitBtn.textContent = 'Sending...';

            // Simulate network delay
            setTimeout(() => {
                console.log('Form Data Received:', data);
                
                alert(`Thank you, ${data.name}! Your message has been sent successfully.`);
                
                // Reset form and button
                contactForm.reset();
                submitBtn.disabled = false;
                submitBtn.textContent = originalBtnText;
            }, 1500);
        });
    }

    /**
     * Active Link Highlighting
     * Uses Intersection Observer to highlight the current section in the nav
     */
    const observerOptions = {
        root: null,
        rootMargin: '-20% 0px -70% 0px', // Adjust to trigger when section is in view
        threshold: 0
    };

    const observerCallback = (entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                const id = entry.target.getAttribute('id');
                document.querySelectorAll('.nav-link').forEach(link => {
                    link.classList.remove('active-link');
                    if (link.getAttribute('href') === `#${id}`) {
                        link.classList.add('active-link');
                    }
                });
            }
        });
    };

    const observer = new IntersectionObserver(observerCallback, observerOptions);
    document.querySelectorAll('section[id]').forEach(section => observer.observe(section));
});
```

### Step 3: Generating CSS

**Prompt:**
```
Based on the following HTML structure, generate the CSS styling.

## HTML Structure:
```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Bean & Brew | Artisanal Coffee Shop</title>
    <!-- CSS will be added here -->
</head>
<body>

    <!-- Navigation Header -->
    <header class="site-header">
        <nav class="nav-container">
            <div class="nav-logo">
                <a href="#" class="logo-link">Bean & Brew</a>
            </div>
            <ul class="nav-menu">
                <li class="nav-item"><a href="#home" class="nav-link">Home</a></li>
                <li class="nav-item"><a href="#menu" class="nav-link">Menu</a></li>
                <li class="nav-item"><a href="#contact" class="nav-link">Contact</a></li>
            </ul>
            <button class="mobile-menu-toggle" aria-label="Toggle Menu">
                <span class="hamburger-icon"></span>
            </button>
        </nav>
    </header>

    <main>
        <!-- Hero Section -->
        <section id="home" class="hero-section">
            <div class="hero-content">
                <h1 class="hero-title">Wake Up to Perfection</h1>
                <p class="hero-subtitle">Hand-roasted beans, expertly brewed, served with a smile.</p>
                <a href="#menu" class="cta-button">View Our Menu</a>
            </div>
        </section>

        <!-- Menu Section -->
        <section id="menu" class="menu-section">
            <div class="section-header">
                <h2 class="section-title">Our Menu</h2>
                <p class="section-description">Freshly brewed coffee and handmade treats.</p>
            </div>

            <div class="menu-grid">
                <!-- Coffee Category -->
                <div class="menu-category">
                    <h3 class="category-title">Coffee Classics</h3>
                    <ul class="menu-list">
                        <li class="menu-item">
                            <div class="item-info">
                                <span class="item-name">Espresso</span>
                                <span class="item-price">$3.50</span>
                            </div>
                            <p class="item-description">Rich and bold double shot.</p>
                        </li>
                        <li class="menu-item">
                            <div class="item-info">
                                <span class="item-name">Cappuccino</span>
                                <span class="item-price">$4.50</span>
                            </div>
                            <p class="item-description">Equal parts espresso, steamed milk, and foam.</p>
                        </li>
                        <li class="menu-item">
                            <div class="item-info">
                                <span class="item-name">Caramel Macchiato</span>
                                <span class="item-price">$5.25</span>
                            </div>
                            <p class="item-description">Freshly steamed milk with vanilla-flavored syrup marked with espresso.</p>
                        </li>
                    </ul>
                </div>

                <!-- Pastries Category -->
                <div class="menu-category">
                    <h3 class="category-title">Fresh Pastries</h3>
                    <ul class="menu-list">
                        <li class="menu-item">
                            <div class="item-info">
                                <span class="item-name">Butter Croissant</span>
                                <span class="item-price">$3.75</span>
                            </div>
                            <p class="item-description">Flaky, golden-brown, and buttery.</p>
                        </li>
                        <li class="menu-item">
                            <div class="item-info">
                                <span class="item-name">Blueberry Muffin</span>
                                <span class="item-price">$4.00</span>
                            </div>
                            <p class="item-description">Bursting with fresh berries and a sugar crumble.</p>
                        </li>
                    </ul>
                </div>
            </div>
        </section>

        <!-- Contact Section -->
        <section id="contact" class="contact-section">
            <div class="section-header">
                <h2 class="section-title">Visit Us</h2>
                <p class="section-description">We'd love to hear from you or see you in person.</p>
            </div>

            <div class="contact-container">
                <!-- Contact Information -->
                <div class="contact-info">
                    <div class="info-block">
                        <h4 class="info-label">Location</h4>
                        <p class="info-text">123 Coffee Lane, Brewtown, BT 56789</p>
                    </div>
                    <div class="info-block">
                        <h4 class="info-label">Hours</h4>
                        <p class="info-text">Mon - Fri: 7am - 7pm</p>
                        <p class="info-text">Sat - Sun: 8am - 6pm</p>
                    </div>
                </div>

                <!-- Contact Form -->
                <form class="contact-form">
                    <div class="form-group">
                        <label for="name" class="form-label">Name</label>
                        <input type="text" id="name" name="name" class="form-input" required>
                    </div>
                    <div class="form-group">
                        <label for="email" class="form-label">Email</label>
                        <input type="email" id="email" name="email" class="form-input" required>
                    </div>
                    <div class="form-group">
                        <label for="message" class="form-label">Message</label>
                        <textarea id="message" name="message" class="form-textarea" rows="5" required></textarea>
                    </div>
                    <button type="submit" class="submit-button">Send Message</button>
                </form>
            </div>
        </section>
    </main>

    <!-- Footer -->
    <footer class="site-footer">
        <div class="footer-content">
            <p class="copyright">&copy; 2023 Bean & Brew Coffee Shop. All rights reserved.</p>
            <div class="social-links">
                <a href="#" class="social-link">Instagram</a>
                <a href="#" class="social-link">Facebook</a>
                <a href="#" class="social-link">Twitter</a>
            </div>
        </div>
    </footer>

</body>
<!-- JavaScript will be added here -->
</html>
```

## Requirements:
Create a simple landing page for a coffee shop with a menu and contact section.

## Instructions:
1. Generate CSS that styles all the HTML elements
2. Create a visually appealing, modern design
3. Ensure responsive design (mobile-first approach)
4. Use CSS Grid and/or Flexbox for layouts
5. Include hover effects and transitions where appropriate
6. Use a consistent color scheme and typography
7. Add comments to organize the CSS sections
8. Follow CSS best practices and naming conventions

## Output Format:
Provide only the CSS code within a code block:
```css
/* CSS code here */
```
```

**Response:**
```css
/* 
========================================
    Variables & Base Styles
========================================
*/

:root {
    --primary-color: #4a3728;    /* Deep Coffee Brown */
    --secondary-color: #c8a165;  /* Golden Latte */
    --accent-color: #8b5e3c;     /* Medium Roast */
    --bg-color: #fdfaf7;         /* Cream White */
    --text-color: #2c241e;       /* Dark Chocolate */
    --light-text: #6d5d50;       /* Muted Brown */
    --white: #ffffff;
    --transition: all 0.3s ease;
    --max-width: 1200px;
    --header-height: 80px;
}

* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

html {
    scroll-behavior: smooth;
}

body {
    font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    line-height: 1.6;
    color: var(--text-color);
    background-color: var(--bg-color);
}

h1, h2, h3, h4 {
    font-family: 'Georgia', serif;
    line-height: 1.2;
}

ul {
    list-style: none;
}

a {
    text-decoration: none;
    color: inherit;
    transition: var(--transition);
}

img {
    max-width: 100%;
    display: block;
}

/* 
========================================
    Navigation Header
========================================
*/

.site-header {
    background-color: var(--white);
    height: var(--header-height);
    position: fixed;
    top: 0;
    width: 100%;
    z-index: 1000;
    box-shadow: 0 2px 10px rgba(0,0,0,0.05);
}

.nav-container {
    max-width: var(--max-width);
    margin: 0 auto;
    padding: 0 2rem;
    height: 100%;
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.logo-link {
    font-family: 'Georgia', serif;
    font-size: 1.5rem;
    font-weight: bold;
    color: var(--primary-color);
    letter-spacing: 1px;
}

.nav-menu {
    display: flex;
    gap: 2rem;
}

.nav-link {
    font-weight: 500;
    font-size: 0.95rem;
    color: var(--text-color);
    text-transform: uppercase;
    letter-spacing: 1px;
}

.nav-link:hover {
    color: var(--secondary-color);
}

.mobile-menu-toggle {
    display: none; /* Hidden on desktop */
    background: none;
    border: none;
    cursor: pointer;
}

/* 
========================================
    Hero Section
========================================
*/

.hero-section {
    height: 100vh;
    background: linear-gradient(rgba(0,0,0,0.5), rgba(0,0,0,0.5)), 
                url('https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&q=80&w=1920') center/cover no-repeat;
    display: flex;
    align-items: center;
    justify-content: center;
    text-align: center;
    color: var(--white);
    padding: 0 1rem;
    margin-top: -var(--header-height);
}

.hero-title {
    font-size: clamp(2.5rem, 8vw, 4.5rem);
    margin-bottom: 1rem;
}

.hero-subtitle {
    font-size: clamp(1rem, 3vw, 1.25rem);
    margin-bottom: 2.5rem;
    font-weight: 300;
    max-width: 600px;
    margin-left: auto;
    margin-right: auto;
}

.cta-button {
    display: inline-block;
    background-color: var(--secondary-color);
    color: var(--white);
    padding: 1rem 2.5rem;
    border-radius: 50px;
    font-weight: bold;
    text-transform: uppercase;
    letter-spacing: 1px;
    transition: var(--transition);
}

.cta-button:hover {
    background-color: var(--accent-color);
    transform: translateY(-3px);
    box-shadow: 0 5px 15px rgba(0,0,0,0.2);
}

/* 
========================================
    Common Section Styles
========================================
*/

section {
    padding: 100px 2rem;
    max-width: var(--max-width);
    margin: 0 auto;
}

.section-header {
    text-align: center;
    margin-bottom: 4rem;
}

.section-title {
    font-size: 2.5rem;
    color: var(--primary-color);
    margin-bottom: 1rem;
    position: relative;
    display: inline-block;
}

.section-title::after {
    content: '';
    position: absolute;
    bottom: -10px;
    left: 50%;
    transform: translateX(-50%);
    width: 60px;
    height: 3px;
    background-color: var(--secondary-color);
}

.section-description {
    color: var(--light-text);
    font-size: 1.1rem;
}

/* 
========================================
    Menu Section
========================================
*/

.menu-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
    gap: 4rem;
}

.category-title {
    font-size: 1.75rem;
    color: var(--accent-color);
    margin-bottom: 2rem;
    border-bottom: 1px solid #eee;
    padding-bottom: 0.5rem;
}

.menu-item {
    margin-bottom: 2rem;
}

.item-info {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    margin-bottom: 0.5rem;
}

.item-name {
    font-weight: bold;
    font-size: 1.1rem;
    color: var(--primary-color);
}

.item-price {
    font-weight: bold;
    color: var(--accent-color);
}

.item-description {
    font-size: 0.9rem;
    color: var(--light-text);
    font-style: italic;
}

/* 
========================================
    Contact Section
========================================
*/

.contact-section {
    background-color: var(--white);
    max-width: 100%;
}

.contact-container {
    max-width: var(--max-width);
    margin: 0 auto;
    display: grid;
    grid-template-columns: 1fr 1.5fr;
    gap: 4rem;
}

.info-block {
    margin-bottom: 2.5rem;
}

.info-label {
    font-size: 1.2rem;
    color: var(--primary-color);
    margin-bottom: 0.5rem;
}

.info-text {
    color: var(--light-text);
}

.contact-form {
    display: flex;
    flex-direction: column;
    gap: 1.5rem;
}

.form-group {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
}

.form-label {
    font-weight: 600;
    font-size: 0.9rem;
}

.form-input, 
.form-textarea {
    padding: 0.8rem;
    border: 1px solid #ddd;
    border-radius: 4px;
    font-family: inherit;
    transition: var(--transition);
}

.form-input:focus, 
.form-textarea:focus {
    outline: none;
    border-color: var(--secondary-color);
    box-shadow: 0 0 0 3px rgba(200, 161, 101, 0.1);
}

.submit-button {
    background-color: var(--primary-color);
    color: var(--white);
    padding: 1rem;
    border: none;
    border-radius: 4px;
    font-weight: bold;
    cursor: pointer;
    text-transform: uppercase;
    letter-spacing: 1px;
    transition: var(--transition);
}

.submit-button:hover {
    background-color: var(--accent-color);
}

/* 
========================================
    Footer
========================================
*/

.site-footer {
    background-color: var(--primary-color);
    color: var(--white);
    padding: 4rem 2rem;
}

.footer-content {
    max-width: var(--max-width);
    margin: 0 auto;
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 2rem;
}

.copyright {
    font-size: 0.9rem;
    opacity: 0.8;
}

.social-links {
    display: flex;
    gap: 1.5rem;
}

.social-link {
    font-size: 0.9rem;
    opacity: 0.8;
}

.social-link:hover {
    opacity: 1;
    color: var(--secondary-color);
}

/* 
========================================
    Responsive Design
========================================
*/

@media (max-width: 768px) {
    .nav-menu {
        display: none; /* In a real app, you'd use JS to toggle a mobile menu */
    }

    .mobile-menu-toggle {
        display: block;
        width: 30px;
        height: 20px;
        position: relative;
    }

    .hamburger-icon, 
    .hamburger-icon::before, 
    .hamburger-icon::after {
        content: '';
        position: absolute;
        width: 100%;
        height: 2px;
        background-color: var(--primary-color);
        transition: var(--transition);
    }

    .hamburger-icon { top: 50%; transform: translateY(-50%); }
    .hamburger-icon::before { top: -8px; }
    .hamburger-icon::after { bottom: -8px; }

    .contact-container {
        grid-template-columns: 1fr;
    }

    .footer-content {
        flex-direction: column;
        text-align: center;
    }

    section {
        padding: 60px 1.5rem;
    }
}

@media (max-width: 480px) {
    .hero-section {
        height: 80vh;
    }
    
    .section-title {
        font-size: 2rem;
    }
}
```

### Step 4: Final HTML Output

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Bean & Brew | Artisanal Coffee Shop</title>
    <!-- CSS will be added here -->

    <style>
        /* 
        ========================================
            Variables & Base Styles
        ========================================
        */
        
        :root {
            --primary-color: #4a3728;    /* Deep Coffee Brown */
            --secondary-color: #c8a165;  /* Golden Latte */
            --accent-color: #8b5e3c;     /* Medium Roast */
            --bg-color: #fdfaf7;         /* Cream White */
            --text-color: #2c241e;       /* Dark Chocolate */
            --light-text: #6d5d50;       /* Muted Brown */
            --white: #ffffff;
            --transition: all 0.3s ease;
            --max-width: 1200px;
            --header-height: 80px;
        }
        
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        html {
            scroll-behavior: smooth;
        }
        
        body {
            font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
            line-height: 1.6;
            color: var(--text-color);
            background-color: var(--bg-color);
        }
        
        h1, h2, h3, h4 {
            font-family: 'Georgia', serif;
            line-height: 1.2;
        }
        
        ul {
            list-style: none;
        }
        
        a {
            text-decoration: none;
            color: inherit;
            transition: var(--transition);
        }
        
        img {
            max-width: 100%;
            display: block;
        }
        
        /* 
        ========================================
            Navigation Header
        ========================================
        */
        
        .site-header {
            background-color: var(--white);
            height: var(--header-height);
            position: fixed;
            top: 0;
            width: 100%;
            z-index: 1000;
            box-shadow: 0 2px 10px rgba(0,0,0,0.05);
        }
        
        .nav-container {
            max-width: var(--max-width);
            margin: 0 auto;
            padding: 0 2rem;
            height: 100%;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        
        .logo-link {
            font-family: 'Georgia', serif;
            font-size: 1.5rem;
            font-weight: bold;
            color: var(--primary-color);
            letter-spacing: 1px;
        }
        
        .nav-menu {
            display: flex;
            gap: 2rem;
        }
        
        .nav-link {
            font-weight: 500;
            font-size: 0.95rem;
            color: var(--text-color);
            text-transform: uppercase;
            letter-spacing: 1px;
        }
        
        .nav-link:hover {
            color: var(--secondary-color);
        }
        
        .mobile-menu-toggle {
            display: none; /* Hidden on desktop */
            background: none;
            border: none;
            cursor: pointer;
        }
        
        /* 
        ========================================
            Hero Section
        ========================================
        */
        
        .hero-section {
            height: 100vh;
            background: linear-gradient(rgba(0,0,0,0.5), rgba(0,0,0,0.5)), 
                        url('https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&q=80&w=1920') center/cover no-repeat;
            display: flex;
            align-items: center;
            justify-content: center;
            text-align: center;
            color: var(--white);
            padding: 0 1rem;
            margin-top: -var(--header-height);
        }
        
        .hero-title {
            font-size: clamp(2.5rem, 8vw, 4.5rem);
            margin-bottom: 1rem;
        }
        
        .hero-subtitle {
            font-size: clamp(1rem, 3vw, 1.25rem);
            margin-bottom: 2.5rem;
            font-weight: 300;
            max-width: 600px;
            margin-left: auto;
            margin-right: auto;
        }
        
        .cta-button {
            display: inline-block;
            background-color: var(--secondary-color);
            color: var(--white);
            padding: 1rem 2.5rem;
            border-radius: 50px;
            font-weight: bold;
            text-transform: uppercase;
            letter-spacing: 1px;
            transition: var(--transition);
        }
        
        .cta-button:hover {
            background-color: var(--accent-color);
            transform: translateY(-3px);
            box-shadow: 0 5px 15px rgba(0,0,0,0.2);
        }
        
        /* 
        ========================================
            Common Section Styles
        ========================================
        */
        
        section {
            padding: 100px 2rem;
            max-width: var(--max-width);
            margin: 0 auto;
        }
        
        .section-header {
            text-align: center;
            margin-bottom: 4rem;
        }
        
        .section-title {
            font-size: 2.5rem;
            color: var(--primary-color);
            margin-bottom: 1rem;
            position: relative;
            display: inline-block;
        }
        
        .section-title::after {
            content: '';
            position: absolute;
            bottom: -10px;
            left: 50%;
            transform: translateX(-50%);
            width: 60px;
            height: 3px;
            background-color: var(--secondary-color);
        }
        
        .section-description {
            color: var(--light-text);
            font-size: 1.1rem;
        }
        
        /* 
        ========================================
            Menu Section
        ========================================
        */
        
        .menu-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 4rem;
        }
        
        .category-title {
            font-size: 1.75rem;
            color: var(--accent-color);
            margin-bottom: 2rem;
            border-bottom: 1px solid #eee;
            padding-bottom: 0.5rem;
        }
        
        .menu-item {
            margin-bottom: 2rem;
        }
        
        .item-info {
            display: flex;
            justify-content: space-between;
            align-items: baseline;
            margin-bottom: 0.5rem;
        }
        
        .item-name {
            font-weight: bold;
            font-size: 1.1rem;
            color: var(--primary-color);
        }
        
        .item-price {
            font-weight: bold;
            color: var(--accent-color);
        }
        
        .item-description {
            font-size: 0.9rem;
            color: var(--light-text);
            font-style: italic;
        }
        
        /* 
        ========================================
            Contact Section
        ========================================
        */
        
        .contact-section {
            background-color: var(--white);
            max-width: 100%;
        }
        
        .contact-container {
            max-width: var(--max-width);
            margin: 0 auto;
            display: grid;
            grid-template-columns: 1fr 1.5fr;
            gap: 4rem;
        }
        
        .info-block {
            margin-bottom: 2.5rem;
        }
        
        .info-label {
            font-size: 1.2rem;
            color: var(--primary-color);
            margin-bottom: 0.5rem;
        }
        
        .info-text {
            color: var(--light-text);
        }
        
        .contact-form {
            display: flex;
            flex-direction: column;
            gap: 1.5rem;
        }
        
        .form-group {
            display: flex;
            flex-direction: column;
            gap: 0.5rem;
        }
        
        .form-label {
            font-weight: 600;
            font-size: 0.9rem;
        }
        
        .form-input, 
        .form-textarea {
            padding: 0.8rem;
            border: 1px solid #ddd;
            border-radius: 4px;
            font-family: inherit;
            transition: var(--transition);
        }
        
        .form-input:focus, 
        .form-textarea:focus {
            outline: none;
            border-color: var(--secondary-color);
            box-shadow: 0 0 0 3px rgba(200, 161, 101, 0.1);
        }
        
        .submit-button {
            background-color: var(--primary-color);
            color: var(--white);
            padding: 1rem;
            border: none;
            border-radius: 4px;
            font-weight: bold;
            cursor: pointer;
            text-transform: uppercase;
            letter-spacing: 1px;
            transition: var(--transition);
        }
        
        .submit-button:hover {
            background-color: var(--accent-color);
        }
        
        /* 
        ========================================
            Footer
        ========================================
        */
        
        .site-footer {
            background-color: var(--primary-color);
            color: var(--white);
            padding: 4rem 2rem;
        }
        
        .footer-content {
            max-width: var(--max-width);
            margin: 0 auto;
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 2rem;
        }
        
        .copyright {
            font-size: 0.9rem;
            opacity: 0.8;
        }
        
        .social-links {
            display: flex;
            gap: 1.5rem;
        }
        
        .social-link {
            font-size: 0.9rem;
            opacity: 0.8;
        }
        
        .social-link:hover {
            opacity: 1;
            color: var(--secondary-color);
        }
        
        /* 
        ========================================
            Responsive Design
        ========================================
        */
        
        @media (max-width: 768px) {
            .nav-menu {
                display: none; /* In a real app, you'd use JS to toggle a mobile menu */
            }
        
            .mobile-menu-toggle {
                display: block;
                width: 30px;
                height: 20px;
                position: relative;
            }
        
            .hamburger-icon, 
            .hamburger-icon::before, 
            .hamburger-icon::after {
                content: '';
                position: absolute;
                width: 100%;
                height: 2px;
                background-color: var(--primary-color);
                transition: var(--transition);
            }
        
            .hamburger-icon { top: 50%; transform: translateY(-50%); }
            .hamburger-icon::before { top: -8px; }
            .hamburger-icon::after { bottom: -8px; }
        
            .contact-container {
                grid-template-columns: 1fr;
            }
        
            .footer-content {
                flex-direction: column;
                text-align: center;
            }
        
            section {
                padding: 60px 1.5rem;
            }
        }
        
        @media (max-width: 480px) {
            .hero-section {
                height: 80vh;
            }
        
            .section-title {
                font-size: 2rem;
            }
        }
    </style>
</head>
<body>

    <!-- Navigation Header -->
    <header class="site-header">
        <nav class="nav-container">
            <div class="nav-logo">
                <a href="#" class="logo-link">Bean & Brew</a>
            </div>
            <ul class="nav-menu">
                <li class="nav-item"><a href="#home" class="nav-link">Home</a></li>
                <li class="nav-item"><a href="#menu" class="nav-link">Menu</a></li>
                <li class="nav-item"><a href="#contact" class="nav-link">Contact</a></li>
            </ul>
            <button class="mobile-menu-toggle" aria-label="Toggle Menu">
                <span class="hamburger-icon"></span>
            </button>
        </nav>
    </header>

    <main>
        <!-- Hero Section -->
        <section id="home" class="hero-section">
            <div class="hero-content">
                <h1 class="hero-title">Wake Up to Perfection</h1>
                <p class="hero-subtitle">Hand-roasted beans, expertly brewed, served with a smile.</p>
                <a href="#menu" class="cta-button">View Our Menu</a>
            </div>
        </section>

        <!-- Menu Section -->
        <section id="menu" class="menu-section">
            <div class="section-header">
                <h2 class="section-title">Our Menu</h2>
                <p class="section-description">Freshly brewed coffee and handmade treats.</p>
            </div>

            <div class="menu-grid">
                <!-- Coffee Category -->
                <div class="menu-category">
                    <h3 class="category-title">Coffee Classics</h3>
                    <ul class="menu-list">
                        <li class="menu-item">
                            <div class="item-info">
                                <span class="item-name">Espresso</span>
                                <span class="item-price">$3.50</span>
                            </div>
                            <p class="item-description">Rich and bold double shot.</p>
                        </li>
                        <li class="menu-item">
                            <div class="item-info">
                                <span class="item-name">Cappuccino</span>
                                <span class="item-price">$4.50</span>
                            </div>
                            <p class="item-description">Equal parts espresso, steamed milk, and foam.</p>
                        </li>
                        <li class="menu-item">
                            <div class="item-info">
                                <span class="item-name">Caramel Macchiato</span>
                                <span class="item-price">$5.25</span>
                            </div>
                            <p class="item-description">Freshly steamed milk with vanilla-flavored syrup marked with espresso.</p>
                        </li>
                    </ul>
                </div>

                <!-- Pastries Category -->
                <div class="menu-category">
                    <h3 class="category-title">Fresh Pastries</h3>
                    <ul class="menu-list">
                        <li class="menu-item">
                            <div class="item-info">
                                <span class="item-name">Butter Croissant</span>
                                <span class="item-price">$3.75</span>
                            </div>
                            <p class="item-description">Flaky, golden-brown, and buttery.</p>
                        </li>
                        <li class="menu-item">
                            <div class="item-info">
                                <span class="item-name">Blueberry Muffin</span>
                                <span class="item-price">$4.00</span>
                            </div>
                            <p class="item-description">Bursting with fresh berries and a sugar crumble.</p>
                        </li>
                    </ul>
                </div>
            </div>
        </section>

        <!-- Contact Section -->
        <section id="contact" class="contact-section">
            <div class="section-header">
                <h2 class="section-title">Visit Us</h2>
                <p class="section-description">We'd love to hear from you or see you in person.</p>
            </div>

            <div class="contact-container">
                <!-- Contact Information -->
                <div class="contact-info">
                    <div class="info-block">
                        <h4 class="info-label">Location</h4>
                        <p class="info-text">123 Coffee Lane, Brewtown, BT 56789</p>
                    </div>
                    <div class="info-block">
                        <h4 class="info-label">Hours</h4>
                        <p class="info-text">Mon - Fri: 7am - 7pm</p>
                        <p class="info-text">Sat - Sun: 8am - 6pm</p>
                    </div>
                </div>

                <!-- Contact Form -->
                <form class="contact-form">
                    <div class="form-group">
                        <label for="name" class="form-label">Name</label>
                        <input type="text" id="name" name="name" class="form-input" required>
                    </div>
                    <div class="form-group">
                        <label for="email" class="form-label">Email</label>
                        <input type="email" id="email" name="email" class="form-input" required>
                    </div>
                    <div class="form-group">
                        <label for="message" class="form-label">Message</label>
                        <textarea id="message" name="message" class="form-textarea" rows="5" required></textarea>
                    </div>
                    <button type="submit" class="submit-button">Send Message</button>
                </form>
            </div>
        </section>
    </main>

    <!-- Footer -->
    <footer class="site-footer">
        <div class="footer-content">
            <p class="copyright">&copy; 2023 Bean & Brew Coffee Shop. All rights reserved.</p>
            <div class="social-links">
                <a href="#" class="social-link">Instagram</a>
                <a href="#" class="social-link">Facebook</a>
                <a href="#" class="social-link">Twitter</a>
            </div>
        </div>
    </footer>


    <script>
        /**
         * Bean & Brew - Interactive Functionality
         */
        
        document.addEventListener('DOMContentLoaded', () => {
            // --- Element Selectors ---
            const header = document.querySelector('.site-header');
            const mobileMenuToggle = document.querySelector('.mobile-menu-toggle');
            const navMenu = document.querySelector('.nav-menu');
            const navLinks = document.querySelectorAll('.nav-link, .cta-button');
            const contactForm = document.querySelector('.contact-form');
        
            /**
             * Mobile Navigation Toggle
             * Handles opening and closing the mobile menu drawer
             */
            const toggleMobileMenu = () => {
                const isOpen = navMenu.classList.toggle('active');
                mobileMenuToggle.classList.toggle('is-active');
        
                // Update accessibility attribute
                mobileMenuToggle.setAttribute('aria-expanded', isOpen);
        
                // Prevent body scroll when menu is open
                document.body.style.overflow = isOpen ? 'hidden' : '';
            };
        
            mobileMenuToggle.addEventListener('click', toggleMobileMenu);
        
            /**
             * Smooth Scrolling
             * Intercepts internal anchor links for a smooth transition to sections
             */
            navLinks.forEach(link => {
                link.addEventListener('click', (e) => {
                    const targetId = link.getAttribute('href');
            
                    // Only process internal links
                    if (targetId.startsWith('#')) {
                        e.preventDefault();
                        const targetElement = document.querySelector(targetId);
        
                        if (targetElement) {
                            // Close mobile menu if it's open
                            if (navMenu.classList.contains('active')) {
                                toggleMobileMenu();
                            }
        
                            // Calculate header height for offset
                            const headerOffset = header.offsetHeight;
                            const elementPosition = targetElement.getBoundingClientRect().top;
                            const offsetPosition = elementPosition + window.pageYOffset - headerOffset;
        
                            window.scrollTo({
                                top: offsetPosition,
                                behavior: 'smooth'
                            });
                        }
                    }
                });
            });
        
            /**
             * Sticky Header Effect
             * Adds a class to the header when the user scrolls down
             */
            const handleHeaderScroll = () => {
                if (window.scrollY > 50) {
                    header.classList.add('header-scrolled');
                } else {
                    header.classList.remove('header-scrolled');
                }
            };
        
            window.addEventListener('scroll', handleHeaderScroll);
        
            /**
             * Contact Form Submission
             * Handles form validation and provides user feedback
             */
            if (contactForm) {
                contactForm.addEventListener('submit', (e) => {
                    e.preventDefault();
        
                    // Get form data
                    const formData = new FormData(contactForm);
                    const data = Object.fromEntries(formData.entries());
        
                    // Simple UI Feedback (Simulating an API call)
                    const submitBtn = contactForm.querySelector('.submit-button');
                    const originalBtnText = submitBtn.textContent;
            
                    submitBtn.disabled = true;
                    submitBtn.textContent = 'Sending...';
        
                    // Simulate network delay
                    setTimeout(() => {
                        console.log('Form Data Received:', data);
                
                        alert(`Thank you, ${data.name}! Your message has been sent successfully.`);
                
                        // Reset form and button
                        contactForm.reset();
                        submitBtn.disabled = false;
                        submitBtn.textContent = originalBtnText;
                    }, 1500);
                });
            }
        
            /**
             * Active Link Highlighting
             * Uses Intersection Observer to highlight the current section in the nav
             */
            const observerOptions = {
                root: null,
                rootMargin: '-20% 0px -70% 0px', // Adjust to trigger when section is in view
                threshold: 0
            };
        
            const observerCallback = (entries) => {
                entries.forEach(entry => {
                    if (entry.isIntersecting) {
                        const id = entry.target.getAttribute('id');
                        document.querySelectorAll('.nav-link').forEach(link => {
                            link.classList.remove('active-link');
                            if (link.getAttribute('href') === `#${id}`) {
                                link.classList.add('active-link');
                            }
                        });
                    }
                });
            };
        
            const observer = new IntersectionObserver(observerCallback, observerOptions);
            document.querySelectorAll('section[id]').forEach(section => observer.observe(section));
        });
    </script>
</body>
<!-- JavaScript will be added here -->
</html>
```

**Result:** Successfully wrote index.html (auto-applied)
