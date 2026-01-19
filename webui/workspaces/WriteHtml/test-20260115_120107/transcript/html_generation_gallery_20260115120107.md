# HTML Generation Transcript

## Creating HTML File: `gallery.html`

### Step 1: Generating HTML Structure

**Prompt:**
```
You are an expert web developer tasked with creating a complete, self-contained HTML file.

## Requirements:
Create a photo gallery page for a travel blog with at least one image placeholder.

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
    <title>Wanderlust Captures | Travel Photo Gallery</title>
    <!-- CSS will be added here -->
</head>
<body>

    <!-- Main Navigation Header -->
    <header class="site-header">
        <nav class="nav-container">
            <div class="nav-logo">
                <a href="#" class="logo-link">Wanderlust Blog</a>
            </div>
            <ul class="nav-menu">
                <li class="nav-item"><a href="#" class="nav-link">Home</a></li>
                <li class="nav-item"><a href="#" class="nav-link active">Gallery</a></li>
                <li class="nav-item"><a href="#" class="nav-link">Destinations</a></li>
                <li class="nav-item"><a href="#" class="nav-link">About</a></li>
            </ul>
        </nav>
    </header>

    <main>
        <!-- Hero Section: Introduction to the gallery -->
        <section class="hero-section">
            <div class="hero-content">
                <h1 class="hero-title">World Through My Lens</h1>
                <p class="hero-subtitle">A collection of moments captured across seven continents.</p>
            </div>
        </section>

        <!-- Gallery Controls: Filtering and Sorting -->
        <section class="gallery-controls">
            <div class="filter-container">
                <button class="filter-btn active" data-filter="all">All</button>
                <button class="filter-btn" data-filter="mountains">Mountains</button>
                <button class="filter-btn" data-filter="beaches">Beaches</button>
                <button class="filter-btn" data-filter="cities">Cities</button>
            </div>
        </section>

        <!-- Photo Gallery Grid -->
        <section class="gallery-section">
            <div class="gallery-grid">
                
                <!-- Gallery Item 1 -->
                <figure class="gallery-item" data-category="mountains">
                    <div class="image-wrapper">
                        <img src="https://via.placeholder.com/600x400?text=Swiss+Alps" alt="Snow capped Swiss Alps during sunset" class="gallery-image">
                        <div class="image-overlay">
                            <span class="view-icon">View Photo</span>
                        </div>
                    </div>
                    <figcaption class="gallery-caption">
                        <h3 class="photo-title">Swiss Alps</h3>
                        <p class="photo-location">Zermatt, Switzerland</p>
                    </figcaption>
                </figure>

                <!-- Gallery Item 2 -->
                <figure class="gallery-item" data-category="cities">
                    <div class="image-wrapper">
                        <img src="https://via.placeholder.com/600x800?text=Tokyo+Nights" alt="Neon lights in Shinjuku, Tokyo" class="gallery-image">
                        <div class="image-overlay">
                            <span class="view-icon">View Photo</span>
                        </div>
                    </div>
                    <figcaption class="gallery-caption">
                        <h3 class="photo-title">Neon Dreams</h3>
                        <p class="photo-location">Tokyo, Japan</p>
                    </figcaption>
                </figure>

                <!-- Gallery Item 3 -->
                <figure class="gallery-item" data-category="beaches">
                    <div class="image-wrapper">
                        <img src="https://via.placeholder.com/600x400?text=Bali+Coast" alt="Crystal clear water in Bali" class="gallery-image">
                        <div class="image-overlay">
                            <span class="view-icon">View Photo</span>
                        </div>
                    </div>
                    <figcaption class="gallery-caption">
                        <h3 class="photo-title">Azure Waters</h3>
                        <p class="photo-location">Bali, Indonesia</p>
                    </figcaption>
                </figure>

                <!-- Gallery Item 4 -->
                <figure class="gallery-item" data-category="mountains">
                    <div class="image-wrapper">
                        <img src="https://via.placeholder.com/600x400?text=Patagonia" alt="The peaks of Torres del Paine" class="gallery-image">
                        <div class="image-overlay">
                            <span class="view-icon">View Photo</span>
                        </div>
                    </div>
                    <figcaption class="gallery-caption">
                        <h3 class="photo-title">Patagonia Peaks</h3>
                        <p class="photo-location">Chile</p>
                    </figcaption>
                </figure>

            </div>
        </section>

        <!-- Lightbox Modal (Hidden by default) -->
        <div class="lightbox-modal" aria-hidden="true">
            <button class="modal-close">&times;</button>
            <div class="modal-content">
                <img src="" alt="" class="modal-image">
                <div class="modal-caption"></div>
            </div>
            <button class="modal-prev">&#10094;</button>
            <button class="modal-next">&#10095;</button>
        </div>
    </main>

    <!-- Footer Section -->
    <footer class="site-footer">
        <div class="footer-container">
            <div class="footer-info">
                <p>&copy; 2023 Wanderlust Travel Blog. All rights reserved.</p>
            </div>
            <div class="footer-socials">
                <a href="#" class="social-link">Instagram</a>
                <a href="#" class="social-link">Pinterest</a>
                <a href="#" class="social-link">Twitter</a>
            </div>
        </div>
    </footer>

</body>
<!-- JavaScript will be added here -->
</html>
```

### Step 1.5: Generating Images

**Prompt:**
```
Based on the following HTML page description and structure, identify 1 key images that should be generated.
## Page Description:
Create a photo gallery page for a travel blog with at least one image placeholder.
## HTML Structure:
```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Wanderlust Captures | Travel Photo Gallery</title>
    <!-- CSS will be added here -->
