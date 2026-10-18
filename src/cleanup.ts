/** Screen cleanup registry — timers/pollers are stopped when leaving a screen. */

let current: (() => void) | null = null;

export function setCleanup(fn: (() => void) | null) {
  if (current) {
    try {
      current();
    } catch {
      /* ignore */
    }
  }
  current = fn;
}

export function runCleanup() {
  setCleanup(null);
}
