/**
 * physics.js
 * 
 * Physics system for Super Mario Bros Clone.
 * Handles gravity application, velocity integration, AABB collision detection
 * and resolution between entities and tiles.
 */

import { PHYSICS, TILE_SIZE } from './constants.js';

/**
 * Apply gravity to an entity's vertical velocity.
 * Caps fall speed at terminal velocity.
 * 
 * @param {Object} entity - Entity with vy and onGround properties
 * @param {number} dt - Delta time in seconds
 */
export function applyGravity(entity, dt) {
    if (entity.onGround) return;

    entity.vy += PHYSICS.GRAVITY * dt;

    // Clamp to terminal velocity
    if (entity.vy > PHYSICS.TERMINAL_VELOCITY) {
        entity.vy = PHYSICS.TERMINAL_VELOCITY;
    }
}

/**
 * Integrate velocity into position.
 * 
 * @param {Object} entity - Entity with x, y, vx, vy properties
 * @param {number} dt - Delta time in seconds
 */
export function integrateVelocity(entity, dt) {
    entity.x += entity.vx * dt;
    entity.y += entity.vy * dt;
}

/**
 * Get the axis-aligned bounding box for an entity.
 * 
 * @param {Object} entity - Entity with x, y, width, height properties
 * @returns {{ left, right, top, bottom }} AABB bounds
 */
export function getAABB(entity) {
    return {
        left:   entity.x,
        right:  entity.x + entity.width,
        top:    entity.y,
        bottom: entity.y + entity.height,
    };
}

/**
 * Test whether two AABBs overlap.
 * 
 * @param {Object} a - AABB { left, right, top, bottom }
 * @param {Object} b - AABB { left, right, top, bottom }
 * @returns {boolean}
 */
export function aabbOverlap(a, b) {
    return (
        a.left   < b.right  &&
        a.right  > b.left   &&
        a.top    < b.bottom &&
        a.bottom > b.top
    );
}

/**
 * Compute the minimum translation vector (MTV) to separate two overlapping AABBs.
 * Returns null if they do not overlap.
 * 
 * @param {Object} a - AABB of the moving entity
 * @param {Object} b - AABB of the static tile / entity
 * @returns {{ dx, dy, side } | null}
 *   dx/dy: displacement to apply to entity A
 *   side: 'top' | 'bottom' | 'left' | 'right' (face of B that was hit)
 */
export function getMTV(a, b) {
    if (!aabbOverlap(a, b)) return null;

    const overlapLeft   = a.right  - b.left;   // A penetrates from the left of B
    const overlapRight  = b.right  - a.left;   // A penetrates from the right of B
    const overlapTop    = a.bottom - b.top;     // A penetrates from the top of B
    const overlapBottom = b.bottom - a.top;     // A penetrates from the bottom of B

    // Find the axis of minimum penetration
    const minX = overlapLeft < overlapRight ? overlapLeft  : overlapRight;
    const minY = overlapTop  < overlapBottom ? overlapTop  : overlapBottom;

    if (minX < minY) {
        // Horizontal separation is smaller
        if (overlapLeft < overlapRight) {
            return { dx: -overlapLeft,  dy: 0, side: 'left' };
        } else {
            return { dx:  overlapRight, dy: 0, side: 'right' };
        }
    } else {
        // Vertical separation is smaller (or equal — prefer vertical)
        if (overlapTop < overlapBottom) {
            return { dx: 0, dy: -overlapTop,    side: 'top' };
        } else {
            return { dx: 0, dy:  overlapBottom, side: 'bottom' };
        }
    }
}

/**
 * Resolve collision between a dynamic entity and a static tile.
 * Mutates entity position and velocity, and sets onGround / hitCeiling flags.
 * 
 * @param {Object} entity - Dynamic entity (player, enemy, etc.)
 * @param {Object} tile   - Static tile { x, y, width, height, solid }
 * @returns {{ collided: boolean, side: string | null }}
 */
export function resolveCollision(entity, tile) {
    if (!tile.solid) return { collided: false, side: null };

    const entityAABB = getAABB(entity);
    const tileAABB   = getAABB(tile);
    const mtv        = getMTV(entityAABB, tileAABB);

    if (!mtv) return { collided: false, side: null };

    // Apply positional correction
    entity.x += mtv.dx;
    entity.y += mtv.dy;

    // Zero out velocity component along the collision axis
    switch (mtv.side) {
        case 'top':
            // Entity landed on top of tile
            if (entity.vy > 0) entity.vy = 0;
            entity.onGround  = true;
            break;

        case 'bottom':
            // Entity hit the underside of tile (ceiling)
            if (entity.vy < 0) entity.vy = 0;
            entity.hitCeiling = true;
            break;

        case 'left':
        case 'right':
            entity.vx = 0;
            break;
    }

    return { collided: true, side: mtv.side };
}