</head>
<body>

    <!-- Main Navigation Header -->
    <header class="site-header">
        <nav class="nav-container">
            <div class="nav-logo">
                <a href="#" class="logo-link">Wanderlust Blog</a>
            </div>
            <ul class="nav-menu">
                <li class="nav-item"><a href="#" class="nav-link">Home</a></li>
                <li class="nav-item"><a href="#" class="nav-link active">Gallery</a></li>
                <li class="nav-item"><a href="#" class="nav-link">Destinations</a></li>
                <li class="nav-item"><a href="#" class="nav-link">About</a></li>
            </ul>
        </nav>
    </header>

    <main>
        <!-- Hero Section: Introduction to the gallery -->
        <section class="hero-section">
            <div class="hero-content">
                <h1 class="hero-title">World Through My Lens</h1>
                <p class="hero-subtitle">A collection of moments captured across seven continents.</p>
            </div>
        </section>

        <!-- Gallery Controls: Filtering and Sorting -->
        <section class="gallery-controls">
            <div class="filter-container">
                <button class="filter-btn active" data-filter="all">All</button>
                <button class="filter-btn" data-filter="mountains">Mountains</button>
                <button class="filter-btn" data-filter="beaches">Beaches</button>
                <button class="filter-btn" data-filter="cities">Cities</button>
            </div>
        </section>

        <!-- Photo Gallery Grid -->
        <section class="gallery-section">
            <div class="gallery-grid">
                
                <!-- Gallery Item 1 -->
                <figure class="gallery-item" data-category="mountains">
                    <div class="image-wrapper">
                        <img src="https://via.placeholder.com/600x400?text=Swiss+Alps" alt="Snow capped Swiss Alps during sunset" class="gallery-image">
                        <div class="image-overlay">
                            <span class="view-icon">View Photo</span>
                        </div>
                    </div>
                    <figcaption class="gallery-caption">
                        <h3 class="photo-title">Swiss Alps</h3>
                        <p class="photo-location">Zermatt, Switzerland</p>
                    </figcaption>
                </figure>

                <!-- Gallery Item 2 -->
                <figure class="gallery-item" data-category="cities">
                    <div class="image-wrapper">
                        <img src="https://via.placeholder.com/600x800?text=Tokyo+Nights" alt="Neon lights in Shinjuku, Tokyo" class="gallery-image">
                        <div class="image-overlay">
                            <span class="view-icon">View Photo</span>
                        </div>
                    </div>
                    <figcaption class="gallery-caption">
                        <h3 class="photo-title">Neon Dreams</h3>
                        <p class="photo-location">Tokyo, Japan</p>
                    </figcaption>
                </figure>

                <!-- Gallery Item 3 -->
                <figure class="gallery-item" data-category="beaches">
                    <div class="image-wrapper">
                        <img src="https://via.placeholder.com/600x400?text=Bali+Coast" alt="Crystal clear water in Bali" class="gallery-image">
                        <div class="image-overlay">
                            <span class="view-icon">View Photo</span>
                        </div>
                    </div>
                    <figcaption class="gallery-caption">
                        <h3 class="photo-title">Azure Waters</h3>
                        <p class="photo-location">Bali, Indonesia</p>
                    </figcaption>
                </figure>

                <!-- Gallery Item 4 -->
                <figure class="gallery-item" data-category="mountains">
                    <div class="image-wrapper">
                        <img src="https://via.placeholder.com/600x400?text=Patagonia" alt="The peaks of Torres del Paine" class="gallery-image">
                        <div class="image-overlay">
                            <span class="view-icon">View Photo</span>
                        </div>
                    </div>
                    <figcaption class="gallery-caption">
                        <h3 class="photo-title">Patagonia Peaks</h3>
                        <p class="photo-location">Chile</p>
                    </figcaption>
                </figure>

            </div>
        </section>

        <!-- Lightbox Modal (Hidden by default) -->
        <div class="lightbox-modal" aria-hidden="true">
            <button class="modal-close">&times;</button>
            <div class="modal-content">
                <img src="" alt="" class="modal-image">
                <div class="modal-caption"></div>
            </div>
            <button class="modal-prev">&#10094;</button>
            <button class="modal-next">&#10095;</button>
        </div>
    </main>

    <!-- Footer Section -->
    <footer class="site-footer">
        <div class="footer-container">
            <div class="footer-info">
                <p>&copy; 2023 Wanderlust Travel Blog. All rights reserved.</p>
            </div>
            <div class="footer-socials">
                <a href="#" class="social-link">Instagram</a>
                <a href="#" class="social-link">Pinterest</a>
                <a href="#" class="social-link">Twitter</a>
            </div>
        </div>
    </footer>

</body>
<!-- JavaScript will be added here -->
</html>
```
For each image, provide:
1. A descriptive filename ending in .png (e.g., "hero-banner.png", "product-showcase.png")
2. A detailed visual description for image generation (be specific about style, colors, composition)

Note: All images will be generated as PNG files by an AI image model.

Format your response as:
IMAGE: filename.png
DESCRIPTION: detailed visual description
IMAGE: another-image.png
DESCRIPTION: another detailed description
```

**Response:**
IMAGE: swiss-alps-sunset.png
DESCRIPTION: A breathtaking, wide-angle photograph of the Swiss Alps during the golden hour. The jagged, snow-capped peaks are bathed in a warm, orange alpenglow against a sky transitioning from soft yellow to deep twilight blue. In the foreground, a pristine alpine meadow with a few scattered wildflowers leads the eye toward the mountains. The composition is cinematic and expansive, capturing the scale and majesty of the landscape with professional-grade clarity and vibrant, natural colors.

**Error generating swiss-alps-sunset.png:** 404 . This model models/gemini-2.5-flash-image-preview is no longer available. Please update your code to use a newer model for the latest features and improvements.

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
    <title>Wanderlust Captures | Travel Photo Gallery</title>
    <!-- CSS will be added here -->
