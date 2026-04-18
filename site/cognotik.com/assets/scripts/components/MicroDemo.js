/**
 * MicroDemo — expandable video preview card web component.
 *
 * Usage:
 *   <micro-demo
 *     title="Build a Plugin in 90 Seconds"
 *     hook="Watch Cognotik plan, scaffold, and run a real plugin — from idea to execution."
 *     video="tours/core/edit/install.mp4"
 *     poster=""
 *     bullets='["AI plans the full task tree","Each step is reviewable before execution","Output appears live in the workspace"]'
 *     link="tours/core/index.html"
 *     link-label="Watch Full Tour"
 *     icon="🚀"
 *   ></micro-demo>
 */
class MicroDemo extends HTMLElement {
    static get observedAttributes() {
        return ['title', 'hook', 'video', 'poster', 'bullets', 'link', 'link-label', 'icon'];
    }

    constructor() {
        super();
        this._expanded = false;
        this._videoLoaded = false;
    }

    connectedCallback() {
        this.render();
        this.querySelector('.micro-demo-toggle').addEventListener('click', () => this.toggle());
    }

    get bullets() {
        try {
            return JSON.parse(this.getAttribute('bullets') || '[]');
        } catch {
            return [];
        }
    }

    toggle() {
        this._expanded = !this._expanded;
        this.classList.toggle('micro-demo--expanded', this._expanded);

        const expandedArea = this.querySelector('.micro-demo-expanded');
        const chevron = this.querySelector('.micro-demo-chevron');
        const video = this.querySelector('video');

        if (this._expanded) {
            expandedArea.style.maxHeight = expandedArea.scrollHeight + 200 + 'px';
            chevron.style.transform = 'rotate(180deg)';
            // Lazy-load video source on first expand
            if (video && !this._videoLoaded) {
                const src = this.getAttribute('video');
                if (src) {
                    video.src = src;
                    video.load();
                    this._videoLoaded = true;
                }
            }
        } else {
            expandedArea.style.maxHeight = '0';
            chevron.style.transform = 'rotate(0deg)';
            if (video) {
                video.pause();
            }
        }
    }

    render() {
        const title = this.getAttribute('title') || 'Demo';
        const hook = this.getAttribute('hook') || '';
        const poster = this.getAttribute('poster') || '';
        const icon = this.getAttribute('icon') || '▶️';
        const link = this.getAttribute('link') || '';
        const linkLabel = this.getAttribute('link-label') || 'Watch Full Tour';
        const bulletItems = this.bullets;

        const bulletsHTML = bulletItems.length
            ? `<ul class="micro-demo-bullets">${bulletItems.map(b => `<li>${b}</li>`).join('')}</ul>`
            : '';

        const linkHTML = link
            ? `<a class="btn btn-sm btn-outline micro-demo-link" href="${link}">${linkLabel} →</a>`
            : '';

        this.innerHTML = `
            <div class="micro-demo-card">
                <button class="micro-demo-toggle" aria-expanded="false" aria-label="Expand demo: ${title}">
                    <span class="micro-demo-icon">${icon}</span>
                    <div class="micro-demo-header-text">
                        <span class="micro-demo-title">${title}</span>
                        <span class="micro-demo-hook">${hook}</span>
                    </div>
                    <span class="micro-demo-chevron" aria-hidden="true">
                        <i class="fas fa-chevron-down"></i>
                    </span>
                </button>
                <div class="micro-demo-expanded" aria-hidden="true">
                    <div class="micro-demo-video-wrap">
                        <video
                            muted
                            playsinline
                            controls
                            preload="none"
                            ${poster ? `poster="${poster}"` : ''}
                        ></video>
                    </div>
                    ${bulletsHTML}
                    ${linkHTML}
                </div>
            </div>
        `;
    }
}

customElements.define('micro-demo', MicroDemo);