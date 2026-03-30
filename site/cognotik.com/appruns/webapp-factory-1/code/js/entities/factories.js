/**
 * factories.js - EnemyFactory and ItemFactory
 *
 * Centralised creation of enemy and item instances so that game.js
 * does not need to import every concrete class directly.
 */

'use strict';

// ─── Enemy Factory ───────────────────────────────────────────────────────────

class EnemyFactory {
    /**
     * Create an enemy of the given type.
     * @param {string} type   ENTITY_TYPE constant
     * @param {number} x      pixel x
     * @param {number} y      pixel y
     * @param {Game}   game   game reference
     * @returns {Enemy|null}
     */
    static create(type, x, y, game) {
        switch (type) {
            case ENTITY_TYPE.GOOMBA:  return new Goomba(x, y, game);
            case ENTITY_TYPE.KOOPA:   return new Koopa(x, y, game);
            default:
                console.warn(`EnemyFactory: unknown type "${type}"`);
                return null;
        }
    }
}

// ─── Item Factory ────────────────────────────────────────────────────────────

class ItemFactory {
    /**
     * Create a collectible item.
     * @param {string} type   ITEM_TYPE constant
     * @param {number} x      pixel x (block origin)
     * @param {number} y      pixel y (block origin)
     * @param {Game}   game   game reference
     * @returns {Item|null}
     */
    static create(type, x, y, game) {
        switch (type) {
            case ITEM_TYPE.COIN:        return new CoinItem(x, y, game);
            case ITEM_TYPE.MUSHROOM:    return new Mushroom(x, y, game);
            case ITEM_TYPE.FIRE_FLOWER: return new FireFlower(x, y, game);
            case ITEM_TYPE.STAR:        return new Star(x, y, game);
            case ITEM_TYPE.ONE_UP:      return new OneUp(x, y, game);
            default:
                console.warn(`ItemFactory: unknown type "${type}"`);
                return null;
        }
    }
}