</head>
<body>

    <!-- Main Navigation Header -->
    <header class="site-header">
        <nav class="nav-container">
            <div class="nav-logo">
                <a href="#" class="logo-link">Wanderlust Blog</a>
            </div>
            <ul class="nav-menu">
                <li class="nav-item"><a href="#" class="nav-link">Home</a></li>
                <li class="nav-item"><a href="#" class="nav-link active">Gallery</a></li>
                <li class="nav-item"><a href="#" class="nav-link">Destinations</a></li>
                <li class="nav-item"><a href="#" class="nav-link">About</a></li>
            </ul>
        </nav>
    </header>

    <main>
        <!-- Hero Section: Introduction to the gallery -->
        <section class="hero-section">
            <div class="hero-content">
                <h1 class="hero-title">World Through My Lens</h1>
                <p class="hero-subtitle">A collection of moments captured across seven continents.</p>
            </div>
        </section>

        <!-- Gallery Controls: Filtering and Sorting -->
        <section class="gallery-controls">
            <div class="filter-container">
                <button class="filter-btn active" data-filter="all">All</button>
                <button class="filter-btn" data-filter="mountains">Mountains</button>
                <button class="filter-btn" data-filter="beaches">Beaches</button>
                <button class="filter-btn" data-filter="cities">Cities</button>
            </div>
        </section>

        <!-- Photo Gallery Grid -->
        <section class="gallery-section">
            <div class="gallery-grid">
                
                <!-- Gallery Item 1 -->
                <figure class="gallery-item" data-category="mountains">
                    <div class="image-wrapper">
                        <img src="https://via.placeholder.com/600x400?text=Swiss+Alps" alt="Snow capped Swiss Alps during sunset" class="gallery-image">
                        <div class="image-overlay">
                            <span class="view-icon">View Photo</span>
                        </div>
                    </div>
                    <figcaption class="gallery-caption">
                        <h3 class="photo-title">Swiss Alps</h3>
                        <p class="photo-location">Zermatt, Switzerland</p>
                    </figcaption>
                </figure>

                <!-- Gallery Item 2 -->
                <figure class="gallery-item" data-category="cities">
                    <div class="image-wrapper">
                        <img src="https://via.placeholder.com/600x800?text=Tokyo+Nights" alt="Neon lights in Shinjuku, Tokyo" class="gallery-image">
                        <div class="image-overlay">
                            <span class="view-icon">View Photo</span>
                        </div>
                    </div>
                    <figcaption class="gallery-caption">
                        <h3 class="photo-title">Neon Dreams</h3>
                        <p class="photo-location">Tokyo, Japan</p>
                    </figcaption>
                </figure>

                <!-- Gallery Item 3 -->
                <figure class="gallery-item" data-category="beaches">
                    <div class="image-wrapper">
                        <img src="https://via.placeholder.com/600x400?text=Bali+Coast" alt="Crystal clear water in Bali" class="gallery-image">
                        <div class="image-overlay">
                            <span class="view-icon">View Photo</span>
                        </div>
                    </div>
                    <figcaption class="gallery-caption">
                        <h3 class="photo-title">Azure Waters</h3>
                        <p class="photo-location">Bali, Indonesia</p>
                    </figcaption>
                </figure>

                <!-- Gallery Item 4 -->
                <figure class="gallery-item" data-category="mountains">
                    <div class="image-wrapper">
                        <img src="https://via.placeholder.com/600x400?text=Patagonia" alt="The peaks of Torres del Paine" class="gallery-image">
                        <div class="image-overlay">
                            <span class="view-icon">View Photo</span>
                        </div>
                    </div>
                    <figcaption class="gallery-caption">
                        <h3 class="photo-title">Patagonia Peaks</h3>
                        <p class="photo-location">Chile</p>
                    </figcaption>
                </figure>

            </div>
        </section>

        <!-- Lightbox Modal (Hidden by default) -->
        <div class="lightbox-modal" aria-hidden="true">
            <button class="modal-close">&times;</button>
            <div class="modal-content">
                <img src="" alt="" class="modal-image">
                <div class="modal-caption"></div>
            </div>
            <button class="modal-prev">&#10094;</button>
            <button class="modal-next">&#10095;</button>
        </div>
    </main>

    <!-- Footer Section -->
    <footer class="site-footer">
        <div class="footer-container">
            <div class="footer-info">
                <p>&copy; 2023 Wanderlust Travel Blog. All rights reserved.</p>
            </div>
            <div class="footer-socials">
                <a href="#" class="social-link">Instagram</a>
                <a href="#" class="social-link">Pinterest</a>
                <a href="#" class="social-link">Twitter</a>
            </div>
        </div>
    </footer>

