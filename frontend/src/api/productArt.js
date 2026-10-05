// Frontend-only product artwork mapper (UI layer only — no backend changes).
// Maps product/category keywords to original inline-SVG illustrations served as
// data URIs (zero network dependency, never broken, distinct per product type).

const TYPES = [
  { key: 'mouse', test: /mouse/i, bg: ['#1e3a8a', '#3b82f6'], glyph: 'mouse' },
  { key: 'keyboard', test: /keyboard/i, bg: ['#4c1d95', '#8b5cf6'], glyph: 'keyboard' },
  { key: 'monitor', test: /monitor|display/i, bg: ['#0f766e', '#14b8a6'], glyph: 'monitor' },
  { key: 'headphones', test: /headphone|headset/i, bg: ['#9a3412', '#f97316'], glyph: 'headphones' },
  { key: 'earbuds', test: /earbud|earphone/i, bg: ['#be123c', '#fb7185'], glyph: 'earbuds' },
  { key: 'speaker', test: /speaker|soundbar/i, bg: ['#713f12', '#eab308'], glyph: 'speaker' },
  { key: 'webcam', test: /webcam|camera/i, bg: ['#155e75', '#06b6d4'], glyph: 'webcam' },
  { key: 'charger', test: /charg|pad|power/i, bg: ['#166534', '#22c55e'], glyph: 'charger' },
  { key: 'cable', test: /cable|ethernet|hdmi|usb-c cable/i, bg: ['#374151', '#9ca3af'], glyph: 'cable' },
  { key: 'hub', test: /hub/i, bg: ['#7c2d12', '#fb923c'], glyph: 'hub' },
  { key: 'lamp', test: /lamp/i, bg: ['#854d0e', '#facc15'], glyph: 'lamp' },
  { key: 'stand', test: /stand/i, bg: ['#1e40af', '#60a5fa'], glyph: 'stand' },
  { key: 'organizer', test: /organiz/i, bg: ['#5b21b6', '#a78bfa'], glyph: 'box' },
  { key: 'notebook', test: /notebook|paper/i, bg: ['#0e7490', '#22d3ee'], glyph: 'notebook' },
  { key: 'remote', test: /presenter|remote/i, bg: ['#831843', '#ec4899'], glyph: 'remote' },
  { key: 'watch', test: /watch/i, bg: ['#111827', '#4b5563'], glyph: 'watch' },
]

