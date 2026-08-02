// Stable, monotonic id generation for AST nodes.
// Ids are strings so they can be used as DOM keys directly.

let counter = 0;

export function nextId(prefix = 'n') {
  counter += 1;
  return `${prefix}${counter}`;
}

// Reset is exposed primarily for deterministic tests.
export function resetIds() {
  counter = 0;
}