</body>
<!-- JavaScript will be added here -->
</html>
```

## Requirements:
Create a photo gallery page for a travel blog with at least one image placeholder.

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
 * Wanderlust Captures - Gallery Functionality
 * Handles filtering of gallery items and the lightbox modal interaction.
 */

document.addEventListener('DOMContentLoaded', () => {
    // --- DOM Elements ---
    const filterButtons = document.querySelectorAll('.filter-btn');
    const galleryItems = document.querySelectorAll('.gallery-item');
    const lightbox = document.querySelector('.lightbox-modal');
    const modalImg = lightbox.querySelector('.modal-image');
    const modalCaption = lightbox.querySelector('.modal-caption');
    const closeBtn = lightbox.querySelector('.modal-close');
    const prevBtn = lightbox.querySelector('.modal-prev');
    const nextBtn = lightbox.querySelector('.modal-next');

    // State management
    let currentIndex = 0;
    let activeItems = [...galleryItems]; // Tracks items currently visible after filtering

    // --- Filtering Logic ---

    /**
     * Filters the gallery items based on the category
     * @param {string} filterValue - The category to filter by
     */
    const filterGallery = (filterValue) => {
        galleryItems.forEach(item => {
            const category = item.getAttribute('data-category');
            if (filterValue === 'all' || category === filterValue) {
                item.style.display = 'block';
                item.classList.remove('hidden');
            } else {
                item.style.display = 'none';
                item.classList.add('hidden');
            }
        });

        // Update the list of active items for lightbox navigation
        activeItems = Array.from(galleryItems).filter(item => !item.classList.contains('hidden'));
    };

    filterButtons.forEach(button => {
        button.addEventListener('click', () => {
            // Update UI for active button
            filterButtons.forEach(btn => btn.classList.remove('active'));
            button.classList.add('active');

            // Execute filter
            const filterValue = button.getAttribute('data-filter');
            filterGallery(filterValue);
        });
    });

    // --- Lightbox Logic ---

    /**
     * Updates the content of the lightbox based on the current index
     * @param {number} index - Index of the item in the activeItems array
     */
    const updateLightboxContent = (index) => {
        const item = activeItems[index];
        const img = item.querySelector('.gallery-image');
        const title = item.querySelector('.photo-title').textContent;
        const location = item.querySelector('.photo-location').textContent;

        modalImg.src = img.src;
        modalImg.alt = img.alt;
        modalCaption.innerHTML = `<h3>${title}</h3><p>${location}</p>`;
        currentIndex = index;
    };

    const openLightbox = (index) => {
        updateLightboxContent(index);
        lightbox.classList.add('active');
        lightbox.setAttribute('aria-hidden', 'false');
        document.body.style.overflow = 'hidden'; // Prevent scrolling when modal is open
    };

    const closeLightbox = () => {
        lightbox.classList.remove('active');
        lightbox.setAttribute('aria-hidden', 'true');
        document.body.style.overflow = 'auto';
    };

    const showNext = () => {
        const nextIndex = (currentIndex + 1) % activeItems.length;
        updateLightboxContent(nextIndex);
    };

    const showPrev = () => {
        const prevIndex = (currentIndex - 1 + activeItems.length) % activeItems.length;
        updateLightboxContent(prevIndex);
    };

    // --- Event Listeners ---

    // Open lightbox when clicking on a gallery item
    galleryItems.forEach((item) => {
        const wrapper = item.querySelector('.image-wrapper');
        wrapper.addEventListener('click', () => {
            const index = activeItems.indexOf(item);
            if (index !== -1) openLightbox(index);
        });
    });

    // Lightbox control buttons
    closeBtn.addEventListener('click', closeLightbox);
    nextBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        showNext();
    });
    prevBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        showPrev();
    });

    // Close lightbox when clicking outside the content
    lightbox.addEventListener('click', (e) => {
        if (e.target === lightbox) closeLightbox();
    });

    // Keyboard Navigation
    document.addEventListener('keydown', (e) => {
        if (!lightbox.classList.contains('active')) return;

        if (e.key === 'Escape') closeLightbox();
        if (e.key === 'ArrowRight') showNext();
        if (e.key === 'ArrowLeft') showPrev();
    });
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
    <title>Wanderlust Captures | Travel Photo Gallery</title>
    <!-- CSS will be added here -->
</head>
<body>

    <!-- Main Navigation Header -->
    <header class="site-header">
        <nav class="nav-container">
            <div class="nav-logo">
                <a href="#" class="logo-link">Wanderlust Blog</a>
            </div>
            <ul class="nav-menu">
                <li class="nav-item"><a href="#" class="nav-link">Home</a></li>
                <li class="nav-item"><a href="#" class="nav-link active">Gallery</a></li>
                <li class="nav-item"><a href="#" class="nav-link">Destinations</a></li>
                <li class="nav-item"><a href="#" class="nav-link">About</a></li>
            </ul>
        </nav>
    </header>

    <main>
        <!-- Hero Section: Introduction to the gallery -->
        <section class="hero-section">
            <div class="hero-content">
                <h1 class="hero-title">World Through My Lens</h1>
                <p class="hero-subtitle">A collection of moments captured across seven continents.</p>
            </div>
        </section>

        <!-- Gallery Controls: Filtering and Sorting -->
        <section class="gallery-controls">
            <div class="filter-container">
                <button class="filter-btn active" data-filter="all">All</button>
                <button class="filter-btn" data-filter="mountains">Mountains</button>
                <button class="filter-btn" data-filter="beaches">Beaches</button>
                <button class="filter-btn" data-filter="cities">Cities</button>
            </div>
        </section>

        <!-- Photo Gallery Grid -->
        <section class="gallery-section">
            <div class="gallery-grid">
                
                <!-- Gallery Item 1 -->
                <figure class="gallery-item" data-category="mountains">
                    <div class="image-wrapper">
                        <img src="https://via.placeholder.com/600x400?text=Swiss+Alps" alt="Snow capped Swiss Alps during sunset" class="gallery-image">
                        <div class="image-overlay">
                            <span class="view-icon">View Photo</span>
                        </div>
                    </div>
                    <figcaption class="gallery-caption">
                        <h3 class="photo-title">Swiss Alps</h3>
                        <p class="photo-location">Zermatt, Switzerland</p>
                    </figcaption>
                </figure>

                <!-- Gallery Item 2 -->
                <figure class="gallery-item" data-category="cities">
                    <div class="image-wrapper">
                        <img src="https://via.placeholder.com/600x800?text=Tokyo+Nights" alt="Neon lights in Shinjuku, Tokyo" class="gallery-image">
                        <div class="image-overlay">
                            <span class="view-icon">View Photo</span>
                        </div>
                    </div>
                    <figcaption class="gallery-caption">
                        <h3 class="photo-title">Neon Dreams</h3>
                        <p class="photo-location">Tokyo, Japan</p>
                    </figcaption>
                </figure>

                <!-- Gallery Item 3 -->
                <figure class="gallery-item" data-category="beaches">
                    <div class="image-wrapper">
                        <img src="https://via.placeholder.com/600x400?text=Bali+Coast" alt="Crystal clear water in Bali" class="gallery-image">
                        <div class="image-overlay">
                            <span class="view-icon">View Photo</span>
                        </div>
                    </div>
                    <figcaption class="gallery-caption">
                        <h3 class="photo-title">Azure Waters</h3>
                        <p class="photo-location">Bali, Indonesia</p>
                    </figcaption>
                </figure>

                <!-- Gallery Item 4 -->
                <figure class="gallery-item" data-category="mountains">
                    <div class="image-wrapper">
                        <img src="https://via.placeholder.com/600x400?text=Patagonia" alt="The peaks of Torres del Paine" class="gallery-image">
                        <div class="image-overlay">
                            <span class="view-icon">View Photo</span>
                        </div>
                    </div>
                    <figcaption class="gallery-caption">
                        <h3 class="photo-title">Patagonia Peaks</h3>
                        <p class="photo-location">Chile</p>
                    </figcaption>
                </figure>

            </div>
        </section>

        <!-- Lightbox Modal (Hidden by default) -->
        <div class="lightbox-modal" aria-hidden="true">
            <button class="modal-close">&times;</button>
            <div class="modal-content">
                <img src="" alt="" class="modal-image">
                <div class="modal-caption"></div>
            </div>
            <button class="modal-prev">&#10094;</button>
            <button class="modal-next">&#10095;</button>
        </div>
    </main>

    <!-- Footer Section -->
    <footer class="site-footer">
        <div class="footer-container">
            <div class="footer-info">
                <p>&copy; 2023 Wanderlust Travel Blog. All rights reserved.</p>
            </div>
            <div class="footer-socials">
                <a href="#" class="social-link">Instagram</a>
                <a href="#" class="social-link">Pinterest</a>
                <a href="#" class="social-link">Twitter</a>
            </div>
        </div>
    </footer>

</body>
<!-- JavaScript will be added here -->
</html>
```

