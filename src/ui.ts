/** Tiny DOM helpers and shared UI bits. */

export function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  props?: {
    class?: string;
    html?: string;
    text?: string;
    style?: string;
    [attr: string]: unknown;
  } | null,
  ...children: (Node | string | null | undefined | false)[]
): HTMLElementTagNameMap[K] {
  const node = document.createElement(tag);
  if (props) {
    const { class: cls, html, text, ...rest } = props;
    if (cls) node.className = cls;
    if (html !== undefined) node.innerHTML = html as string;
    if (text !== undefined) node.textContent = text as string;
    Object.assign(node, rest);
  }
  for (const c of children) {
    if (c === null || c === undefined || c === false) continue;
    node.append(typeof c === 'string' ? document.createTextNode(c) : c);
  }
  return node;
}

export function clear(node: HTMLElement) {
  while (node.firstChild) node.removeChild(node.firstChild);
}

export const svg = (path: string, viewBox = '0 0 24 24') => {
  const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  s.setAttribute('viewBox', viewBox);
  s.setAttribute('aria-hidden', 'true');
  const p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  p.setAttribute('d', path);
  s.append(p);
  return s;
};

export const ICONS = {
  walk: 'M13.5 5.5a2 2 0 1 0 0-4 2 2 0 0 0 0 4Zm-3.6 2.9L7 22h2.1l1.8-8 2.1 2v6h2v-7.3l-2.1-2 .6-3A7.3 7.3 0 0 0 19 12v-2c-1.7 0-3.2-.9-4-2.2l-1-1.6c-.4-.6-1-.9-1.7-.9-.3 0-.6 0-.8.1L5 7.3V13h2V8.6l2.9-1.2Z',
  bus: 'M4 16c0 .9.4 1.6 1 2v2a1 1 0 0 0 1 1h1a1 1 0 0 0 1-1v-1h8v1a1 1 0 0 0 1 1h1a1 1 0 0 0 1-1v-2c.6-.4 1-1.1 1-2V6c0-3.5-3.6-4-8-4S4 2.5 4 6v10Zm2.5-.5a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3Zm11 0a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3ZM6 10V6h12v4H6Z',
  pin: 'M12 2a7 7 0 0 0-7 7c0 5 7 13 7 13s7-8 7-13a7 7 0 0 0-7-7Zm0 9.5A2.5 2.5 0 1 1 12 6.5a2.5 2.5 0 0 1 0 5Z',
  clock: 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm1 10.6 4 2.4-.9 1.6L15 15V6h-2Z',
  chevron: 'M9 6l6 6-6 6',
  close: 'M6.4 5 5 6.4 10.6 12 5 17.6 6.4 19 12 13.4 17.6 19 19 17.6 13.4 12 19 6.4 17.6 5 12 10.6Z',
  gear: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8Zm9.4 4c0 .6-.1 1.2-.2 1.7l2 1.6-2 3.4-2.4-1a8 8 0 0 1-3 1.7l-.4 2.6h-4l-.4-2.6a8 8 0 0 1-3-1.7l-2.4 1-2-3.4 2-1.6a9 9 0 0 1 0-3.4l-2-1.6 2-3.4 2.4 1a8 8 0 0 1 3-1.7L9.4 2h4l.4 2.6a8 8 0 0 1 3 1.7l2.4-1 2 3.4-2 1.6c.1.5.2 1.1.2 1.7Z',
  swap: 'M7 4 3 8l4 4V9h10V7H7V4Zm10 8v3H7v2h10v3l4-4-4-4Z',
  star: 'M12 2.5 15 9l7 .6-5.3 4.6L18.3 21 12 17.4 5.7 21l1.6-6.8L2 9.6 9 9 12 2.5Z',
  starFilled:
    'M12 2.5 15 9l7 .6-5.3 4.6L18.3 21 12 17.4 5.7 21l1.6-6.8L2 9.6 9 9 12 2.5Z',
  live: 'M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6Zm0-4a7 7 0 0 0-7 7h2a5 5 0 1 1 10 0h2a7 7 0 0 0-7-7Z',
  back: 'M15.4 5 14 6.4l5.6 5.6L14 17.6 15.4 19l7-7-7-7ZM4 11h10v2H4v-2Z',
  search: 'M10 2a8 8 0 1 0 4.9 14.3l5.4 5.4 1.4-1.4-5.4-5.4A8 8 0 0 0 10 2Zm0 2a6 6 0 1 1 0 12 6 6 0 0 1 0-12Z',
} as const;

/** Deterministic line colors (KM Szczecinek lines). */
const LINE_COLORS: Record<string, string> = {
  '0': '#7c3aed',
  '1': '#e63946',
  '1A': '#e76f51',
  '2': '#0077b6',
  '4': '#2a9d8f',
  '5': '#f4a261',
  '6': '#d62828',
  '7': '#2b9348',
  '9': '#8338ec',
  '14': '#0ea5e9',
};
const FALLBACK = ['#e63946', '#2a9d8f', '#0077b6', '#f4a261', '#7c3aed', '#2b9348', '#d62828', '#0ea5e9'];

export function lineColor(line: string): string {
  if (LINE_COLORS[line]) return LINE_COLORS[line];
  let h = 0;
  for (const c of line) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  return FALLBACK[h % FALLBACK.length];
}

export function lineBadge(line: string, big = false): HTMLElement {
  const color = lineColor(line);
  return el(
    'span',
    {
      class: `line-badge${big ? ' line-badge--big' : ''}`,
      style: `--line-color:${color}`,
      text: line,
    },
  );
}

/** Markdown-lite: only [text](url) links, everything else escaped as text. */
export function markdownLite(md: string): DocumentFragment {
  const frag = document.createDocumentFragment();
  const re = /\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g;
  let last = 0;
  for (const m of md.matchAll(re)) {
    if (m.index! > last) frag.append(md.slice(last, m.index));
    frag.append(el('a', { href: m[2], target: '_blank', rel: 'noopener', text: m[1] }));
    last = m.index! + m[0].length;
  }
  if (last < md.length) frag.append(md.slice(last));
  return frag;
}

export function toast(msg: string, ms = 3200) {
  let host = document.getElementById('toast');
  if (!host) {
    host = el('div', { id: 'toast' });
    document.body.append(host);
  }
  clear(host);
  host.append(el('div', { class: 'toast__item', text: msg }));
  host.classList.add('toast--visible');
  setTimeout(() => host!.classList.remove('toast--visible'), ms);
}
