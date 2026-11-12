/* CarLog icon set — 24 grid, stroke 1.8 (= Pleos 48dp grid / 3.6px), round caps & joins.
   Usage: <svg class="i"><use href="#i-pin"/></svg>. This script injects the sprite inline so it also works from file://. */
(function () {
  const P = {
    pin: '<path d="M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11z"/><circle cx="12" cy="10" r="2.4"/>',
    play: '<path d="M8 5.5v13l10.5-6.5z"/>',
    stop: '<rect x="6.5" y="6.5" width="11" height="11" rx="1.5"/>',
    mic: '<rect x="9" y="3" width="6" height="11.5" rx="3"/><path d="M5.5 11.5a6.5 6.5 0 0 0 13 0M12 18v3"/>',
    check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    refresh: '<path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3M19.5 4.5v4h-4"/>',
    warning: '<path d="M12 4l9 16H3z"/><path d="M12 10v4.5M12 17.6v.1"/>',
    battery: '<rect x="3" y="7.5" width="16" height="9" rx="2"/><path d="M21 10.5v3M6.5 10.5v3M9.5 10.5v3M12.5 10.5v3"/>',
    range: '<path d="M4 19c4-1 3-6 8-7s4-6 8-7"/><circle cx="4" cy="19" r="1.4"/><circle cx="20" cy="5" r="1.4"/>',
    door: '<path d="M6 21V5.5L14 3v18M6 21h12M14 5.5h4V21"/><path d="M11 12.5v.1"/>',
    window: '<path d="M4 15.5L8.5 6h11v9.5z"/><path d="M4 15.5h15.5M11.5 6v9.5"/>',
    car: '<path d="M4 16.5v-3.5l2-5h12l2 5v3.5z"/><path d="M4 13h16"/><circle cx="7.5" cy="16.5" r="1.8"/><circle cx="16.5" cy="16.5" r="1.8"/>',
    user: '<circle cx="12" cy="8.5" r="3.8"/><path d="M4.5 20.5a7.5 7.5 0 0 1 15 0"/>',
    chevron: '<path d="M6 9.5l6 6 6-6"/>',
    close: '<path d="M6 6l12 12M18 6L6 18"/>',
    pen: '<path d="M4 20l1-4.5L15.5 5a2.1 2.1 0 0 1 3 3L8 18.5z"/><path d="M13.5 7l3 3"/>',
    clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
    gear: '<circle cx="12" cy="12" r="3"/><path d="M12 2.8v2.4M12 18.8v2.4M2.8 12h2.4M18.8 12h2.4M5.5 5.5l1.7 1.7M16.8 16.8l1.7 1.7M5.5 18.5l1.7-1.7M16.8 7.2l1.7-1.7"/>',
    board: '<rect x="4" y="4" width="16" height="16" rx="3"/><path d="M4 10h16M10 10v10"/>',
    keyboard: '<rect x="2.5" y="6" width="19" height="12" rx="2"/><path d="M6 10h.1M9.5 10h.1M13 10h.1M16.5 10h.1M7.5 14.5h9"/>',
    lock: '<rect x="5" y="10.5" width="14" height="10" rx="2"/><path d="M8 10.5V7.5a4 4 0 0 1 8 0v3"/>',
    trash: '<path d="M4.5 7h15M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13"/>',
    speaker: '<path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z"/><path d="M15.5 9a4.5 4.5 0 0 1 0 6M18 6.5a8 8 0 0 1 0 11"/>',
    star: '<path d="M12 4l2.5 5.2 5.7.8-4.1 4 1 5.6L12 17l-5.1 2.6 1-5.6-4.1-4 5.7-.8z"/>',
    bag: '<path d="M5 8h14l-1 12H6z"/><path d="M9 10.5V7a3 3 0 0 1 6 0v3.5"/>',
    wrench: '<path d="M15 3.8a5 5 0 0 0-4.6 6.9L3.8 17.3a2 2 0 0 0 2.9 2.9l6.6-6.6A5 5 0 0 0 20.2 9l-3 3-3.2-.9-.9-3.2 3-3a5 5 0 0 0-1.1-.1z"/>',
    note: '<path d="M6 3.5h9l3.5 3.5v13.5H6z"/><path d="M9 11h6.5M9 15h6.5"/>',
    signal: '<path d="M5 19v-3M10 19v-6.5M15 19V9M20 19V5"/>',
    send: '<path d="M4 12l16-7.5-6 16-2.5-6.5z"/>',
    swap: '<path d="M7 7.5h11.5l-3-3M17 16.5H5.5l3 3"/>'
  };
  const sprite = '<svg xmlns="http://www.w3.org/2000/svg" style="position:absolute;width:0;height:0;overflow:hidden" aria-hidden="true">' +
    Object.entries(P).map(([k, v]) => `<symbol id="i-${k}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${v}</symbol>`).join('') +
    '</svg>';
  document.body.insertAdjacentHTML('afterbegin', sprite);
  // theme from ?theme=day|night, frame from ?frame=half|full
  const q = new URLSearchParams(location.search);
  if (q.get('theme')) document.documentElement.dataset.theme = q.get('theme');
  if (q.get('frame')) document.documentElement.dataset.frame = q.get('frame');
})();