## Requirements:
Create a photo gallery page for a travel blog with at least one image placeholder.

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
    Wanderlust Captures - Travel Gallery Styles
    Table of Contents:
    1. Variables & Reset
    2. Base Styles & Typography
    3. Header & Navigation
    4. Hero Section
    5. Gallery Controls (Filters)
    6. Photo Gallery Grid
    7. Gallery Item & Hover Effects
    8. Lightbox Modal
    9. Footer
    10. Responsive Media Queries
*/

/* 1. Variables & Reset */
:root {
    --primary-color: #2d3436;
    --accent-color: #d63031;
    --text-main: #2d3436;
    --text-muted: #636e72;
    --bg-light: #f9f9f9;
    --white: #ffffff;
    --transition: all 0.3s ease;
    --shadow: 0 4px 15px rgba(0, 0, 0, 0.1);
    --container-width: 1200px;
}

* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

body {
    font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    line-height: 1.6;
    color: var(--text-main);
    background-color: var(--bg-light);
    overflow-x: hidden;
}

/* 2. Base Styles */
a {
    text-decoration: none;
    color: inherit;
    transition: var(--transition);
}

ul {
    list-style: none;
}

img {
    max-width: 100%;
    display: block;
}

/* 3. Header & Navigation */
.site-header {
    background-color: var(--white);
    padding: 1rem 5%;
    position: sticky;
    top: 0;
    z-index: 1000;
    box-shadow: 0 2px 10px rgba(0,0,0,0.05);
}

.nav-container {
    max-width: var(--container-width);
    margin: 0 auto;
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.logo-link {
    font-size: 1.5rem;
    font-weight: 800;
    text-transform: uppercase;
    letter-spacing: 1px;
    color: var(--primary-color);
}

.nav-menu {
    display: flex;
    gap: 2rem;
}

.nav-link {
    font-weight: 500;
    font-size: 0.9rem;
    color: var(--text-muted);
    position: relative;
}

.nav-link:hover, .nav-link.active {
    color: var(--accent-color);
}

.nav-link.active::after {
    content: '';
    position: absolute;
    bottom: -5px;
    left: 0;
    width: 100%;
    height: 2px;
    background-color: var(--accent-color);
}

/* 4. Hero Section */
.hero-section {
    padding: 4rem 5%;
    text-align: center;
    background-color: var(--white);
    margin-bottom: 2rem;
}

.hero-title {
    font-size: 2.5rem;
    margin-bottom: 1rem;
    font-weight: 700;
}

.hero-subtitle {
    color: var(--text-muted);
    font-size: 1.1rem;
    max-width: 600px;
    margin: 0 auto;
}

/* 5. Gallery Controls */
.gallery-controls {
    max-width: var(--container-width);
    margin: 0 auto 3rem;
    padding: 0 5%;
    display: flex;
    justify-content: center;
}

.filter-container {
    display: flex;
    gap: 1rem;
    flex-wrap: wrap;
    justify-content: center;
}

.filter-btn {
    padding: 0.6rem 1.5rem;
    border: 1px solid #dfe6e9;
    background: var(--white);
    cursor: pointer;
    border-radius: 30px;
    font-weight: 500;
    transition: var(--transition);
}

.filter-btn:hover {
    border-color: var(--accent-color);
    color: var(--accent-color);
}

.filter-btn.active {
    background-color: var(--accent-color);
    color: var(--white);
    border-color: var(--accent-color);
}

/* 6. Photo Gallery Grid */
.gallery-section {
    max-width: var(--container-width);
    margin: 0 auto 5rem;
    padding: 0 5%;
}

.gallery-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    gap: 2rem;
    /* Masonry-like feel for different aspect ratios */
    align-items: start;
}

/* 7. Gallery Item & Hover Effects */
.gallery-item {
    background: var(--white);
    border-radius: 12px;
    overflow: hidden;
    box-shadow: var(--shadow);
    transition: transform 0.3s ease;
}

.gallery-item:hover {
    transform: translateY(-5px);
}

.image-wrapper {
    position: relative;
    overflow: hidden;
    aspect-ratio: 3/2;
}

/* Handle vertical images if they exist */
.gallery-item[data-category="cities"] .image-wrapper {
    aspect-ratio: 3/4;
}

.gallery-image {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.5s ease;
}

.image-overlay {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: rgba(0, 0, 0, 0.4);
    display: flex;
    justify-content: center;
    align-items: center;
    opacity: 0;
    transition: opacity 0.3s ease;
    cursor: pointer;
}

.view-icon {
    color: var(--white);
    border: 2px solid var(--white);
    padding: 0.5rem 1.2rem;
    border-radius: 4px;
    font-weight: 600;
    text-transform: uppercase;
    font-size: 0.8rem;
}

.gallery-item:hover .gallery-image {
    transform: scale(1.1);
}

.gallery-item:hover .image-overlay {
    opacity: 1;
}

.gallery-caption {
    padding: 1.5rem;
}

.photo-title {
    font-size: 1.2rem;
    margin-bottom: 0.3rem;
}

.photo-location {
    font-size: 0.85rem;
    color: var(--text-muted);
    display: flex;
    align-items: center;
    gap: 0.3rem;
}

.photo-location::before {
    content: '📍';
    font-size: 0.8rem;
}

/* 8. Lightbox Modal */
.lightbox-modal {
    position: fixed;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    background: rgba(0, 0, 0, 0.95);
    z-index: 2000;
    display: none; /* Hidden by default */
    justify-content: center;
    align-items: center;
    flex-direction: column;
}

/* When active via JS */
.lightbox-modal.active {
    display: flex;
}

.modal-content {
    max-width: 90%;
    max-height: 80vh;
    position: relative;
}

.modal-image {
    max-width: 100%;
    max-height: 80vh;
    object-fit: contain;
    border: 3px solid var(--white);
}

.modal-caption {
    color: var(--white);
    margin-top: 1rem;
    text-align: center;
}

.modal-close {
    position: absolute;
    top: 20px;
    right: 30px;
    color: var(--white);
    font-size: 3rem;
    background: none;
    border: none;
    cursor: pointer;
}

.modal-prev, .modal-next {
    position: absolute;
    top: 50%;
    transform: translateY(-50%);
    background: rgba(255,255,255,0.1);
    color: white;
    padding: 1rem;
    border: none;
    cursor: pointer;
    font-size: 1.5rem;
    transition: var(--transition);
}

.modal-prev:hover, .modal-next:hover {
    background: rgba(255,255,255,0.2);
}

