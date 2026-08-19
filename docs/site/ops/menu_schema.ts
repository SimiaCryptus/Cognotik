/**
 * Schema for `docs/site/apps/menu.json`.
 *
 * `menu.json` is the data source that drives the Cognotik website.
 *
 * This file defines the TypeScript shape that `menu.json` must satisfy,
 * plus a lightweight runtime guard used to sanity-check generated output.
 */

/** A single entry in the app gallery menu. */
export interface AppMenuEntry {
  /**
   * URL-safe identifier, e.g. "comic-book-creator".
   */
  slug: string;

  /** Display name shown on the gallery card / nav item, e.g. "Comic Book Creator". */
  name: string;

  /**
   * One-sentence tagline / hook
   */
  shortDescription: string;

  /**
   * Path, relative to the menu json, e.g. "comic-book-creator.md".
   */
  file: string;

}

/** Top-level shape of `menu.json`. */
export interface AppMenu {
  /** All apps to show in the gallery, in the order they should render. */
  apps: AppMenuEntry[];
}

/**
 * Minimal runtime shape-check for `AppMenu`.
 *
 * This is not a full validator — it only guards against obviously
 * malformed generated output (missing required fields, wrong types)
 * before `menu.json` is committed.
 */
export function isValidAppMenu(value: unknown): value is AppMenu {
  if (typeof value !== "object" || value === null) return false;
  const menu = value as Record<string, unknown>;
  if (!Array.isArray(menu.apps)) return false;

  return menu.apps.every((entry) => {
    if (typeof entry !== "object" || entry === null) return false;
    const e = entry as Record<string, unknown>;
    return (
      typeof e.slug === "string" &&
      typeof e.name === "string" &&
      typeof e.shortDescription === "string" &&
      typeof e.file === "string"
    );
  });
}