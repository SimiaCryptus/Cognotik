/**
 * leveldata.js - Level definitions
 *
 * LevelData.get(world, level) returns a level descriptor object used by
 * the Level class to build the tile map and entity roster.
 *
 * Tile map rows are listed top-to-bottom; each value is a TILE constant.
 * Enemy entries: { type, x, y }  (tile coordinates)
 */

'use strict';

// Shorthand aliases for readability inside map arrays
const _ = TILE.EMPTY;
const G = TILE.GROUND;
const B = TILE.BRICK;
const Q = TILE.QUESTION;
const U = TILE.USED_BLOCK;
const PL = TILE.PIPE_TOP_L;
const PR = TILE.PIPE_TOP_R;
const BL = TILE.PIPE_BODY_L;
const BR = TILE.PIPE_BODY_R;
const F  = TILE.FLAG_POLE;
const C  = TILE.CASTLE;
const S  = TILE.SOLID;

class LevelData {
    /**
     * @param {number} world
     * @param {number} level
     * @returns {object|null}
     */
    static get(world, level) {
        const key = `${world}-${level}`;
        return LevelData._levels[key] || null;
    }
}

LevelData._levels = {

    // ════════════════════════════════════════════════════════════════════════
    //  World 1-1  (classic layout)
    // ════════════════════════════════════════════════════════════════════════
    '1-1': {
        timeLimit:   400,
        playerStart: { x: 2, y: 11 },

        // 15 rows × 64 columns
        // Row 0 = top of screen, Row 14 = bottom
        map: (() => {
            // Build a 15-row × 64-col map programmatically
            const W = 64, H = 15;
            const m = Array.from({ length: H }, () => new Array(W).fill(TILE.EMPTY));

            // Ground row (row 13) – full width with a gap
            for (let x = 0; x < W; x++) {
                if (x >= 22 && x <= 23) continue; // pit
                m[13][x] = G;
            }
            // Underground row (row 14)
            for (let x = 0; x < W; x++) m[14][x] = G;

            // ── Brick / Question block rows ──
            // Classic 1-1 layout (approximate)
            m[8][3]  = Q;   // first ? block (coin)
            m[8][5]  = B;
            m[8][6]  = Q;   // mushroom
            m[8][7]  = B;
            m[8][8]  = Q;   // coin
            m[8][9]  = B;

            m[4][6]  = Q;   // hidden high ? block (1-up)

            m[8][20] = B;
            m[8][21] = Q;
            m[8][22] = B;

            m[4][20] = B;
            m[4][21] = B;
            m[4][22] = B;
            m[4][23] = B;

            // ── Pipes ──
            m[11][28] = PL; m[11][29] = PR;
            m[12][28] = BL; m[12][29] = BR;

            m[10][38] = PL; m[10][39] = PR;
            m[11][38] = BL; m[11][39] = BR;
            m[12][38] = BL; m[12][39] = BR;

            m[9][46]  = PL; m[9][47]  = PR;
            m[10][46] = BL; m[10][47] = BR;
            m[11][46] = BL; m[11][47] = BR;
            m[12][46] = BL; m[12][47] = BR;

            // ── Staircase to flag ──
            for (let step = 0; step < 8; step++) {
                for (let row = 13 - step; row <= 13; row++) {
                    m[row][55 + step] = G;
                }
            }

            // ── Flag pole ──
            for (let row = 4; row <= 13; row++) m[row][63] = F;

            return m;
        })(),

        enemies: [
            { type: ENTITY_TYPE.GOOMBA, x: 16, y: 12 },
            { type: ENTITY_TYPE.GOOMBA, x: 18, y: 12 },
            { type: ENTITY_TYPE.KOOPA,  x: 25, y: 12 },
            { type: ENTITY_TYPE.GOOMBA, x: 32, y: 12 },
            { type: ENTITY_TYPE.GOOMBA, x: 33, y: 12 },
            { type: ENTITY_TYPE.KOOPA,  x: 42, y: 12 },
            { type: ENTITY_TYPE.GOOMBA, x: 50, y: 12 },
        ],

        // What each ? block contains: key = "col,row"
        blockItems: {
            '3,8':  ITEM_TYPE.COIN,
            '6,8':  ITEM_TYPE.MUSHROOM,
            '8,8':  ITEM_TYPE.COIN,
            '6,4':  ITEM_TYPE.ONE_UP,
            '21,8': ITEM_TYPE.COIN,
        },
    },

    // ════════════════════════════════════════════════════════════════════════
    //  World 1-2  (underground)
    // ════════════════════════════════════════════════════════════════════════
    '1-2': {
        timeLimit:   300,
        playerStart: { x: 2, y: 11 },

        map: (() => {
            const W = 64, H = 15;
            const m = Array.from({ length: H }, () => new Array(W).fill(TILE.EMPTY));

            // Ceiling
            for (let x = 0; x < W; x++) { m[0][x] = G; m[1][x] = G; }
            // Floor
            for (let x = 0; x < W; x++) { m[13][x] = G; m[14][x] = G; }

            // Platforms
            for (let x = 5; x <= 12; x++) m[8][x] = B;
            m[8][8] = Q;

            for (let x = 16; x <= 20; x++) m[6][x] = B;
            m[6][18] = Q;

            for (let x = 24; x <= 30; x++) m[10][x] = B;
            m[10][27] = Q;

            // Pipes (exit)
            m[11][58] = PL; m[11][59] = PR;
            m[12][58] = BL; m[12][59] = BR;

            return m;
        })(),

        enemies: [
            { type: ENTITY_TYPE.GOOMBA, x: 10, y: 12 },
            { type: ENTITY_TYPE.GOOMBA, x: 20, y: 12 },
            { type: ENTITY_TYPE.KOOPA,  x: 35, y: 12 },
            { type: ENTITY_TYPE.GOOMBA, x: 45, y: 12 },
            { type: ENTITY_TYPE.GOOMBA, x: 46, y: 12 },
        ],

        blockItems: {
            '8,8':  ITEM_TYPE.COIN,
            '18,6': ITEM_TYPE.FIRE_FLOWER,
            '27,10': ITEM_TYPE.STAR,
        },
    },

    // ════════════════════════════════════════════════════════════════════════
    //  World 2-1
    // ════════════════════════════════════════════════════════════════════════
    '2-1': {
        timeLimit:   400,
        playerStart: { x: 2, y: 11 },

        map: (() => {
            const W = 64, H = 15;
            const m = Array.from({ length: H }, () => new Array(W).fill(TILE.EMPTY));

            for (let x = 0; x < W; x++) {
                if (x >= 18 && x <= 19) continue;
                if (x >= 32 && x <= 33) continue;
                m[13][x] = G;
            }
            for (let x = 0; x < W; x++) m[14][x] = G;

            m[8][4]  = Q;
            m[8][7]  = B;
            m[8][8]  = Q;
            m[8][9]  = B;
            m[4][8]  = Q;

            m[8][22] = B;
            m[8][23] = Q;
            m[8][24] = B;

            m[11][28] = PL; m[11][29] = PR;
            m[12][28] = BL; m[12][29] = BR;

            m[10][40] = PL; m[10][41] = PR;
            m[11][40] = BL; m[11][41] = BR;
            m[12][40] = BL; m[12][41] = BR;

            for (let step = 0; step < 8; step++) {
                for (let row = 13 - step; row <= 13; row++) {
                    m[row][55 + step] = G;
                }
            }
            for (let row = 4; row <= 13; row++) m[row][63] = F;

            return m;
        })(),

        enemies: [
            { type: ENTITY_TYPE.GOOMBA, x: 12, y: 12 },
            { type: ENTITY_TYPE.GOOMBA, x: 14, y: 12 },
            { type: ENTITY_TYPE.KOOPA,  x: 22, y: 12 },
            { type: ENTITY_TYPE.GOOMBA, x: 28, y: 12 },
            { type: ENTITY_TYPE.KOOPA,  x: 36, y: 12 },
            { type: ENTITY_TYPE.GOOMBA, x: 44, y: 12 },
            { type: ENTITY_TYPE.GOOMBA, x: 45, y: 12 },
            { type: ENTITY_TYPE.KOOPA,  x: 50, y: 12 },
        ],

        blockItems: {
            '4,8':  ITEM_TYPE.MUSHROOM,
            '8,8':  ITEM_TYPE.COIN,
            '8,4':  ITEM_TYPE.COIN,
            '23,8': ITEM_TYPE.FIRE_FLOWER,
        },
    },
};