.modal-prev { left: 20px; }
.modal-next { right: 20px; }

/* 9. Footer */
.site-footer {
    background-color: var(--primary-color);
    color: var(--white);
    padding: 3rem 5%;
}

.footer-container {
    max-width: var(--container-width);
    margin: 0 auto;
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 2rem;
}

.footer-info p {
    font-size: 0.9rem;
    opacity: 0.8;
}

.footer-socials {
    display: flex;
    gap: 1.5rem;
}

.social-link {
    font-size: 0.9rem;
    opacity: 0.8;
}

.social-link:hover {
    opacity: 1;
    color: var(--accent-color);
}

/* 10. Responsive Media Queries */

/* Mobile Adjustments */
@media (max-width: 768px) {
    .hero-title {
        font-size: 2rem;
    }
    
    .nav-menu {
        display: none; /* Typically replaced by a hamburger menu in full JS implementation */
    }
    
    .footer-container {
        flex-direction: column;
        text-align: center;
    }

    .gallery-grid {
        grid-template-columns: 1fr;
    }
}

/* Tablet Adjustments */
@media (min-width: 769px) and (max-width: 1024px) {
    .gallery-grid {
        grid-template-columns: repeat(2, 1fr);
    }
}

/* Animation for filtering (to be used with JS) */
.gallery-item.fade-out {
    opacity: 0;
    transform: scale(0.9);
}

