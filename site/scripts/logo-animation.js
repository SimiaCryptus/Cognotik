/**
 * Logo animation functionality for Cognotik website
 */

/**
 * Initialize logo animations with animated, multicolored gradient responsive to cursor
 */
function initLogoAnimation() {
    const heroLogo = document.getElementById('hero-logo');
    // Optionally, navLogo can be handled similarly if needed
    // const navLogo = document.querySelector('.logo svg');

    if (heroLogo) {
        // --- Inject animated gradient defs if not already present ---
        let defs = heroLogo.querySelector('defs');
        if (!defs) {
            defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs');
            heroLogo.insertBefore(defs, heroLogo.firstChild);
        }

        // Remove any existing gradient
        let oldGrad = heroLogo.querySelector('#animated-gradient');
        if (oldGrad) oldGrad.remove();

        // Create a multicolored, animated radial gradient
        const grad = document.createElementNS('http://www.w3.org/2000/svg', 'radialGradient');
        grad.setAttribute('id', 'animated-gradient');
        grad.setAttribute('cx', '50%');
        grad.setAttribute('cy', '50%');
        grad.setAttribute('r', '70%');
        grad.setAttribute('fx', '50%');
        grad.setAttribute('fy', '50%');
        grad.setAttribute('gradientUnits', 'objectBoundingBox');

        // Multicolored stops
        const stops = [
            { offset: '0%', color: '#ff00cc' },
            { offset: '25%', color: '#3333ff' },
            { offset: '50%', color: '#00ffcc' },
            { offset: '75%', color: '#ffcc00' },
            { offset: '100%', color: '#ff0066' }
        ];
        stops.forEach(stop => {
            const s = document.createElementNS('http://www.w3.org/2000/svg', 'stop');
            s.setAttribute('offset', stop.offset);
            s.setAttribute('stop-color', stop.color);
            grad.appendChild(s);
        });
        defs.appendChild(grad);

        // Apply the gradient to all paths
        heroLogo.querySelectorAll('path').forEach(path => {
            path.setAttribute('fill', 'url(#animated-gradient)');
        });

        // --- Animate the gradient's center over time (auto) ---
        let t = 0;
        function animateGradient() {
            t += 0.01;
            // Animate cx/cy in a circle
            const cx = 50 + 20 * Math.cos(t * 1.2);
            const cy = 50 + 20 * Math.sin(t);
            grad.setAttribute('cx', `${cx}%`);
            grad.setAttribute('cy', `${cy}%`);
            // Animate color stops (optional: hue rotation)
            grad.querySelectorAll('stop').forEach((stop, i) => {
                // Optionally animate the color stops for a more dynamic effect
                // (left as static for now)
            });
            requestAnimationFrame(animateGradient);
        }
        animateGradient();

        // --- Make gradient center follow cursor when over logo ---
        function onPointerMove(e) {
            // Get bounding rect of SVG
            const rect = heroLogo.getBoundingClientRect();
            let x = ((e.clientX - rect.left) / rect.width) * 100;
            let y = ((e.clientY - rect.top) / rect.height) * 100;
            // Clamp to [0,100]
            x = Math.max(0, Math.min(100, x));
            y = Math.max(0, Math.min(100, y));
            grad.setAttribute('cx', `${x}%`);
            grad.setAttribute('cy', `${y}%`);
        }
        function onPointerLeave() {
            // Reset to center
            grad.setAttribute('cx', '50%');
            grad.setAttribute('cy', '50%');
        }
        heroLogo.addEventListener('pointermove', onPointerMove);
        heroLogo.addEventListener('pointerleave', onPointerLeave);
    }
}

document.addEventListener('DOMContentLoaded', function() {
    initLogoAnimation();
});