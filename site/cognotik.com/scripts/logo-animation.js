/**
 * Logo animation functionality for Cognotik website
 */

function rnd() {
    let x = 0;
    for (let i = 0; i < 6; i++) {
        x += Math.random() * 2 - 1;
    }
    return x;
}

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

        // Plasma field helper: returns a value between 0 and 1 using a recursive, randomized infilling algorithm (Diamond–Square)
        function generatePlasmaGrid(size) {
            const grid = [];
            for (let i = 0; i < size; i++) {
                grid[i] = new Array(size).fill(0);
            }
            // Initialize corners
            grid[0][0] = Math.random() * 0.8 + 0.1; // Keep values away from extremes for smoother transitions
            grid[0][size - 1] = Math.random() * 0.8 + 0.1;
            grid[size - 1][0] = Math.random() * 0.8 + 0.1;
            grid[size - 1][size - 1] = Math.random() * 0.8 + 0.1;

            let step = size - 1;
            let offset = 1;
            while(step > 1) {
                const half = step / 2;
                // Diamond step
                for (let y = 0; y < size - 1; y += step) { // Iterate y outer for cache coherency (minor)
                    for (let x = 0; x < size - 1; x += step) {
                        const avg = (grid[x][y] + grid[x][y + step] +
                                     grid[x + step][y] + grid[x + step][y + step]) / 4;
                        grid[x + half][y + half] = avg + rnd() * offset;
                    }
                }
                // Square step
                for (let y = 0; y < size; y += half) { // Iterate y outer
                    for (let x = (y + half) % step; x < size; x += step) {
                        let sum = 0, count = 0;
                        if (x - half >= 0) { sum += grid[x - half][y]; count++; }
                        if (x + half < size) { sum += grid[x + half][y]; count++; }
                        if (y - half >= 0) { sum += grid[x][y - half]; count++; }
                        if (y + half < size) { sum += grid[x][y + half]; count++; }
                        grid[x][y] = (sum / count) + rnd() * offset;
                    }
                }
                step = half;
                offset *= 0.5;
            }
            // Normalize grid values to [0,1]
            let min = Infinity, max = -Infinity;
            for (let i = 0; i < size; i++) { // Iterate y outer
                for (let j = 0; j < size; j++) { // Iterate x inner
                    if (grid[i][j] < min) min = grid[i][j];
                    if (grid[i][j] > max) max = grid[i][j];
                }
            }
            for (let i = 0; i < size; i++) {
                for (let j = 0; j < size; j++) {
                    grid[i][j] = (grid[i][j] - min) / (max - min);
                }
            }
            return grid;
        }

        function samplePlasma(grid, x, y) {
            const size = grid.length;
            const gx = x * (size - 1);
            const gy = y * (size - 1);
            const x0 = Math.floor(gx);
            const x1 = Math.min(x0 + 1, size - 1);
            const y0 = Math.floor(gy);
            const y1 = Math.min(y0 + 1, size - 1);
            const tx = gx - x0;
            const ty = gy - y0;
            const a = grid[x0][y0];
            const b = grid[x1][y0];
            const c = grid[x0][y1];
            const d = grid[x1][y1];
            const ab = a * (1 - tx) + b * tx;
            const cd = c * (1 - tx) + d * tx;
            return ab * (1 - ty) + (cd * ty);
        }

        // Generate the plasma grid once (size 129 = 2^7 + 1 for sufficient detail)
        const plasmaGrid = generatePlasmaGrid(64 + 1); // Further reduced size for performance, 2^6 + 1

        // Plasma field function using the generated plasma grid.
        // The time parameter z is used to shift the sampling, creating animation.
        function plasma(x, y, z) {
            const shift = z % 1;
            return samplePlasma(plasmaGrid, (x + shift) % 1, (y + shift) % 1);
        }

        // --- Animate the gradient's center over time (auto) ---
        let t = 0;
        let isUserInteracting = false;
        let animationFrameId = null; // To potentially cancel the animation frame
        let leaveTimeoutId = null; // Timeout for smooth transition on leave

        function animateGradient() {
            t += 0.01;
           // Only animate gradient center if user is not interacting
           if (!isUserInteracting) {

               // Animate gradient center (cx,cy) in a Lissajous curve path for more visual interest
               const cx = 50 + 25 * Math.sin(t * 0.8);
               const cy = 50 + 25 * Math.cos(t * 1.1);
               // Animate focal point (fx,fy) and radius (r) for more dynamism
               const fx = 50 + 20 * Math.cos(t * 0.65 + Math.PI / 3); // Different speeds and phases
               const fy = 50 + 20 * Math.sin(t * 0.95 + Math.PI / 1.5);
               const radius = 65 + 15 * Math.sin(t * 0.45); // Pulsating radius
               grad.setAttribute('cx', `${cx}%`);
               grad.setAttribute('cy', `${cy}%`);
               grad.setAttribute('fx', `${fx}%`);
               grad.setAttribute('fy', `${fy}%`);
               grad.setAttribute('r', `${radius}%`);
           }

            // Update each color stop based on a 2D slice of a 3D plasma field.
            grad.querySelectorAll('stop').forEach((stop, i) => {
                // Parse the original offset (0-100) and convert to [0,1] coordinates.
                const offsetVal = parseFloat(stop.getAttribute('offset'));
                const xCoord = offsetVal / 100;
                const yCoord = offsetVal / 100; // for this example, using the same value for x and y

                // Compute plasma field value for the (x,y) slice at depth = t.
                const plasmaValue = plasma(xCoord, yCoord, t / 12); // Slightly faster plasma evolution

                // Map the plasma value [0,1] to a hue angle [0,360]
                const hue = Math.floor(plasmaValue * 360 * 2);

                // Update the stop color dynamically.
                stop.setAttribute('stop-color', `hsl(${hue},100%,50%)`);
            });

            animationFrameId = requestAnimationFrame(animateGradient);
        }
        animateGradient();

        function onPointerMove(e) {
           clearTimeout(leaveTimeoutId); // Clear any pending leave transition
           isUserInteracting = true;
            // Get bounding rect of SVG
            const rect = heroLogo.getBoundingClientRect();
            let x = ((e.clientX - rect.left) / rect.width) * 100;
            let y = ((e.clientY - rect.top) / rect.height) * 100;
            // Clamp to [0,100]
            x = Math.max(0, Math.min(100, x));
            y = Math.max(0, Math.min(100, y));
            // Make the interactive focal point also move
            const fxInteractive = 50 + (x - 50) * 0.8; // Focal point follows cursor but less extremely
            const fyInteractive = 50 + (y - 50) * 0.8;
            grad.setAttribute('cx', `${x}%`);
            grad.setAttribute('cy', `${y}%`);
            grad.setAttribute('fx', `${fxInteractive}%`);
            grad.setAttribute('fy', `${fyInteractive}%`);
        }
        function onPointerLeave() {
           // Delay setting isUserInteracting to false for a smoother transition back to auto-animation
           leaveTimeoutId = setTimeout(() => {
               isUserInteracting = false;
               // Optionally, smoothly animate back to center or let the auto-animation take over
               // grad.style.transition = 'cx 0.5s ease, cy 0.5s ease'; // Add transition in CSS if desired
               // grad.setAttribute('cx', '50%');
               // grad.setAttribute('cy', '50%');
           }, 200); // 200ms delay
        }
       // Add a small delay when pointer leaves to make transition smoother
       function onPointerEnter() {
           clearTimeout(leaveTimeoutId); // Clear timeout if pointer re-enters quickly
           isUserInteracting = true;
       }
       
        heroLogo.addEventListener('pointermove', onPointerMove);
        heroLogo.addEventListener('pointerleave', onPointerLeave);
        heroLogo.addEventListener('pointerenter', onPointerEnter);
    }
}

document.addEventListener('DOMContentLoaded', function() {
    initLogoAnimation();
});