.gallery-item.fade-in {
    opacity: 1;
    transform: scale(1);
}
```

### Step 4: Final HTML Output

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Wanderlust Captures | Travel Photo Gallery</title>
    <!-- CSS will be added here -->

    <style>
        /* 
            Wanderlust Captures - Travel Gallery Styles
            Table of Contents:
            1. Variables & Reset
            2. Base Styles & Typography
            3. Header & Navigation
            4. Hero Section
            5. Gallery Controls (Filters)
            6. Photo Gallery Grid
            7. Gallery Item & Hover Effects
            8. Lightbox Modal
            9. Footer
            10. Responsive Media Queries
        */
        
        /* 1. Variables & Reset */
        :root {
            --primary-color: #2d3436;
            --accent-color: #d63031;
            --text-main: #2d3436;
            --text-muted: #636e72;
            --bg-light: #f9f9f9;
            --white: #ffffff;
            --transition: all 0.3s ease;
            --shadow: 0 4px 15px rgba(0, 0, 0, 0.1);
            --container-width: 1200px;
        }
        
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
            line-height: 1.6;
            color: var(--text-main);
            background-color: var(--bg-light);
            overflow-x: hidden;
        }
        
        /* 2. Base Styles */
        a {
            text-decoration: none;
            color: inherit;
            transition: var(--transition);
        }
        
        ul {
            list-style: none;
        }
        
        img {
            max-width: 100%;
            display: block;
        }
        
        /* 3. Header & Navigation */
        .site-header {
            background-color: var(--white);
            padding: 1rem 5%;
            position: sticky;
            top: 0;
            z-index: 1000;
            box-shadow: 0 2px 10px rgba(0,0,0,0.05);
        }
        
        .nav-container {
            max-width: var(--container-width);
            margin: 0 auto;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        
        .logo-link {
            font-size: 1.5rem;
            font-weight: 800;
            text-transform: uppercase;
            letter-spacing: 1px;
            color: var(--primary-color);
        }
        
        .nav-menu {
            display: flex;
            gap: 2rem;
        }
        
        .nav-link {
            font-weight: 500;
            font-size: 0.9rem;
            color: var(--text-muted);
            position: relative;
        }
        
        .nav-link:hover, .nav-link.active {
            color: var(--accent-color);
        }
        
        .nav-link.active::after {
            content: '';
            position: absolute;
            bottom: -5px;
            left: 0;
            width: 100%;
            height: 2px;
            background-color: var(--accent-color);
        }
        
        /* 4. Hero Section */
        .hero-section {
            padding: 4rem 5%;
            text-align: center;
            background-color: var(--white);
            margin-bottom: 2rem;
        }
        
        .hero-title {
            font-size: 2.5rem;
            margin-bottom: 1rem;
            font-weight: 700;
        }
        
        .hero-subtitle {
            color: var(--text-muted);
            font-size: 1.1rem;
            max-width: 600px;
            margin: 0 auto;
        }
        
        /* 5. Gallery Controls */
        .gallery-controls {
            max-width: var(--container-width);
            margin: 0 auto 3rem;
            padding: 0 5%;
            display: flex;
            justify-content: center;
        }
        
        .filter-container {
            display: flex;
            gap: 1rem;
            flex-wrap: wrap;
            justify-content: center;
        }
        
        .filter-btn {
            padding: 0.6rem 1.5rem;
            border: 1px solid #dfe6e9;
            background: var(--white);
            cursor: pointer;
            border-radius: 30px;
            font-weight: 500;
            transition: var(--transition);
        }
        
        .filter-btn:hover {
            border-color: var(--accent-color);
            color: var(--accent-color);
        }
        
        .filter-btn.active {
            background-color: var(--accent-color);
            color: var(--white);
            border-color: var(--accent-color);
        }
        
        /* 6. Photo Gallery Grid */
        .gallery-section {
            max-width: var(--container-width);
            margin: 0 auto 5rem;
            padding: 0 5%;
        }
        
        .gallery-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
            gap: 2rem;
            /* Masonry-like feel for different aspect ratios */
            align-items: start;
        }
        
        /* 7. Gallery Item & Hover Effects */
        .gallery-item {
            background: var(--white);
            border-radius: 12px;
            overflow: hidden;
            box-shadow: var(--shadow);
            transition: transform 0.3s ease;
        }
        
        .gallery-item:hover {
            transform: translateY(-5px);
        }
        
        .image-wrapper {
            position: relative;
            overflow: hidden;
            aspect-ratio: 3/2;
        }
        
        /* Handle vertical images if they exist */
        .gallery-item[data-category="cities"] .image-wrapper {
            aspect-ratio: 3/4;
        }
        
        .gallery-image {
            width: 100%;
            height: 100%;
            object-fit: cover;
            transition: transform 0.5s ease;
        }
        
        .image-overlay {
            position: absolute;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.4);
            display: flex;
            justify-content: center;
            align-items: center;
            opacity: 0;
            transition: opacity 0.3s ease;
            cursor: pointer;
        }
        
        .view-icon {
            color: var(--white);
            border: 2px solid var(--white);
            padding: 0.5rem 1.2rem;
            border-radius: 4px;
            font-weight: 600;
            text-transform: uppercase;
            font-size: 0.8rem;
        }
        
        .gallery-item:hover .gallery-image {
            transform: scale(1.1);
        }
        
        .gallery-item:hover .image-overlay {
            opacity: 1;
        }
        
        .gallery-caption {
            padding: 1.5rem;
        }
        
        .photo-title {
            font-size: 1.2rem;
            margin-bottom: 0.3rem;
        }
        
        .photo-location {
            font-size: 0.85rem;
            color: var(--text-muted);
            display: flex;
            align-items: center;
            gap: 0.3rem;
        }
        
        .photo-location::before {
            content: '📍';
            font-size: 0.8rem;
        }
        
        /* 8. Lightbox Modal */
        .lightbox-modal {
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.95);
            z-index: 2000;
            display: none; /* Hidden by default */
            justify-content: center;
            align-items: center;
            flex-direction: column;
        }
        
        /* When active via JS */
        .lightbox-modal.active {
            display: flex;
        }
        
        .modal-content {
            max-width: 90%;
            max-height: 80vh;
            position: relative;
        }
        
        .modal-image {
            max-width: 100%;
            max-height: 80vh;
            object-fit: contain;
            border: 3px solid var(--white);
        }
        
        .modal-caption {
            color: var(--white);
            margin-top: 1rem;
            text-align: center;
        }
        
        .modal-close {
            position: absolute;
            top: 20px;
            right: 30px;
            color: var(--white);
            font-size: 3rem;
            background: none;
            border: none;
            cursor: pointer;
        }
        
        .modal-prev, .modal-next {
            position: absolute;
            top: 50%;
            transform: translateY(-50%);
            background: rgba(255,255,255,0.1);
            color: white;
            padding: 1rem;
            border: none;
            cursor: pointer;
            font-size: 1.5rem;
            transition: var(--transition);
        }
        
        .modal-prev:hover, .modal-next:hover {
            background: rgba(255,255,255,0.2);
        }
        
        .modal-prev { left: 20px; }
        .modal-next { right: 20px; }
        
        /* 9. Footer */
        .site-footer {
            background-color: var(--primary-color);
            color: var(--white);
            padding: 3rem 5%;
        }
        
        .footer-container {
            max-width: var(--container-width);
            margin: 0 auto;
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 2rem;
        }
        
        .footer-info p {
            font-size: 0.9rem;
            opacity: 0.8;
        }
        
        .footer-socials {
            display: flex;
            gap: 1.5rem;
        }
        
        .social-link {
            font-size: 0.9rem;
            opacity: 0.8;
        }
        
        .social-link:hover {
            opacity: 1;
            color: var(--accent-color);
        }
        
        /* 10. Responsive Media Queries */
        
        /* Mobile Adjustments */
        @media (max-width: 768px) {
            .hero-title {
                font-size: 2rem;
            }
        
            .nav-menu {
                display: none; /* Typically replaced by a hamburger menu in full JS implementation */
            }
        
            .footer-container {
                flex-direction: column;
                text-align: center;
            }
        
            .gallery-grid {
                grid-template-columns: 1fr;
            }
        }
        
        /* Tablet Adjustments */
        @media (min-width: 769px) and (max-width: 1024px) {
            .gallery-grid {
                grid-template-columns: repeat(2, 1fr);
            }
        }
        
        /* Animation for filtering (to be used with JS) */
        .gallery-item.fade-out {
            opacity: 0;
            transform: scale(0.9);
        }
        
        .gallery-item.fade-in {
            opacity: 1;
            transform: scale(1);
        }
    </style>
</head>
<body>

    <!-- Main Navigation Header -->
    <header class="site-header">
        <nav class="nav-container">
            <div class="nav-logo">
                <a href="#" class="logo-link">Wanderlust Blog</a>
            </div>
            <ul class="nav-menu">
                <li class="nav-item"><a href="#" class="nav-link">Home</a></li>
                <li class="nav-item"><a href="#" class="nav-link active">Gallery</a></li>
                <li class="nav-item"><a href="#" class="nav-link">Destinations</a></li>
                <li class="nav-item"><a href="#" class="nav-link">About</a></li>
            </ul>
        </nav>
    </header>

    <main>
        <!-- Hero Section: Introduction to the gallery -->
        <section class="hero-section">
            <div class="hero-content">
                <h1 class="hero-title">World Through My Lens</h1>
                <p class="hero-subtitle">A collection of moments captured across seven continents.</p>
            </div>
        </section>

        <!-- Gallery Controls: Filtering and Sorting -->
        <section class="gallery-controls">
            <div class="filter-container">
                <button class="filter-btn active" data-filter="all">All</button>
                <button class="filter-btn" data-filter="mountains">Mountains</button>
                <button class="filter-btn" data-filter="beaches">Beaches</button>
                <button class="filter-btn" data-filter="cities">Cities</button>
            </div>
        </section>

        <!-- Photo Gallery Grid -->
        <section class="gallery-section">
            <div class="gallery-grid">
                
                <!-- Gallery Item 1 -->
                <figure class="gallery-item" data-category="mountains">
                    <div class="image-wrapper">
                        <img src="https://via.placeholder.com/600x400?text=Swiss+Alps" alt="Snow capped Swiss Alps during sunset" class="gallery-image">
                        <div class="image-overlay">
                            <span class="view-icon">View Photo</span>
                        </div>
                    </div>
                    <figcaption class="gallery-caption">
                        <h3 class="photo-title">Swiss Alps</h3>
                        <p class="photo-location">Zermatt, Switzerland</p>
                    </figcaption>
                </figure>

                <!-- Gallery Item 2 -->
                <figure class="gallery-item" data-category="cities">
                    <div class="image-wrapper">
                        <img src="https://via.placeholder.com/600x800?text=Tokyo+Nights" alt="Neon lights in Shinjuku, Tokyo" class="gallery-image">
                        <div class="image-overlay">
                            <span class="view-icon">View Photo</span>
                        </div>
                    </div>
                    <figcaption class="gallery-caption">
                        <h3 class="photo-title">Neon Dreams</h3>
                        <p class="photo-location">Tokyo, Japan</p>
                    </figcaption>
                </figure>

                <!-- Gallery Item 3 -->
                <figure class="gallery-item" data-category="beaches">
                    <div class="image-wrapper">
                        <img src="https://via.placeholder.com/600x400?text=Bali+Coast" alt="Crystal clear water in Bali" class="gallery-image">
                        <div class="image-overlay">
                            <span class="view-icon">View Photo</span>
                        </div>
                    </div>
                    <figcaption class="gallery-caption">
                        <h3 class="photo-title">Azure Waters</h3>
                        <p class="photo-location">Bali, Indonesia</p>
                    </figcaption>
                </figure>

                <!-- Gallery Item 4 -->
                <figure class="gallery-item" data-category="mountains">
                    <div class="image-wrapper">
                        <img src="https://via.placeholder.com/600x400?text=Patagonia" alt="The peaks of Torres del Paine" class="gallery-image">
                        <div class="image-overlay">
                            <span class="view-icon">View Photo</span>
                        </div>
                    </div>
                    <figcaption class="gallery-caption">
                        <h3 class="photo-title">Patagonia Peaks</h3>
                        <p class="photo-location">Chile</p>
                    </figcaption>
                </figure>

            </div>
        </section>

        <!-- Lightbox Modal (Hidden by default) -->
        <div class="lightbox-modal" aria-hidden="true">
            <button class="modal-close">&times;</button>
            <div class="modal-content">
                <img src="" alt="" class="modal-image">
                <div class="modal-caption"></div>
            </div>
            <button class="modal-prev">&#10094;</button>
            <button class="modal-next">&#10095;</button>
        </div>
    </main>

    <!-- Footer Section -->
    <footer class="site-footer">
        <div class="footer-container">
            <div class="footer-info">
                <p>&copy; 2023 Wanderlust Travel Blog. All rights reserved.</p>
            </div>
            <div class="footer-socials">
                <a href="#" class="social-link">Instagram</a>
                <a href="#" class="social-link">Pinterest</a>
                <a href="#" class="social-link">Twitter</a>
            </div>
        </div>
    </footer>


    <script>
        /**
         * Wanderlust Captures - Gallery Functionality
         * Handles filtering of gallery items and the lightbox modal interaction.
         */
        
        document.addEventListener('DOMContentLoaded', () => {
            // --- DOM Elements ---
            const filterButtons = document.querySelectorAll('.filter-btn');
            const galleryItems = document.querySelectorAll('.gallery-item');
            const lightbox = document.querySelector('.lightbox-modal');
            const modalImg = lightbox.querySelector('.modal-image');
            const modalCaption = lightbox.querySelector('.modal-caption');
            const closeBtn = lightbox.querySelector('.modal-close');
            const prevBtn = lightbox.querySelector('.modal-prev');
            const nextBtn = lightbox.querySelector('.modal-next');
        
            // State management
            let currentIndex = 0;
            let activeItems = [...galleryItems]; // Tracks items currently visible after filtering
        
            // --- Filtering Logic ---
        
            /**
             * Filters the gallery items based on the category
             * @param {string} filterValue - The category to filter by
             */
            const filterGallery = (filterValue) => {
                galleryItems.forEach(item => {
                    const category = item.getAttribute('data-category');
                    if (filterValue === 'all' || category === filterValue) {
                        item.style.display = 'block';
                        item.classList.remove('hidden');
                    } else {
                        item.style.display = 'none';
                        item.classList.add('hidden');
                    }
                });
        
                // Update the list of active items for lightbox navigation
                activeItems = Array.from(galleryItems).filter(item => !item.classList.contains('hidden'));
            };
        
            filterButtons.forEach(button => {
                button.addEventListener('click', () => {
                    // Update UI for active button
                    filterButtons.forEach(btn => btn.classList.remove('active'));
                    button.classList.add('active');
        
                    // Execute filter
                    const filterValue = button.getAttribute('data-filter');
                    filterGallery(filterValue);
                });
            });
        
            // --- Lightbox Logic ---
        
            /**
             * Updates the content of the lightbox based on the current index
             * @param {number} index - Index of the item in the activeItems array
             */
            const updateLightboxContent = (index) => {
                const item = activeItems[index];
                const img = item.querySelector('.gallery-image');
                const title = item.querySelector('.photo-title').textContent;
                const location = item.querySelector('.photo-location').textContent;
        
                modalImg.src = img.src;
                modalImg.alt = img.alt;
                modalCaption.innerHTML = `<h3>${title}</h3><p>${location}</p>`;
                currentIndex = index;
            };
        
            const openLightbox = (index) => {
                updateLightboxContent(index);
                lightbox.classList.add('active');
                lightbox.setAttribute('aria-hidden', 'false');
                document.body.style.overflow = 'hidden'; // Prevent scrolling when modal is open
            };
        
            const closeLightbox = () => {
                lightbox.classList.remove('active');
                lightbox.setAttribute('aria-hidden', 'true');
                document.body.style.overflow = 'auto';
            };
        
            const showNext = () => {
                const nextIndex = (currentIndex + 1) % activeItems.length;
                updateLightboxContent(nextIndex);
            };
        
            const showPrev = () => {
                const prevIndex = (currentIndex - 1 + activeItems.length) % activeItems.length;
                updateLightboxContent(prevIndex);
            };
        
            // --- Event Listeners ---
        
            // Open lightbox when clicking on a gallery item
            galleryItems.forEach((item) => {
                const wrapper = item.querySelector('.image-wrapper');
                wrapper.addEventListener('click', () => {
                    const index = activeItems.indexOf(item);
                    if (index !== -1) openLightbox(index);
                });
            });
        
            // Lightbox control buttons
            closeBtn.addEventListener('click', closeLightbox);
            nextBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                showNext();
            });
            prevBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                showPrev();
            });
        
            // Close lightbox when clicking outside the content
            lightbox.addEventListener('click', (e) => {
                if (e.target === lightbox) closeLightbox();
            });
        
            // Keyboard Navigation
            document.addEventListener('keydown', (e) => {
                if (!lightbox.classList.contains('active')) return;
        
                if (e.key === 'Escape') closeLightbox();
                if (e.key === 'ArrowRight') showNext();
                if (e.key === 'ArrowLeft') showPrev();
            });
        });
    </script>
</body>
<!-- JavaScript will be added here -->
</html>
```

**Result:** Successfully wrote gallery.html (auto-applied)