/**
 * Resolve all collisions between a dynamic entity and a list of tiles.
 * Performs two passes (horizontal then vertical) for stable corner resolution.
 * 
 * @param {Object}   entity    - Dynamic entity
 * @param {Object[]} tiles     - Array of tile objects
 * @param {number}   dt        - Delta time (used for sub-step integration)
 * @returns {string[]} Array of sides that were hit this frame
 */
export function resolveEntityTileCollisions(entity, tiles, dt) {
    // Reset per-frame flags before resolution
    entity.onGround   = false;
    entity.hitCeiling = false;

    const hitSides = [];

    // --- Horizontal pass ---
    entity.x += entity.vx * dt;

    for (const tile of tiles) {
        if (!tile.solid) continue;

        const entityAABB = getAABB(entity);
        const tileAABB   = getAABB(tile);
        const mtv        = getMTV(entityAABB, tileAABB);

        if (mtv && (mtv.side === 'left' || mtv.side === 'right')) {
            entity.x += mtv.dx;
            entity.vx  = 0;
            hitSides.push(mtv.side);
        }
    }

    // --- Vertical pass ---
    entity.y += entity.vy * dt;

    for (const tile of tiles) {
        if (!tile.solid) continue;

        const entityAABB = getAABB(entity);
        const tileAABB   = getAABB(tile);
        const mtv        = getMTV(entityAABB, tileAABB);

        if (mtv && (mtv.side === 'top' || mtv.side === 'bottom')) {
            entity.y += mtv.dy;

            if (mtv.side === 'top') {
                if (entity.vy > 0) entity.vy = 0;
                entity.onGround = true;
            } else if (mtv.side === 'bottom') {
                if (entity.vy < 0) entity.vy = 0;
                entity.hitCeiling = true;
            }

            hitSides.push(mtv.side);
        }
    }

    return hitSides;
}

/**
 * Check collision between two dynamic entities (e.g. player vs enemy).
 * Does NOT resolve — returns collision info for game logic to handle.
 * 
 * @param {Object} entityA
 * @param {Object} entityB
 * @returns {{ collided: boolean, side: string | null, mtv: Object | null }}
 */
export function checkEntityCollision(entityA, entityB) {
    const aabbA = getAABB(entityA);
    const aabbB = getAABB(entityB);
    const mtv   = getMTV(aabbA, aabbB);

    if (!mtv) return { collided: false, side: null, mtv: null };

    return { collided: true, side: mtv.side, mtv };
}

/**
 * Broad-phase filter: return only tiles within a certain pixel radius of entity.
 * Avoids testing every tile in the level each frame.
 * 
 * @param {Object}   entity  - Entity with x, y, width, height
 * @param {Object[]} allTiles
 * @param {number}   [margin=TILE_SIZE * 2] - Extra pixels around entity AABB
 * @returns {Object[]} Nearby tiles
 */
export function getNearbyTiles(entity, allTiles, margin = TILE_SIZE * 2) {
    const left   = entity.x - margin;
    const right  = entity.x + entity.width  + margin;
    const top    = entity.y - margin;
    const bottom = entity.y + entity.height + margin;

    return allTiles.filter(tile =>
        tile.x + tile.width  > left  &&
        tile.x               < right &&
        tile.y + tile.height > top   &&
        tile.y               < bottom
    );
}

/**
 * Full physics step for a single entity:
 *   1. Apply gravity
 *   2. Broad-phase tile filter
 *   3. Resolve entity-tile collisions (split-axis)
 * 
 * NOTE: velocity integration is handled inside resolveEntityTileCollisions
 *       so do NOT call integrateVelocity separately when using this function.
 * 
 * @param {Object}   entity
 * @param {Object[]} allTiles
 * @param {number}   dt
 * @returns {string[]} Hit sides
 */
export function physicsStep(entity, allTiles, dt) {
    applyGravity(entity, dt);

    const nearby  = getNearbyTiles(entity, allTiles);
    const hitSides = resolveEntityTileCollisions(entity, nearby, dt);

    return hitSides;
}