const GLYPHS = {
  mouse: '<rect x="160" y="90" width="80" height="120" rx="40" fill="none" stroke="#fff" stroke-width="10"/><line x1="200" y1="90" x2="200" y2="140" stroke="#fff" stroke-width="8"/><circle cx="200" cy="160" r="8" fill="#fff"/>',
  keyboard: '<rect x="90" y="120" width="220" height="80" rx="12" fill="none" stroke="#fff" stroke-width="10"/><circle cx="125" cy="150" r="7" fill="#fff"/><circle cx="160" cy="150" r="7" fill="#fff"/><circle cx="195" cy="150" r="7" fill="#fff"/><circle cx="230" cy="150" r="7" fill="#fff"/><circle cx="265" cy="150" r="7" fill="#fff"/><rect x="125" y="170" width="150" height="12" rx="6" fill="#fff"/>',
  monitor: '<rect x="100" y="80" width="200" height="120" rx="10" fill="none" stroke="#fff" stroke-width="10"/><line x1="200" y1="200" x2="200" y2="230" stroke="#fff" stroke-width="10"/><line x1="160" y1="230" x2="240" y2="230" stroke="#fff" stroke-width="10" stroke-linecap="round"/><circle cx="200" cy="140" r="24" fill="none" stroke="#fff" stroke-width="8"/>',
  headphones: '<path d="M130 200 v-30 a70 70 0 0 1 140 0 v30" fill="none" stroke="#fff" stroke-width="10"/><rect x="112" y="190" width="36" height="60" rx="14" fill="#fff"/><rect x="252" y="190" width="36" height="60" rx="14" fill="#fff"/>',
  earbuds: '<circle cx="160" cy="140" r="26" fill="none" stroke="#fff" stroke-width="10"/><circle cx="250" cy="140" r="26" fill="none" stroke="#fff" stroke-width="10"/><line x1="160" y1="166" x2="160" y2="220" stroke="#fff" stroke-width="10" stroke-linecap="round"/><line x1="250" y1="166" x2="250" y2="220" stroke="#fff" stroke-width="10" stroke-linecap="round"/>',
  speaker: '<rect x="150" y="70" width="100" height="170" rx="20" fill="none" stroke="#fff" stroke-width="10"/><circle cx="200" cy="130" r="24" fill="none" stroke="#fff" stroke-width="8"/><circle cx="200" cy="195" r="12" fill="#fff"/>',
  webcam: '<circle cx="200" cy="150" r="55" fill="none" stroke="#fff" stroke-width="10"/><circle cx="200" cy="150" r="22" fill="#fff"/><rect x="130" y="225" width="140" height="14" rx="7" fill="#fff"/>',
  charger: '<rect x="140" y="150" width="120" height="60" rx="30" fill="none" stroke="#fff" stroke-width="10"/><path d="M205 130 l-25 35 h18 l-8 30 30-45 h-19 z" fill="#fff"/>',
  cable: '<path d="M110 220 C150 120, 250 120, 290 220" fill="none" stroke="#fff" stroke-width="10" stroke-linecap="round"/><rect x="96" y="210" width="28" height="40" rx="6" fill="#fff"/><rect x="276" y="210" width="28" height="40" rx="6" fill="#fff"/>',
  hub: '<rect x="110" y="130" width="180" height="60" rx="14" fill="none" stroke="#fff" stroke-width="10"/><circle cx="150" cy="160" r="9" fill="#fff"/><circle cx="185" cy="160" r="9" fill="#fff"/><circle cx="220" cy="160" r="9" fill="#fff"/>',
  lamp: '<path d="M150 230 L200 110 L260 170" fill="none" stroke="#fff" stroke-width="10" stroke-linecap="round"/><circle cx="272" cy="182" r="18" fill="#fff"/><line x1="110" y1="230" x2="190" y2="230" stroke="#fff" stroke-width="10" stroke-linecap="round"/>',
  stand: '<path d="M140 90 L260 90 L290 200 L110 200 Z" fill="none" stroke="#fff" stroke-width="10" stroke-linejoin="round"/><line x1="170" y1="200" x2="170" y2="225" stroke="#fff" stroke-width="10"/><line x1="230" y1="200" x2="230" y2="225" stroke="#fff" stroke-width="10"/>',
  box: '<rect x="120" y="140" width="160" height="90" rx="8" fill="none" stroke="#fff" stroke-width="10"/><line x1="120" y1="165" x2="280" y2="165" stroke="#fff" stroke-width="8"/><line x1="200" y1="140" x2="200" y2="230" stroke="#fff" stroke-width="8"/>',
  notebook: '<rect x="140" y="80" width="120" height="150" rx="8" fill="none" stroke="#fff" stroke-width="10"/><line x1="165" y1="120" x2="235" y2="120" stroke="#fff" stroke-width="8" stroke-linecap="round"/><line x1="165" y1="150" x2="235" y2="150" stroke="#fff" stroke-width="8" stroke-linecap="round"/><line x1="165" y1="180" x2="210" y2="180" stroke="#fff" stroke-width="8" stroke-linecap="round"/>',
  remote: '<rect x="175" y="70" width="50" height="170" rx="25" fill="none" stroke="#fff" stroke-width="10"/><circle cx="200" cy="115" r="9" fill="#fff"/><circle cx="200" cy="150" r="9" fill="#fff"/><rect x="188" y="180" width="24" height="30" rx="8" fill="#fff"/>',
  watch: '<rect x="165" y="100" width="70" height="110" rx="24" fill="none" stroke="#fff" stroke-width="10"/><line x1="185" y1="100" x2="185" y2="70" stroke="#fff" stroke-width="10" stroke-linecap="round"/><line x1="215" y1="100" x2="215" y2="70" stroke="#fff" stroke-width="10" stroke-linecap="round"/><line x1="185" y1="210" x2="185" y2="240" stroke="#fff" stroke-width="10" stroke-linecap="round"/><line x1="215" y1="210" x2="215" y2="240" stroke="#fff" stroke-width="10" stroke-linecap="round"/><circle cx="200" cy="155" r="10" fill="#fff"/>',
  chip: '<rect x="150" y="120" width="100" height="100" rx="12" fill="none" stroke="#fff" stroke-width="10"/><rect x="180" y="150" width="40" height="40" rx="6" fill="#fff"/>',
}

function typeFor(product = {}) {
  const hay = [product.title, product.category, ...(product.tags || [])].filter(Boolean).join(' ')
  return TYPES.find(t => t.test.test(hay)) || { key: 'chip', bg: ['#334155', '#64748b'], glyph: 'chip' }
}

export function productArt(product) {
  const t = typeFor(product)
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 300"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${t.bg[0]}"/><stop offset="1" stop-color="${t.bg[1]}"/></linearGradient></defs><rect width="400" height="300" fill="url(#g)"/><circle cx="340" cy="50" r="90" fill="#ffffff" opacity="0.08"/><circle cx="40" cy="270" r="70" fill="#000000" opacity="0.10"/>${GLYPHS[t.glyph]}</svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}

export function artByTitle(title = '') {
  return productArt({ title })
}

export const CATEGORY_ART = {
  peripherals: 'keyboard',
  audio: 'headphones',
  cables: 'cable',
  office: 'lamp',
}

export function categoryArt(category = '') {
  const glyph = CATEGORY_ART[category] || 'chip'
  const t = TYPES.find(t => t.glyph === glyph) || { bg: ['#334155', '#64748b'] }
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 300"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${t.bg[0]}"/><stop offset="1" stop-color="${t.bg[1]}"/></linearGradient></defs><rect width="400" height="300" fill="url(#g)"/><circle cx="340" cy="50" r="90" fill="#ffffff" opacity="0.08"/>${GLYPHS[glyph]}</svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}
