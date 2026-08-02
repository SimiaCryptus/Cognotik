// 2D rendering: grid, dipole disks, and orientation arrows. No physics.

export class SceneRenderer {
    constructor(canvas) {
        this.canvas = canvas;
        this.ctx = canvas.getContext('2d');
        this.offset = [0, 0]; // world offset to keep lattice centered
        this.dpr = window.devicePixelRatio || 1;
        this.resize();
        window.addEventListener('resize', () => this.resize());
    }

    resize() {
        const rect = this.canvas.getBoundingClientRect();
        this.canvas.width = rect.width * this.dpr;
        this.canvas.height = rect.height * this.dpr;
        this.w = rect.width;
        this.h = rect.height;
    }

    /** Screen (canvas-local px) -> world. World origin is canvas center + offset. */
    screenToWorld(sx, sy) {
        return [sx - this.w / 2 - this.offset[0], sy - this.h / 2 - this.offset[1]];
    }

    worldToScreen(wx, wy) {
        return [wx + this.w / 2 + this.offset[0], wy + this.h / 2 + this.offset[1]];
    }

    render(lattice, opts = {}) {
        const ctx = this.ctx;
        const {highlightMode = null, modeVec = null} = opts;
        ctx.save();
        ctx.scale(this.dpr, this.dpr);
        ctx.clearRect(0, 0, this.w, this.h);

        const pitch = lattice.pitch;

        // grid dots
        ctx.fillStyle = '#2a2a33';
        const halfW = Math.ceil(this.w / pitch / 2) + 1;
        const halfH = Math.ceil(this.h / pitch / 2) + 1;
        for (let gx = -halfW; gx <= halfW; gx++) {
            for (let gy = -halfH; gy <= halfH; gy++) {
                const [sx, sy] = this.worldToScreen(gx * pitch, gy * pitch);
                ctx.beginPath();
                ctx.arc(sx, sy, 1.5, 0, Math.PI * 2);
                ctx.fill();
            }
        }

        // magnets
        const R = pitch * 0.36;
        lattice.magnets.forEach((mg, idx) => {
            const [wx, wy] = lattice.cellToWorld(mg.cell);
            const [sx, sy] = this.worldToScreen(wx, wy);

            // disk
            ctx.beginPath();
            ctx.arc(sx, sy, R, 0, Math.PI * 2);
            ctx.fillStyle = '#2c3540';
            ctx.fill();
            ctx.lineWidth = 1.5;
            ctx.strokeStyle = '#4a6a8a';
            ctx.stroke();

            // dipole arrow (N red / S blue halves)
            const th = mg.theta;
            const ex = Math.cos(th),
                ey = Math.sin(th);
            // north half
            ctx.beginPath();
            ctx.moveTo(sx, sy);
            ctx.lineTo(sx + ex * R, sy + ey * R);
            ctx.strokeStyle = '#e05555';
            ctx.lineWidth = 3;
            ctx.stroke();
            // south half
            ctx.beginPath();
            ctx.moveTo(sx, sy);
            ctx.lineTo(sx - ex * R, sy - ey * R);
            ctx.strokeStyle = '#5577e0';
            ctx.lineWidth = 3;
            ctx.stroke();
            // arrowhead
            const hx = sx + ex * R,
                hy = sy + ey * R;
            const a = 0.5;
            ctx.beginPath();
            ctx.moveTo(hx, hy);
            ctx.lineTo(
                hx - (ex * Math.cos(a) - ey * Math.sin(a)) * 8,
                hy - (ex * Math.sin(a) + ey * Math.cos(a)) * 8,
            );
            ctx.moveTo(hx, hy);
            ctx.lineTo(
                hx - (ex * Math.cos(-a) - ey * Math.sin(-a)) * 8,
                hy - (ex * Math.sin(-a) + ey * Math.cos(-a)) * 8,
            );
            ctx.strokeStyle = '#e05555';
            ctx.lineWidth = 2;
            ctx.stroke();

            // mode overlay: show mode displacement as a green wedge
            if (modeVec && idx < modeVec.length) {
                const amp = modeVec[idx];
                ctx.beginPath();
                ctx.arc(sx, sy, R + 5, th - 0.4, th - 0.4 + amp * 1.5);
                ctx.strokeStyle = amp >= 0 ? '#6f6' : '#f66';
                ctx.lineWidth = 2;
                ctx.stroke();
            }

            // id label
            ctx.fillStyle = '#889';
            ctx.font = '10px monospace';
            ctx.fillText(String(mg.id), sx + R + 2, sy - R);
        });

        ctx.restore();
    }
}
