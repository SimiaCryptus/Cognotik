/**
 * assets.js - Procedural Sprite Drawing Library
 * 
 * Draws all game sprites using HTML5 Canvas 2D API without external images.
 * Each sprite is drawn pixel-art style to match the classic Super Mario Bros aesthetic.
 */

const Assets = (() => {
    // ─── Sprite Cache ────────────────────────────────────────────────────────
    const cache = {};

    /**
     * Creates an off-screen canvas and returns {canvas, ctx}.
     */
    function makeCanvas(w, h) {
        const canvas = document.createElement('canvas');
        canvas.width  = w;
        canvas.height = h;
        return { canvas, ctx: canvas.getContext('2d') };
    }

    /**
     * Draws a pixel-art grid onto a context.
     * @param {CanvasRenderingContext2D} ctx
     * @param {string[][]} grid  - 2-D array of colour strings ('' = transparent)
     * @param {number} px        - pixel size (scale factor)
     * @param {number} ox        - x offset
     * @param {number} oy        - y offset
     */
    function drawGrid(ctx, grid, px = 1, ox = 0, oy = 0) {
        for (let row = 0; row < grid.length; row++) {
            for (let col = 0; col < grid[row].length; col++) {
                const colour = grid[row][col];
                if (!colour || colour === '.') continue;
                ctx.fillStyle = colour;
                ctx.fillRect(ox + col * px, oy + row * px, px, px);
            }
        }
    }

    // ─── Colour Palettes ─────────────────────────────────────────────────────
    const C = {
        // Mario
        mSkin:   '#FFA07A',
        mHat:    '#CC0000',
        mShirt:  '#CC0000',
        mOver:   '#0000CC',
        mBoot:   '#8B4513',
        mBrown:  '#8B4513',
        mEye:    '#000000',
        mBtn:    '#FFD700',
        mWhite:  '#FFFFFF',

        // Goomba
        gBrown:  '#8B4513',
        gDark:   '#5C2E00',
        gFeet:   '#5C2E00',
        gEye:    '#FFFFFF',
        gPupil:  '#000000',
        gBrow:   '#000000',

        // Koopa
        kShell:  '#228B22',
        kLight:  '#90EE90',
        kSkin:   '#FFFF99',
        kEye:    '#000000',
        kFeet:   '#FFFF99',

        // Mushroom
        muRed:   '#CC0000',
        muWhite: '#FFFFFF',
        muStem:  '#FFCC99',

        // Fire Flower
        ffOrange:'#FF6600',
        ffYellow:'#FFFF00',
        ffGreen: '#228B22',
        ffRed:   '#CC0000',

        // Star
        stYellow:'#FFD700',
        stOrange:'#FFA500',
        stEye:   '#000000',

        // Coin
        coGold:  '#FFD700',
        coDark:  '#B8860B',

        // Brick
        brRed:   '#CC4400',
        brDark:  '#993300',
        brLight: '#FF6633',
        brMortar:'#FFCC99',

        // Question Block
        qbYellow:'#FFD700',
        qbOrange:'#FFA500',
        qbDark:  '#CC8800',
        qbBrown: '#8B4513',
        qbWhite: '#FFFFFF',

        // Pipe
        piGreen: '#228B22',
        piLight: '#44BB44',
        piDark:  '#115511',

        // Ground
        grBrown: '#C84C0C',
        grDark:  '#8B3A0A',
        grLight: '#E8622C',
        grTan:   '#F0A060',

        // Sky
        skyBlue: '#5C94FC',

        // Cloud
        clWhite: '#FFFFFF',
        clGray:  '#DDDDDD',

        // Flag
        flGreen: '#228B22',
        flPole:  '#AAAAAA',

        // Fireball
        fbOrange:'#FF6600',
        fbYellow:'#FFFF00',
        fbWhite: '#FFFFFF',
    };

    // =========================================================================
    // MARIO  (small – 16 × 16 px, rendered at 2× → 32 × 32)
    // =========================================================================
    function drawMarioSmall(state = 'idle') {
        const key = `mario_small_${state}`;
        if (cache[key]) return cache[key];

        const PX = 2, W = 16, H = 16;
        const { canvas, ctx } = makeCanvas(W * PX, H * PX);

        // Pixel grid (16 rows × 16 cols)
        // Colours: R=hat/shirt, S=skin, B=overalls, T=boot, .=transparent
        const idle = [
            '....RRRR........',
            '...RRRRRRRR.....',
            '...TTTSSST......',
            '..TSTSTSTS......',
            '..SSSSSSSS......',
            '...SSRRSSS......',
            '..SSSSSSSS......',
            '.BBRSSRSSBB.....',
            'BBBBSSSSBBBB....',
            'BBBSSSSSSBB.....',
            '..SSSSSSSS......',
            '..TTSS..SSTT....',
            '.TTTSS..SSTTT...',
            'TTTTSS..SSTTT...',
            '........SSSS....',
            '................',
        ];

        const walk1 = [
            '....RRRR........',
            '...RRRRRRRR.....',
            '...TTTSSST......',
            '..TSTSTSTS......',
            '..SSSSSSSS......',
            '...SSRRSSS......',
            '..SSSSSSSS......',
            '.BBRSSRSSBB.....',
            'BBBBSSSSBBBB....',
            'BBBSSSSSSBB.....',
            '..SSSSSSSS......',
            '...TTSSSS.......',
            '..TTTSSTT.......',
            '.TTTTSSTT.......',
            '....SSSS........',
            '................',
        ];

        const walk2 = [
            '....RRRR........',
            '...RRRRRRRR.....',
            '...TTTSSST......',
            '..TSTSTSTS......',
            '..SSSSSSSS......',
            '...SSRRSSS......',
            '..SSSSSSSS......',
            '.BBRSSRSSBB.....',
            'BBBBSSSSBBBB....',
            'BBBSSSSSSBB.....',
            '..SSSSSSSS......',
            '.SSTT....TTSS...',
            'SSTTT....TTTSS..',
            'SSTTT....TTTSS..',
            '................',
            '................',
        ];

        const jump = [
            '....RRRR........',
            '...RRRRRRRR.....',
            '...TTTSSST......',
            '..TSTSTSTS......',
            '..SSSSSSSS......',
            '...SSRRSSS......',
            'BBSSSSSSSSBB....',
            'BBBSSSSSSBB.....',
            'BBBSSSSSSBB.....',
            '..SSSSSSSS......',
            '..TTSSSSTT......',
            '.TTTSSSSTT......',
            'TTTTSSSSTT......',
            '................',
            '................',
            '................',
        ];

        const grids = { idle, walk1, walk2, jump };
        const grid  = grids[state] || idle;

        const colourMap = {
            'R': C.mHat,
            'S': C.mSkin,
            'B': C.mOver,
            'T': C.mBoot,
            '.': null,
        };

        for (let row = 0; row < grid.length; row++) {
            for (let col = 0; col < grid[row].length; col++) {
                const ch = grid[row][col];
                const colour = colourMap[ch];
                if (!colour) continue;
                ctx.fillStyle = colour;
                ctx.fillRect(col * PX, row * PX, PX, PX);
            }
        }

        cache[key] = canvas;
        return canvas;
    }

    // =========================================================================
    // MARIO  (big – 16 × 32 px, rendered at 2× → 32 × 64)
    // =========================================================================
    function drawMarioBig(state = 'idle') {
        const key = `mario_big_${state}`;
        if (cache[key]) return cache[key];

        const PX = 2, W = 16, H = 32;
        const { canvas, ctx } = makeCanvas(W * PX, H * PX);

        // Top half (hat + head)
        const top = [
            '....RRRR........',
            '...RRRRRRRR.....',
            '..RRRRRRRRRRR...',
            '...TTTSSST......',
            '..TSTSTSTS......',
            '..SSSSSSSS......',
            '...SSRRSSS......',
            '..SSSSSSSS......',
        ];

        // Bottom half (body + legs)
        const bottom_idle = [
            '..BBRSSRSSBB....',
            '.BBBBSSSSBBBB...',
            'BBBBBSSSSBBBB...',
            'BBBBBSSSSBBBB...',
            '..BBSSSSSSBB....',
            '..SSSSSSSSSS....',
            '..SSSSSSSSSS....',
            '..TTSS..SSTT....',
            '.TTTSS..SSTTT...',
            'TTTTSS..SSTTT...',
            '........SSSS....',
            '................',
            '................',
            '................',
            '................',
            '................',
        ];

        const bottom_walk1 = [
            '..BBRSSRSSBB....',
            '.BBBBSSSSBBBB...',
            'BBBBBSSSSBBBB...',
            'BBBBBSSSSBBBB...',
            '..BBSSSSSSBB....',
            '..SSSSSSSSSS....',
            '..SSSSSSSSSS....',
            '...TTSSSSTT.....',
            '..TTTSSSSTT.....',
            '.TTTTSSSSTT.....',
            '....SSSSSS......',
            '................',
            '................',
            '................',
            '................',
            '................',
        ];

        const colourMap = {
            'R': C.mHat,
            'S': C.mSkin,
            'B': C.mOver,
            'T': C.mBoot,
            '.': null,
        };

        const bottoms = { idle: bottom_idle, walk1: bottom_walk1, walk2: bottom_walk1, jump: bottom_idle };
        const bottomGrid = bottoms[state] || bottom_idle;

        [top, bottomGrid].forEach((grid, gi) => {
            const yOff = gi * 8;
            for (let row = 0; row < grid.length; row++) {
                for (let col = 0; col < grid[row].length; col++) {
                    const ch = grid[row][col];
                    const colour = colourMap[ch];
                    if (!colour) continue;
                    ctx.fillStyle = colour;
                    ctx.fillRect(col * PX, (yOff + row) * PX, PX, PX);
                }
            }
        });

        cache[key] = canvas;
        return canvas;
    }

    // =========================================================================
    // GOOMBA  (16 × 16, rendered at 2× → 32 × 32)
    // =========================================================================
    function drawGoomba(state = 'walk1') {
        const key = `goomba_${state}`;
        if (cache[key]) return cache[key];

        const PX = 2, W = 16, H = 16;
        const { canvas, ctx } = makeCanvas(W * PX, H * PX);

        // B=brown body, D=dark brown, F=feet, W=white eye, P=pupil, K=brow
        const walk1 = [
            '................',
            '....BBBBBB......',
            '...BBBBBBBB.....',
            '..BBBBBBBBBB....',
            '..BBBBBBBBBB....',
            '..WBBBBBBBWB....',
            '..WWBBBBBWWB....',
            '..PPWBBBWPPB....',
            '..PPWBBBWPPB....',
            '...KBBBBBK......',
            '....BBBBBB......',
            '...BBBBBBBB.....',
            '..DDBBBBBBDD....',
            '..DDBBBBBBDD....',
            '..FF......FF....',
            '................',
        ];

        const walk2 = [
            '................',
            '....BBBBBB......',
            '...BBBBBBBB.....',
            '..BBBBBBBBBB....',
            '..BBBBBBBBBB....',
            '..WBBBBBBBWB....',
            '..WWBBBBBWWB....',
            '..PPWBBBWPPB....',
            '..PPWBBBWPPB....',
            '...KBBBBBK......',
            '....BBBBBB......',
            '...BBBBBBBB.....',
            '..DDBBBBBBDD....',
            '..DDBBBBBBDD....',
            '.FF........FF...',
            '................',
        ];

        const squished = [
            '................',
            '................',
            '................',
            '................',
            '................',
            '....BBBBBB......',
            '...BBBBBBBB.....',
            '..BBBBBBBBBB....',
            '..WBBBBBBBWB....',
            '..WWBBBBBWWB....',
            '..PPWBBBWPPB....',
            '...KBBBBBK......',
            '....BBBBBB......',
            '..DDBBBBBBDD....',
            '..FF......FF....',
            '................',
        ];

        const grids = { walk1, walk2, squished };
        const grid  = grids[state] || walk1;

        const colourMap = {
            'B': C.gBrown,
            'D': C.gDark,
            'F': C.gFeet,
            'W': C.gEye,
            'P': C.gPupil,
            'K': C.gBrow,
            '.': null,
        };

        for (let row = 0; row < grid.length; row++) {
            for (let col = 0; col < grid[row].length; col++) {
                const colour = colourMap[grid[row][col]];
                if (!colour) continue;
                ctx.fillStyle = colour;
                ctx.fillRect(col * PX, row * PX, PX, PX);
            }
        }

        cache[key] = canvas;
        return canvas;
    }

    // =========================================================================
    // KOOPA TROOPA  (16 × 24, rendered at 2× → 32 × 48)
    // =========================================================================
    function drawKoopa(state = 'walk1') {
        const key = `koopa_${state}`;
        if (cache[key]) return cache[key];

        const PX = 2, W = 16, H = 24;
        const { canvas, ctx } = makeCanvas(W * PX, H * PX);

        // G=shell green, L=light green, S=skin/yellow, E=eye, F=feet
        const walk1 = [
            '................',
            '....SSSSSS......',
            '...SSSSSSSS.....',
            '..SSESSSSSSS....',
            '..SSEESSSSSS....',
            '...GGGGGGG......',
            '..GGGGGGGGG.....',
            '.LGGGGGGGGGL....',
            '.LGGGGGGGGGL....',
            '.LGGGGGGGGGL....',
            '..GGGGGGGGG.....',
            '...GGGGGGG......',
            '....GGGGG.......',
            '....SSSSS.......',
            '....SSSSS.......',
            '...SSSSSSS......',
            '..FFSS..SSFF....',
            '.FFFSS..SSFFF...',
            '................',
            '................',
            '................',
            '................',
            '................',
            '................',
        ];

        const shell = [
            '................',
            '................',
            '................',
            '................',
            '....GGGGGGG.....',
            '...GGGGGGGGG....',
            '..LGGGGGGGGGGL..',
            '..LGGGGGGGGGGL..',
            '..LGGGGGGGGGGL..',
            '..LGGGGGGGGGGL..',
            '..LGGGGGGGGGGL..',
            '...GGGGGGGGG....',
            '....GGGGGGG.....',
            '................',
            '................',
            '................',
            '................',
            '................',
            '................',
            '................',
            '................',
            '................',
            '................',
            '................',
        ];

        const grids = { walk1, walk2: walk1, shell };
        const grid  = grids[state] || walk1;

        const colourMap = {
            'G': C.kShell,
            'L': C.kLight,
            'S': C.kSkin,
            'E': C.kEye,
            'F': C.kFeet,
            '.': null,
        };

        for (let row = 0; row < grid.length; row++) {
            for (let col = 0; col < grid[row].length; col++) {
                const colour = colourMap[grid[row][col]];
                if (!colour) continue;
                ctx.fillStyle = colour;
                ctx.fillRect(col * PX, row * PX, PX, PX);
            }
        }

        cache[key] = canvas;
        return canvas;
    }

    // =========================================================================
    // MUSHROOM  (16 × 16, rendered at 2× → 32 × 32)
    // =========================================================================
    function drawMushroom(type = 'super') {
        const key = `mushroom_${type}`;
        if (cache[key]) return cache[key];

        const PX = 2, W = 16, H = 16;
        const { canvas, ctx } = makeCanvas(W * PX, H * PX);

        // R=red cap, W=white spot, S=stem/skin, .=transparent
        const grid = [
            '....RRRRRR......',
            '...RRRRRRRR.....',
            '..RRRRRRRRRR....',
            '.RRRWWRRRWWR....',
            '.RRRWWRRRWWR....',
            '.RRRRRRRRRRRR...',
            '..RRRRRRRRRR....',
            '...SSSSSSSS.....',
            '..SSSSSSSSSS....',
            '..SSSSSSSSSS....',
            '..SSSSSSSSSS....',
            '...SSSSSSSS.....',
            '................',
            '................',
            '................',
            '................',
        ];

        const capColour = type === 'super' ? C.muRed : C.ffOrange;

        for (let row = 0; row < grid.length; row++) {
            for (let col = 0; col < grid[row].length; col++) {
                const ch = grid[row][col];
                if (ch === 'R') { ctx.fillStyle = capColour; ctx.fillRect(col * PX, row * PX, PX, PX); }
                else if (ch === 'W') { ctx.fillStyle = C.muWhite; ctx.fillRect(col * PX, row * PX, PX, PX); }
                else if (ch === 'S') { ctx.fillStyle = C.muStem; ctx.fillRect(col * PX, row * PX, PX, PX); }
            }
        }

        cache[key] = canvas;
        return canvas;
    }

    // =========================================================================
    // FIRE FLOWER  (16 × 16, rendered at 2× → 32 × 32)
    // =========================================================================
    function drawFireFlower() {
        const key = 'fire_flower';
        if (cache[key]) return cache[key];

        const PX = 2, W = 16, H = 16;
        const { canvas, ctx } = makeCanvas(W * PX, H * PX);

        // O=orange petal, Y=yellow centre, G=green stem, R=red petal
        const grid = [
            '....OOOO........',
            '...OYYYYO.......',
            '..OYYYYYYOO.....',
            '..OYYYYYYOO.....',
            '...OYYYYO.......',
            '....OOOO........',
            '.....GG.........',
            '.....GG.........',
            '....GGGG........',
            '...GG..GG.......',
            '..GG....GG......',
            '................',
            '................',
            '................',
            '................',
            '................',
        ];

        const colourMap = { 'O': C.ffOrange, 'Y': C.ffYellow, 'G': C.ffGreen, 'R': C.ffRed, '.': null };

        for (let row = 0; row < grid.length; row++) {
            for (let col = 0; col < grid[row].length; col++) {
                const colour = colourMap[grid[row][col]];
                if (!colour) continue;
                ctx.fillStyle = colour;
                ctx.fillRect(col * PX, row * PX, PX, PX);
            }
        }

        cache[key] = canvas;
        return canvas;
    }

    // =========================================================================
    // STAR  (16 × 16, rendered at 2× → 32 × 32)
    // =========================================================================
    function drawStar() {
        const key = 'star';
        if (cache[key]) return cache[key];

        const PX = 2, W = 16, H = 16;
        const { canvas, ctx } = makeCanvas(W * PX, H * PX);

        // Y=yellow, O=orange outline, E=eye
        const grid = [
            '....YYYY........',
            '...YYYYYY.......',
            '..YYYYYYYY......',
            'OYYYYYYYYYYY....',
            'OYYYYYYYYYYY....',
            '.YYYYEEYYYY.....',
            '..YYYYYYYY......',
            '...YYYYYY.......',
            '....YYYY........',
            '...YYYYYY.......',
            '..YYYYYYYY......',
            '................',
            '................',
            '................',
            '................',
            '................',
        ];

        const colourMap = { 'Y': C.stYellow, 'O': C.stOrange, 'E': C.stEye, '.': null };

        for (let row = 0; row < grid.length; row++) {
            for (let col = 0; col < grid[row].length; col++) {
                const colour = colourMap[grid[row][col]];
                if (!colour) continue;
                ctx.fillStyle = colour;
                ctx.fillRect(col * PX, row * PX, PX, PX);
            }
        }

        cache[key] = canvas;
        return canvas;
    }

    // =========================================================================
    // COIN  (16 × 16, rendered at 2× → 32 × 32)
    // =========================================================================
    function drawCoin(animated = false, frame = 0) {
        const key = `coin_${frame}`;
        if (cache[key]) return cache[key];

        const PX = 2, W = 16, H = 16;
        const { canvas, ctx } = makeCanvas(W * PX, H * PX);

        // Coin animation frames (width varies)
        const frames = [
            // Full circle
            [
                '....GGGGGG......',
                '...GGGGGGGG.....',
                '..GGDDDDDDGG....',
                '..GDDDDDDDGG....',
                '..GDDDDDDDGG....',
                '..GDDDDDDDGG....',
                '..GDDDDDDDGG....',
                '..GDDDDDDDGG....',
                '..GDDDDDDDGG....',
                '..GDDDDDDDGG....',
                '..GGDDDDDDGG....',
                '...GGGGGGGG.....',
                '....GGGGGG......',
                '................',
                '................',
                '................',
            ],
            // Slightly narrower
            [
                '.....GGGG.......',
                '....GGGGGG......',
                '....GDDDGG......',
                '....GDDDGG......',
                '....GDDDGG......',
                '....GDDDGG......',
                '....GDDDGG......',
                '....GDDDGG......',
                '....GDDDGG......',
                '....GDDDGG......',
                '....GDDDGG......',
                '....GGGGGG......',
                '.....GGGG.......',
                '................',
                '................',
                '................',
            ],
            // Thin line
            [
                '......GG........',
                '......GG........',
                '......GG........',
                '......GG........',
                '......GG........',
                '......GG........',
                '......GG........',
                '......GG........',
                '......GG........',
                '......GG........',
                '......GG........',
                '......GG........',
                '......GG........',
                '................',
                '................',
                '................',
            ],
        ];

        const grid = frames[frame % frames.length];
        const colourMap = { 'G': C.coGold, 'D': C.coDark, '.': null };

        for (let row = 0; row < grid.length; row++) {
            for (let col = 0; col < grid[row].length; col++) {
                const colour = colourMap[grid[row][col]];
                if (!colour) continue;
                ctx.fillStyle = colour;
                ctx.fillRect(col * PX, row * PX, PX, PX);
            }
        }

        cache[key] = canvas;
        return canvas;
    }

    // =========================================================================
    // BRICK TILE  (16 × 16, rendered at 2× → 32 × 32)
    // =========================================================================
    function drawBrick() {
        const key = 'brick';
        if (cache[key]) return cache[key];

        const PX = 2, W = 16, H = 16;
        const { canvas, ctx } = makeCanvas(W * PX, H * PX);

        // Fill background
        ctx.fillStyle = C.brRed;
        ctx.fillRect(0, 0, W * PX, H * PX);

        // Mortar lines (horizontal)
        ctx.fillStyle = C.brMortar;
        [0, 7, 8, 15].forEach(y => {
            ctx.fillRect(0, y * PX, W * PX, PX);
        });

        // Mortar lines (vertical – offset per row)
        ctx.fillStyle = C.brMortar;
        // Top half: vertical at col 8
        ctx.fillRect(8 * PX, 1 * PX, PX, 6 * PX);
        // Bottom half: vertical at col 4 and col 12
        ctx.fillRect(4 * PX, 9 * PX, PX, 6 * PX);
        ctx.fillRect(12 * PX, 9 * PX, PX, 6 * PX);

        // Highlight top-left of each brick
        ctx.fillStyle = C.brLight;
        ctx.fillRect(1 * PX, 1 * PX, 6 * PX, PX);
        ctx.fillRect(1 * PX, 1 * PX, PX, 5 * PX);
        ctx.fillRect(9 * PX, 1 * PX, 6 * PX, PX);
        ctx.fillRect(9 * PX, 1 * PX, PX, 5 * PX);

        cache[key] = canvas;
        return canvas;
    }

    // =========================================================================
    // QUESTION BLOCK  (16 × 16, rendered at 2× → 32 × 32)
    // =========================================================================
    function drawQuestionBlock(active = true) {
        const key = `qblock_${active}`;
        if (cache[key]) return cache[key];

        const PX = 2, W = 16, H = 16;
        const { canvas, ctx } = makeCanvas(W * PX, H * PX);

        if (active) {
            // Yellow background
            ctx.fillStyle = C.qbYellow;
            ctx.fillRect(0, 0, W * PX, H * PX);

            // Border
            ctx.fillStyle = C.qbOrange;
            ctx.fillRect(0, 0, W * PX, PX);
            ctx.fillRect(0, (H - 1) * PX, W * PX, PX);
            ctx.fillRect(0, 0, PX, H * PX);
            ctx.fillRect((W - 1) * PX, 0, PX, H * PX);

            // Inner border
            ctx.fillStyle = C.qbDark;
            ctx.fillRect(PX, PX, (W - 2) * PX, PX);
            ctx.fillRect(PX, (H - 2) * PX, (W - 2) * PX, PX);
            ctx.fillRect(PX, PX, PX, (H - 2) * PX);
            ctx.fillRect((W - 2) * PX, PX, PX, (H - 2) * PX);

            // "?" symbol
            ctx.fillStyle = C.qbWhite;
            // Top of ?
            ctx.fillRect(6 * PX, 3 * PX, 4 * PX, PX);
            ctx.fillRect(5 * PX, 4 * PX, PX, PX);
            ctx.fillRect(10 * PX, 4 * PX, PX, PX);
            ctx.fillRect(9 * PX, 5 * PX, PX, PX);
            ctx.fillRect(8 * PX, 6 * PX, PX, PX);
            ctx.fillRect(7 * PX, 7 * PX, PX, PX);
            ctx.fillRect(7 * PX, 8 * PX, PX, PX);
            // Dot
            ctx.fillRect(7 * PX, 10 * PX, 2 * PX, 2 * PX);
        } else {
            // Used block – dark brown
            ctx.fillStyle = C.qbBrown;
            ctx.fillRect(0, 0, W * PX, H * PX);

            ctx.fillStyle = C.gDark;
            ctx.fillRect(0, 0, W * PX, PX);
            ctx.fillRect(0, (H - 1) * PX, W * PX, PX);
            ctx.fillRect(0, 0, PX, H * PX);
            ctx.fillRect((W - 1) * PX, 0, PX, H * PX);
        }

        cache[key] = canvas;
        return canvas;
    }

    // =========================================================================
    // PIPE  (32 × 32 top section + body, rendered at 2× → 64 × 64)
    // =========================================================================
    function drawPipe(section = 'top') {
        const key = `pipe_${section}`;
        if (cache[key]) return cache[key];

        const PX = 2, W = 32, H = 16;
        const { canvas, ctx } = makeCanvas(W * PX, H * PX);

        if (section === 'top') {
            // Pipe head (wider)
            ctx.fillStyle = C.piGreen;
            ctx.fillRect(0, 0, W * PX, H * PX);

            // Light stripe
            ctx.fillStyle = C.piLight;
            ctx.fillRect(2 * PX, 0, 4 * PX, H * PX);

            // Dark edge
            ctx.fillStyle = C.piDark;
            ctx.fillRect(0, 0, 2 * PX, H * PX);
            ctx.fillRect((W - 2) * PX, 0, 2 * PX, H * PX);

            // Top highlight
            ctx.fillStyle = C.piLight;
            ctx.fillRect(0, 0, W * PX, PX);
        } else {
            // Pipe body (narrower – centred)
            const bodyW = 24, bodyX = 4;
            ctx.fillStyle = C.piGreen;
            ctx.fillRect(bodyX * PX, 0, bodyW * PX, H * PX);

            ctx.fillStyle = C.piLight;
            ctx.fillRect((bodyX + 2) * PX, 0, 4 * PX, H * PX);

            ctx.fillStyle = C.piDark;
            ctx.fillRect(bodyX * PX, 0, 2 * PX, H * PX);
            ctx.fillRect((bodyX + bodyW - 2) * PX, 0, 2 * PX, H * PX);
        }

        cache[key] = canvas;
        return canvas;
    }

    // =========================================================================
    // GROUND TILE  (16 × 16, rendered at 2× → 32 × 32)
    // =========================================================================
    function drawGround() {
        const key = 'ground';
        if (cache[key]) return cache[key];

        const PX = 2, W = 16, H = 16;
        const { canvas, ctx } = makeCanvas(W * PX, H * PX);

        ctx.fillStyle = C.grBrown;
        ctx.fillRect(0, 0, W * PX, H * PX);

        // Top highlight row
        ctx.fillStyle = C.grLight;
        ctx.fillRect(0, 0, W * PX, PX);

        // Dot pattern
        ctx.fillStyle = C.grDark;
        for (let row = 2; row < H; row += 4) {
            for (let col = 2; col < W; col += 4) {
                ctx.fillRect(col * PX, row * PX, PX, PX);
            }
        }

        // Tan specks
        ctx.fillStyle = C.grTan;
        for (let row = 3; row < H; row += 4) {
            for (let col = 0; col < W; col += 4) {
                ctx.fillRect(col * PX, row * PX, PX, PX);
            }
        }

        cache[key] = canvas;
        return canvas;
    }

    // =========================================================================
    // SKY BACKGROUND  (fills any rectangle)
    // =========================================================================
    function drawSky(ctx, x, y, w, h) {
        ctx.fillStyle = C.skyBlue;
        ctx.fillRect(x, y, w, h);
    }

    // =========================================================================
    // CLOUD  (48 × 32, rendered at 2× → 96 × 64)
    // =========================================================================
    function drawCloud(size = 'large') {
        const key = `cloud_${size}`;
        if (cache[key]) return cache[key];

        const PX = 2;
        const W = size === 'large' ? 48 : 32;
        const H = size === 'large' ? 32 : 24;
        const { canvas, ctx } = makeCanvas(W * PX, H * PX);

        ctx.fillStyle = C.clWhite;

        if (size === 'large') {
            // Bottom rectangle
            ctx.fillRect(4 * PX, 16 * PX, 40 * PX, 16 * PX);
            // Middle bumps
            ctx.fillRect(8 * PX, 8 * PX, 12 * PX, 12 * PX);
            ctx.fillRect(18 * PX, 4 * PX, 16 * PX, 16 * PX);
            ctx.fillRect(32 * PX, 10 * PX, 10 * PX, 10 * PX);
            // Rounded corners (remove)
            ctx.clearRect(0, 0, 4 * PX, H * PX);
            ctx.clearRect((W - 4) * PX, 0, 4 * PX, H * PX);
        } else {
            ctx.fillRect(2 * PX, 12 * PX, 28 * PX, 12 * PX);
            ctx.fillRect(6 * PX, 6 * PX, 10 * PX, 10 * PX);
            ctx.fillRect(14 * PX, 2 * PX, 12 * PX, 14 * PX);
            ctx.fillRect(24 * PX, 8 * PX, 6 * PX, 8 * PX);
        }

        // Outline
        ctx.strokeStyle = C.clGray;
        ctx.lineWidth = PX;
        ctx.strokeRect(0, 0, W * PX, H * PX);

        cache[key] = canvas;
        return canvas;
    }

    // =========================================================================
    // FLAG POLE  (8 × 256, rendered at 2× → 16 × 512)
    // =========================================================================
    function drawFlagPole(height = 256) {
        const key = `flagpole_${height}`;
        if (cache[key]) return cache[key];

        const PX = 2, W = 8;
        const { canvas, ctx } = makeCanvas(W * PX, height * PX);

        // Pole
        ctx.fillStyle = C.flPole;
        ctx.fillRect(3 * PX, 0, 2 * PX, height * PX);

        // Ball on top
        ctx.fillStyle = C.coGold;
        ctx.beginPath();
        ctx.arc(4 * PX, 4 * PX, 4 * PX, 0, Math.PI * 2);
        ctx.fill();

        // Flag
        ctx.fillStyle = C.flGreen;
        ctx.fillRect(5 * PX, 4 * PX, 12 * PX, 8 * PX);

        cache[key] = canvas;
        return canvas;
    }

    // =========================================================================
    // FIREBALL  (8 × 8, rendered at 2× → 16 × 16)
    // =========================================================================
    function drawFireball(frame = 0) {
        const key = `fireball_${frame}`;
        if (cache[key]) return cache[key];

        const PX = 2, W = 8, H = 8;
        const { canvas, ctx } = makeCanvas(W * PX, H * PX);

        const frames = [
            [
                '..OOOO..',
                '.OYYYYY.',
                'OYYYYYYY',
                'OYYYYYYY',
                'OYYYYYYY',
                '.OYYYYY.',
                '..OOOO..',
                '........',
            ],
            [
                '..WWWW..',
                '.WOOOO..',
                'WOOOOOOO',
                'WOOOOOOO',
                'WOOOOOOO',
                '.WOOOO..',
                '..WWWW..',
                '........',
            ],
        ];

        const grid = frames[frame % 2];
        const colourMap = { 'O': C.fbOrange, 'Y': C.fbYellow, 'W': C.fbWhite, '.': null };

        for (let row = 0; row < grid.length; row++) {
            for (let col = 0; col < grid[row].length; col++) {
                const colour = colourMap[grid[row][col]];
                if (!colour) continue;
                ctx.fillStyle = colour;
                ctx.fillRect(col * PX, row * PX, PX, PX);
            }
        }

        cache[key] = canvas;
        return canvas;
    }

    // =========================================================================
    // SCORE POPUP  (text rendered onto a small canvas)
    // =========================================================================
    function drawScorePopup(value) {
        const key = `score_${value}`;
        if (cache[key]) return cache[key];

        const { canvas, ctx } = makeCanvas(40, 16);
        ctx.fillStyle = '#FFFFFF';
        ctx.font = 'bold 12px monospace';
        ctx.textAlign = 'center';
        ctx.fillText(String(value), 20, 12);

        cache[key] = canvas;
        return canvas;
    }

    // =========================================================================
    // HUD ELEMENTS
    // =========================================================================
    function drawHUDIcon(type) {
        const key = `hud_${type}`;
        if (cache[key]) return cache[key];

        const PX = 1, W = 8, H = 8;
        const { canvas, ctx } = makeCanvas(W * PX, H * PX);

        if (type === 'coin') {
            ctx.fillStyle = C.coGold;
            ctx.beginPath();
            ctx.arc(4, 4, 3, 0, Math.PI * 2);
            ctx.fill();
        } else if (type === 'life') {
            // Mini Mario head
            ctx.fillStyle = C.mHat;
            ctx.fillRect(2, 0, 4, 3);
            ctx.fillStyle = C.mSkin;
            ctx.fillRect(1, 3, 6, 4);
            ctx.fillStyle = C.mOver;
            ctx.fillRect(1, 5, 6, 3);
        }

        cache[key] = canvas;
        return canvas;
    }

    // =========================================================================
    // DEATH ANIMATION FRAME
    // =========================================================================
    function drawMarioDead() {
        const key = 'mario_dead';
        if (cache[key]) return cache[key];

        const PX = 2, W = 16, H = 16;
        const { canvas, ctx } = makeCanvas(W * PX, H * PX);

        // Upside-down Mario silhouette
        const grid = [
            '................',
            '..TTSS..SSTTT...',
            '..TTSS..SSTTT...',
            '..SSSSSSSS......',
            '..SSSSSSSS......',
            'BBBSSSSSSBB.....',
            'BBBBSSSSBBBB....',
            '.BBRSSRSSBB.....',
            '..SSSSSSSS......',
            '...SSRRSSS......',
            '..SSSSSSSS......',
            '..TSTSTSTS......',
            '...TTTSSST......',
            '...RRRRRRRR.....',
            '....RRRR........',
            '................',
        ];

        const colourMap = { 'R': C.mHat, 'S': C.mSkin, 'B': C.mOver, 'T': C.mBoot, '.': null };

        for (let row = 0; row < grid.length; row++) {
            for (let col = 0; col < grid[row].length; col++) {
                const colour = colourMap[grid[row][col]];
                if (!colour) continue;
                ctx.fillStyle = colour;
                ctx.fillRect(col * PX, row * PX, PX, PX);
            }
        }

        cache[key] = canvas;
        return canvas;
    }

    // =========================================================================
    // BLOCK FRAGMENT  (for brick break animation)
    // =========================================================================
    function drawBrickFragment() {
        const key = 'brick_fragment';
        if (cache[key]) return cache[key];

        const PX = 2, W = 8, H = 8;
        const { canvas, ctx } = makeCanvas(W * PX, H * PX);

        ctx.fillStyle = C.brRed;
        ctx.fillRect(0, 0, W * PX, H * PX);
        ctx.fillStyle = C.brLight;
        ctx.fillRect(0, 0, W * PX, PX);
        ctx.fillRect(0, 0, PX, H * PX);
        ctx.fillStyle = C.brDark;
        ctx.fillRect(0, (H - 1) * PX, W * PX, PX);
        ctx.fillRect((W - 1) * PX, 0, PX, H * PX);

        cache[key] = canvas;
        return canvas;
    }

    // =========================================================================
    // CASTLE  (simple decorative end-level castle)
    // =========================================================================
    function drawCastle() {
        const key = 'castle';
        if (cache[key]) return cache[key];

        const PX = 2, W = 80, H = 80;
        const { canvas, ctx } = makeCanvas(W * PX, H * PX);

        const grey  = '#AAAAAA';
        const dark  = '#666666';
        const black = '#000000';
        const door  = '#000000';

        // Main body
        ctx.fillStyle = grey;
        ctx.fillRect(10 * PX, 30 * PX, 60 * PX, 50 * PX);

        // Battlements
        const battX = [10, 20, 30, 50, 60, 70];
        battX.forEach(x => {
            ctx.fillStyle = grey;
            ctx.fillRect(x * PX, 20 * PX, 10 * PX, 12 * PX);
        });

        // Tower left
        ctx.fillStyle = grey;
        ctx.fillRect(0, 20 * PX, 20 * PX, 60 * PX);
        ctx.fillRect(0, 10 * PX, 8 * PX, 12 * PX);
        ctx.fillRect(12 * PX, 10 * PX, 8 * PX, 12 * PX);

        // Tower right
        ctx.fillRect(60 * PX, 20 * PX, 20 * PX, 60 * PX);
        ctx.fillRect(60 * PX, 10 * PX, 8 * PX, 12 * PX);
        ctx.fillRect(72 * PX, 10 * PX, 8 * PX, 12 * PX);

        // Windows
        ctx.fillStyle = black;
        ctx.fillRect(4 * PX, 30 * PX, 8 * PX, 10 * PX);
        ctx.fillRect(68 * PX, 30 * PX, 8 * PX, 10 * PX);
        ctx.fillRect(28 * PX, 40 * PX, 10 * PX, 12 * PX);
        ctx.fillRect(42 * PX, 40 * PX, 10 * PX, 12 * PX);

        // Door
        ctx.fillStyle = door;
        ctx.fillRect(32 * PX, 58 * PX, 16 * PX, 22 * PX);
        // Arch top
        ctx.beginPath();
        ctx.arc(40 * PX, 58 * PX, 8 * PX, Math.PI, 0);
        ctx.fill();

        // Dark outline
        ctx.strokeStyle = dark;
        ctx.lineWidth = PX;
        ctx.strokeRect(0, 20 * PX, 20 * PX, 60 * PX);
        ctx.strokeRect(60 * PX, 20 * PX, 20 * PX, 60 * PX);
        ctx.strokeRect(10 * PX, 30 * PX, 60 * PX, 50 * PX);

        cache[key] = canvas;
        return canvas;
    }

    // =========================================================================
    // CLEAR CACHE  (call when restarting to free memory)
    // =========================================================================
    function clearCache() {
        Object.keys(cache).forEach(k => delete cache[k]);
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================
    return {
        // Characters
        getMarioSmall:    (state)  => drawMarioSmall(state),
        getMarioBig:      (state)  => drawMarioBig(state),
        getMarioDead:     ()       => drawMarioDead(),
        getGoomba:        (state)  => drawGoomba(state),
        getKoopa:         (state)  => drawKoopa(state),

        // Power-ups
        getMushroom:      (type)   => drawMushroom(type),
        getFireFlower:    ()       => drawFireFlower(),
        getStar:          ()       => drawStar(),

        // Projectiles
        getFireball:      (frame)  => drawFireball(frame),

        // Collectibles
        getCoin:          (frame)  => drawCoin(false, frame),

        // Tiles
        getBrick:         ()       => drawBrick(),
        getQuestionBlock: (active) => drawQuestionBlock(active),
        getPipe:          (sec)    => drawPipe(sec),
        getGround:        ()       => drawGround(),

        // Scenery
        drawSky,
        getCloud:         (size)   => drawCloud(size),
        getFlagPole:      (h)      => drawFlagPole(h),
        getCastle:        ()       => drawCastle(),

        // Effects
        getScorePopup:    (val)    => drawScorePopup(val),
        getBrickFragment: ()       => drawBrickFragment(),
        getHUDIcon:       (type)   => drawHUDIcon(type),

        // Utility
        clearCache,
        colours: C,
    };
})();