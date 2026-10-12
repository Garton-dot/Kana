/* Kana — node-based D&D 5e campaign plotter. Browser UI. */
(function () {
  'use strict';

  const { NODE_TYPES, EDGE_KINDS } = Model;
  const STORAGE_KEY = 'kana.campaign.v1';
  const PORT_Y = 29; // vertical centre of the ports, relative to the node's top edge
  const GRID = 24;
  const MIN_ZOOM = 0.2;
  const MAX_ZOOM = 2.5;
  const READABLE_ZOOM = 0.55; // smallest zoom used when auto-fitting a freshly loaded board
  const TYPE_ORDER = Object.keys(NODE_TYPES);

  // ---------- state ----------
  let campaign = null;
  const ui = {
    selected: new Set(),
    selectedEdge: null,
    hiddenTypes: new Set(),
    search: '',
    focusThread: true,
  };
  const history = { undo: [], redo: [], lastKey: null, lastTime: 0 };
  const nodeEls = new Map();
  const sizes = new Map();
  let drag = null;

  // ---------- DOM ----------
  const $ = (sel) => document.querySelector(sel);
  const viewport = $('#viewport');
  const world = $('#world');
  const nodeLayer = $('#node-layer');
  const edgeLayer = $('#edge-layer');
  const draftEdge = $('#draft-edge');
  const marquee = $('#marquee');
  const inspector = $('#inspector');
  const contextMenu = $('#context-menu');
  const searchInput = $('#search');

  function h(tag, props, ...children) {
    const el = document.createElement(tag);
    if (props) {
      for (const [k, v] of Object.entries(props)) {
        if (v == null || v === false) continue;
        if (k === 'class') el.className = v;
        else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);
        else if (k === 'dataset') Object.assign(el.dataset, v);
        else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
        else if (k === 'value' || k === 'checked') el[k] = v;
        else el.setAttribute(k, v === true ? '' : v);
      }
    }
    for (const c of children.flat(Infinity)) {
      if (c == null || c === false) continue;
      el.append(c instanceof Node ? c : document.createTextNode(String(c)));
    }
    return el;
  }

  function svg(tag, attrs) {
    const el = document.createElementNS('http://www.w3.org/2000/svg', tag);
    for (const [k, v] of Object.entries(attrs || {})) el.setAttribute(k, v);
    return el;
  }

  const isTyping = (el) => el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT' || el.isContentEditable);
  const nodeById = (id) => campaign.nodes.find((n) => n.id === id);
  const edgeById = (id) => campaign.edges.find((e) => e.id === id);

  // ---------- history & persistence ----------
  function snapshot() {
    const { view, ...rest } = campaign;
    return JSON.stringify(rest);
  }

  // Call BEFORE mutating. Consecutive calls with the same key within a short window coalesce
  // into one undo step (e.g. typing in a text field).
  function checkpoint(key) {
    const now = Date.now();
    if (key && key === history.lastKey && now - history.lastTime < 1500) {
      history.lastTime = now;
      return;
    }
    history.undo.push(snapshot());
    if (history.undo.length > 200) history.undo.shift();
    history.redo.length = 0;
    history.lastKey = key || null;
    history.lastTime = now;
    updateUndoButtons();
  }

  function restore(json) {
    const view = campaign.view;
    campaign = Object.assign(JSON.parse(json), { view });
    for (const id of [...ui.selected]) if (!nodeById(id)) ui.selected.delete(id);
    if (ui.selectedEdge && !edgeById(ui.selectedEdge)) ui.selectedEdge = null;
    history.lastKey = null;
    renderAll();
    scheduleSave();
  }

  function undo() {
    if (!history.undo.length) return;
    history.redo.push(snapshot());
    restore(history.undo.pop());
  }

  function redo() {
    if (!history.redo.length) return;
    history.undo.push(snapshot());
    restore(history.redo.pop());
  }

  function updateUndoButtons() {
    $('#btn-undo').disabled = !history.undo.length;
    $('#btn-redo').disabled = !history.redo.length;
  }

  let saveTimer = null;
  function scheduleSave() {
    clearTimeout(saveTimer);
    $('#status-save').textContent = 'Saving…';
    saveTimer = setTimeout(saveNow, 400);
  }

  function saveNow() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(campaign));
      $('#status-save').textContent = 'Saved locally ' + new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch (err) {
      $('#status-save').textContent = 'Not saved (storage unavailable) — use Export';
    }
  }

  function loadInitial() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) return Model.normalizeCampaign(JSON.parse(raw));
    } catch (err) {
      console.warn('Could not load saved campaign:', err);
    }
    return buildSample(Model);
  }

  // Replace the whole campaign (new / sample / import). Undoable.
  function replaceCampaign(next, fit) {
    checkpoint();
    campaign = next;
    ui.selected.clear();
    ui.selectedEdge = null;
    renderAll();
    if (fit) fitView(READABLE_ZOOM);
    scheduleSave();
  }

  // ---------- view transform ----------
  function applyView() {
    const { x, y, zoom } = campaign.view;
    world.style.transform = `translate(${x}px, ${y}px) scale(${zoom})`;
    const g = GRID * zoom;
    viewport.style.backgroundSize = `${g}px ${g}px`;
    viewport.style.backgroundPosition = `${x}px ${y}px`;
    $('#zoom-label').textContent = Math.round(zoom * 100) + '%';
  }

  function screenToWorld(clientX, clientY) {
    const r = viewport.getBoundingClientRect();
    const { x, y, zoom } = campaign.view;
    return { x: (clientX - r.left - x) / zoom, y: (clientY - r.top - y) / zoom };
  }

  function zoomAt(clientX, clientY, factor) {
    const r = viewport.getBoundingClientRect();
    const v = campaign.view;
    const next = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, v.zoom * factor));
    const px = clientX - r.left;
    const py = clientY - r.top;
    v.x = px - (px - v.x) * (next / v.zoom);
    v.y = py - (py - v.y) * (next / v.zoom);
    v.zoom = next;
    applyView();
    scheduleSave();
  }

  function zoomCenter(factor) {
    const r = viewport.getBoundingClientRect();
    zoomAt(r.left + r.width / 2, r.top + r.height / 2, factor);
  }

  function visibleNodes() {
    return campaign.nodes.filter((n) => !ui.hiddenTypes.has(n.type));
  }

  // Fit all visible nodes in view. With a minZoom, a board too large to fit stays readable
  // and is anchored at its left edge, where the story starts.
  function fitView(minZoom) {
    const floor = typeof minZoom === 'number' ? minZoom : MIN_ZOOM;
    const nodes = visibleNodes();
    const r = viewport.getBoundingClientRect();
    if (!nodes.length) {
      campaign.view = { x: r.width / 2, y: r.height / 2, zoom: 1 };
      applyView();
      return;
    }
    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
    for (const n of nodes) {
      const s = sizes.get(n.id) || { w: 220, h: 100 };
      minX = Math.min(minX, n.x);
      minY = Math.min(minY, n.y);
      maxX = Math.max(maxX, n.x + s.w);
      maxY = Math.max(maxY, n.y + s.h);
    }
    const pad = 60;
    const zoom = Math.min(1.2, Math.max(floor, Math.min((r.width - pad * 2) / (maxX - minX), (r.height - pad * 2) / (maxY - minY))));
    const w = (maxX - minX) * zoom;
    const hgt = (maxY - minY) * zoom;
    campaign.view = {
      zoom,
      x: (w <= r.width - pad * 2 ? (r.width - w) / 2 : pad) - minX * zoom,
      y: (hgt <= r.height - pad * 2 ? (r.height - hgt) / 2 : pad) - minY * zoom,
    };
    applyView();
    scheduleSave();
  }

  function centerOn(id) {
    const n = nodeById(id);
    if (!n) return;
    const r = viewport.getBoundingClientRect();
    const s = sizes.get(id) || { w: 220, h: 100 };
    const v = campaign.view;
    v.x = r.width / 2 - (n.x + s.w / 2) * v.zoom;
    v.y = r.height / 2 - (n.y + s.h / 2) * v.zoom;
    applyView();
  }

  function viewCenterWorld() {
    const r = viewport.getBoundingClientRect();
    return screenToWorld(r.left + r.width / 2, r.top + r.height / 2);
  }

  // ---------- node summaries (shown on cards and in exports) ----------
  function encounterResult(node) {
    if (node.type !== 'encounter' || node.data.kind !== 'combat') return null;
    const monsters = node.data.monsters || [];
    if (!monsters.length) return null;
    return Rules.evaluateEncounter(monsters, campaign.party);
  }

  function nodeChips(node) {
    const d = node.data;
    const chips = [];
    const chip = (text, cls) => chips.push(h('span', { class: 'chip ' + (cls || '') }, text));
    switch (node.type) {
      case 'session':
        if (d.number !== '' && d.number != null) chip('#' + d.number);
        if (d.date) chip(d.date);
        break;
      case 'quest':
        chip(d.status, { completed: 'good', failed: 'bad', abandoned: 'bad', active: 'warn' }[d.status]);
        if (d.reward) chip('🪙 ' + d.reward);
        break;
      case 'beat':
        chip('Act ' + d.act);
        chip(d.status, d.status === 'happened' ? 'good' : d.status === 'skipped' ? 'bad' : '');
        break;
      case 'encounter': {
        chip(d.kind);
        const res = encounterResult(node);
        if (res) {
          chip(res.difficulty, 'diff-' + res.difficulty);
          chip(res.baseXP.toLocaleString() + ' XP');
        }
        if (d.resolved) chip('resolved', 'good');
        break;
      }
      case 'decision': {
        const options = campaign.edges.filter((e) => e.from === node.id).length;
        chip(options + (options === 1 ? ' path' : ' paths'));
        if (d.chosen) chip('✔ ' + d.chosen, 'good');
        break;
      }
      case 'location':
        if (d.terrain) chip(d.terrain);
        if (d.region) chip(d.region);
        break;
      case 'npc':
        if (d.role) chip(d.role);
        chip(d.attitude, { friendly: 'good', hostile: 'bad' }[d.attitude]);
        if (!d.alive) chip('☠ dead', 'bad');
        break;
      case 'faction':
        if (d.standing !== '' && d.standing != null) chip('renown ' + d.standing);
        break;
      case 'item':
        chip(d.rarity);
        if (d.attunement) chip('attunement');
        break;
    }
    for (const t of node.tags) chip('#' + t, 'tag');
    return chips;
  }

  function isDone(node) {
    const d = node.data;
    return (node.type === 'quest' && (d.status === 'completed' || d.status === 'failed' || d.status === 'abandoned')) ||
      (node.type === 'encounter' && d.resolved) ||
      (node.type === 'beat' && d.status === 'skipped');
  }

  // ---------- rendering: nodes ----------
  function buildNodeEl(node) {
    const def = NODE_TYPES[node.type];
    return h('div', {
      class: 'node' + (isDone(node) ? ' done' : ''),
      dataset: { id: node.id },
      style: { '--type-color': def.color, transform: `translate(${node.x}px, ${node.y}px)` },
    },
    h('div', { class: 'port in', title: 'Incoming links' }),
    h('div', { class: 'node-head' },
      h('span', { class: 'node-icon' }, def.icon),
      h('span', { class: 'node-title', title: node.title }, node.title)),
    h('div', { class: 'node-type' }, def.label),
    node.description ? h('div', { class: 'node-desc' }, node.description) : null,
    h('div', { class: 'node-chips' }, nodeChips(node)),
    h('div', { class: 'port out', title: 'Drag to link to another node' }));
  }

  function measure(id) {
    const el = nodeEls.get(id);
    if (el) sizes.set(id, { w: el.offsetWidth, h: el.offsetHeight });
  }

  function renderNodes() {
    nodeLayer.textContent = '';
    nodeEls.clear();
    for (const node of campaign.nodes) {
      if (ui.hiddenTypes.has(node.type)) continue;
      const el = buildNodeEl(node);
      nodeLayer.append(el);
      nodeEls.set(node.id, el);
    }
    for (const id of nodeEls.keys()) measure(id);
    $('#empty-state').hidden = campaign.nodes.length > 0;
  }

  function refreshNode(id) {
    const node = nodeById(id);
    const old = nodeEls.get(id);
    if (!node || !old) return;
    const el = buildNodeEl(node);
    old.replaceWith(el);
    nodeEls.set(id, el);
    measure(id);
  }

  function positionNode(node) {
    const el = nodeEls.get(node.id);
    if (el) el.style.transform = `translate(${node.x}px, ${node.y}px)`;
  }

  // ---------- rendering: edges ----------
  function curve(sx, sy, tx, ty) {
    const dx = Math.max(50, Math.abs(tx - sx) / 2);
    return [sx, sy, sx + dx, sy, tx - dx, ty, tx, ty];
  }

  function curvePath(c) {
    return `M${c[0]},${c[1]} C${c[2]},${c[3]} ${c[4]},${c[5]} ${c[6]},${c[7]}`;
  }

  function renderEdges() {
    edgeLayer.textContent = '';
    for (const e of campaign.edges) {
      const a = nodeById(e.from);
      const b = nodeById(e.to);
      if (!a || !b || !nodeEls.has(a.id) || !nodeEls.has(b.id)) continue;
      const sa = sizes.get(a.id) || { w: 220 };
      const c = curve(a.x + sa.w, a.y + PORT_Y, b.x, b.y + PORT_Y);
      const d = curvePath(c);
      const g = svg('g', { class: 'edge-group', 'data-id': e.id });
      g.append(svg('path', { class: 'edge-hit', d }));
      g.append(svg('path', { class: 'edge', d, 'marker-end': 'url(#arrow)' }));
      if (e.label) {
        const t = svg('text', {
          class: 'edge-label',
          x: (c[0] + 3 * c[2] + 3 * c[4] + c[6]) / 8,
          y: (c[1] + 3 * c[3] + 3 * c[5] + c[7]) / 8 - 8,
        });
        t.textContent = e.label;
        g.append(t);
      }
      edgeLayer.append(g);
    }
    applyHighlights();
  }

  // ---------- highlight: selection, plot thread, search ----------
  function searchMatches() {
    const q = ui.search.trim().toLowerCase();
    if (!q) return null;
    const set = new Set();
    for (const n of campaign.nodes) {
      const hay = [n.title, n.description, n.tags.join(' '), NODE_TYPES[n.type].label,
        ...Object.values(n.data).map((v) => (typeof v === 'object' ? JSON.stringify(v) : String(v)))]
        .join(' ').toLowerCase();
      if (hay.includes(q)) set.add(n.id);
    }
    return set;
  }

  function applyHighlights() {
    const matches = searchMatches();
    let thread = null;
    if (!matches && ui.focusThread && ui.selected.size === 1) {
      const id = [...ui.selected][0];
      thread = new Set([id, ...Model.reachable(campaign, id, 'in'), ...Model.reachable(campaign, id, 'out')]);
    }
    for (const [id, el] of nodeEls) {
      el.classList.toggle('selected', ui.selected.has(id));
      el.classList.toggle('match', !!matches && matches.has(id));
      el.classList.toggle('thread', !!thread && thread.has(id) && !ui.selected.has(id));
      el.classList.toggle('dim', (matches && !matches.has(id)) || (thread && !thread.has(id)) || false);
    }
    for (const g of edgeLayer.children) {
      const e = edgeById(g.dataset.id);
      const selected = ui.selectedEdge === e.id;
      const inThread = !!thread && thread.has(e.from) && thread.has(e.to);
      g.classList.toggle('selected', selected);
      g.classList.toggle('thread', inThread);
      g.classList.toggle('dim', (!!matches && !(matches.has(e.from) && matches.has(e.to))) || (!!thread && !inThread));
      g.querySelector('.edge').setAttribute('marker-end', selected || inThread ? 'url(#arrow-active)' : 'url(#arrow)');
    }
  }

  function renderStatus() {
    const counts = {};
    for (const n of campaign.nodes) counts[n.type] = (counts[n.type] || 0) + 1;
    const parts = [campaign.nodes.length + ' nodes', campaign.edges.length + ' links'];
    $('#status-counts').textContent = parts.join(' · ');
  }

  function renderTopbar() {
    $('#campaign-name').value = campaign.name;
    $('#party-size').value = campaign.party.size;
    $('#party-level').value = campaign.party.level;
  }

  function renderAll() {
    applyView();
    renderTopbar();
    renderNodes();
    renderEdges();
    renderInspector();
    renderStatus();
    updateUndoButtons();
  }

  // Re-render after a data change that may affect several nodes (party, deletions, links…).
  function refreshBoard() {
    renderNodes();
    renderEdges();
    renderStatus();
  }

  // ---------- selection ----------
  function select(ids, edgeId) {
    ui.selected = new Set(ids || []);
    ui.selectedEdge = edgeId || null;
    applyHighlights();
    renderInspector();
  }

  // ---------- mutations ----------
  function addNode(type, x, y, opts) {
    checkpoint();
    const node = Model.createNode(type, x, y);
    campaign.nodes.push(node);
    if (opts && opts.linkFrom && Model.canConnect(campaign, opts.linkFrom, node.id)) {
      campaign.edges.push(Model.createEdge(opts.linkFrom, node.id));
    }
    refreshBoard();
    select([node.id]);
    scheduleSave();
    const title = inspector.querySelector('.title-input');
    if (title) { title.focus(); title.select(); }
    return node;
  }

  function addNodeAtCenter(type) {
    const c = viewCenterWorld();
    // Nudge so repeated clicks don't stack exactly on top of each other.
    const offset = (campaign.nodes.length % 5) * 24;
    addNode(type, c.x - 110 + offset, c.y - 50 + offset);
  }

  function deleteSelection() {
    if (ui.selectedEdge) {
      checkpoint();
      campaign.edges = campaign.edges.filter((e) => e.id !== ui.selectedEdge);
      ui.selectedEdge = null;
    } else if (ui.selected.size) {
      checkpoint();
      campaign.nodes = campaign.nodes.filter((n) => !ui.selected.has(n.id));
      campaign.edges = campaign.edges.filter((e) => !ui.selected.has(e.from) && !ui.selected.has(e.to));
      ui.selected.clear();
    } else return;
    refreshBoard();
    renderInspector();
    scheduleSave();
  }

  function duplicateSelection() {
    if (!ui.selected.size) return;
    checkpoint();
    const map = new Map();
    for (const id of ui.selected) {
      const src = nodeById(id);
      const copy = JSON.parse(JSON.stringify(src));
      copy.id = Model.uid('n');
      copy.x += 40;
      copy.y += 40;
      map.set(id, copy.id);
      campaign.nodes.push(copy);
    }
    for (const e of [...campaign.edges]) {
      if (map.has(e.from) && map.has(e.to)) campaign.edges.push(Model.createEdge(map.get(e.from), map.get(e.to), e.label));
    }
    refreshBoard();
    select([...map.values()]);
    scheduleSave();
  }

  function connect(from, to) {
    if (!Model.canConnect(campaign, from, to)) return false;
    checkpoint();
    const edge = Model.createEdge(from, to, nodeById(from).type === 'decision' ? 'option' : 'leads to');
    campaign.edges.push(edge);
    refreshBoard();
    select([], edge.id);
    scheduleSave();
    return true;
  }

  // Mutate a node from the inspector; coalesces typing into one undo step per field.
  function updateNode(id, key, fn) {
    const node = nodeById(id);
    if (!node) return;
    checkpoint(id + ':' + key);
    fn(node);
    refreshNode(id);
    renderEdges();
    scheduleSave();
  }

  // ---------- context menu ----------
  function openContextMenu(clientX, clientY, items) {
    contextMenu.textContent = '';
    for (const item of items) {
      if (item.label) contextMenu.append(h('div', { class: 'label' }, item.label));
      else {
        contextMenu.append(h('button', {
          class: item.danger ? 'danger' : '',
          onclick: () => { closeContextMenu(); item.run(); },
        }, item.text));
      }
    }
    contextMenu.hidden = false;
    const r = viewport.getBoundingClientRect();
    const mw = contextMenu.offsetWidth;
    const mh = contextMenu.offsetHeight;
    contextMenu.style.left = Math.min(clientX - r.left, r.width - mw - 8) + 'px';
    contextMenu.style.top = Math.max(8, Math.min(clientY - r.top, r.height - mh - 8)) + 'px';
  }

  function closeContextMenu() {
    contextMenu.hidden = true;
  }

  function typeItems(run) {
    return TYPE_ORDER.map((t) => ({ text: NODE_TYPES[t].icon + '  ' + NODE_TYPES[t].label, run: () => run(t) }));
  }

  function addMenuAt(clientX, clientY, linkFrom) {
    const p = screenToWorld(clientX, clientY);
    openContextMenu(clientX, clientY, [
      { label: linkFrom ? 'Add linked node' : 'Add node' },
      ...typeItems((t) => addNode(t, p.x, p.y - PORT_Y, { linkFrom })),
    ]);
  }

  function nodeMenu(clientX, clientY, id) {
    if (!ui.selected.has(id)) select([id]);
    const n = nodeById(id);
    const s = sizes.get(id) || { w: 220 };
    openContextMenu(clientX, clientY, [
      { label: n.title },
      { text: 'Duplicate', run: duplicateSelection },
      { text: 'Center view here', run: () => centerOn(id) },
      { text: 'Select whole thread', run: () => select([id, ...Model.reachable(campaign, id, 'in'), ...Model.reachable(campaign, id, 'out')]) },
      { text: 'Delete', danger: true, run: deleteSelection },
      { label: 'Add linked node' },
      ...typeItems((t) => addNode(t, n.x + s.w + 80, n.y, { linkFrom: id })),
    ]);
  }

  // ---------- pointer interaction ----------
  viewport.addEventListener('pointerdown', (ev) => {
    if (!contextMenu.hidden && !contextMenu.contains(ev.target)) closeContextMenu();
    if (contextMenu.contains(ev.target) || ev.target.closest('.zoom-controls')) return;
    if (ev.button === 2) return;
    viewport.focus({ preventScroll: true });
    const p = screenToWorld(ev.clientX, ev.clientY);

    if (ev.button === 1) {
      ev.preventDefault();
      startPan(ev);
      return;
    }

    const port = ev.target.closest('.port.out');
    const nodeEl = ev.target.closest('.node');
    const edgeEl = ev.target.closest('.edge-group');

    if (port && nodeEl) {
      const from = nodeById(nodeEl.dataset.id);
      const s = sizes.get(from.id);
      drag = { type: 'link', from: from.id, sx: from.x + s.w, sy: from.y + PORT_Y, target: null };
      draftEdge.setAttribute('d', curvePath(curve(drag.sx, drag.sy, p.x, p.y)));
    } else if (nodeEl) {
      const id = nodeEl.dataset.id;
      if (ev.shiftKey || ev.ctrlKey || ev.metaKey) {
        const next = new Set(ui.selected);
        next.has(id) ? next.delete(id) : next.add(id);
        select(next);
        if (!next.has(id)) return;
      } else if (!ui.selected.has(id)) {
        select([id]);
      } else if (ui.selectedEdge) {
        select(ui.selected);
      }
      const start = new Map();
      for (const sid of ui.selected) {
        const n = nodeById(sid);
        if (n) start.set(sid, { x: n.x, y: n.y });
      }
      drag = { type: 'move', px: p.x, py: p.y, start, moved: false, clickId: id, additive: ev.shiftKey || ev.ctrlKey || ev.metaKey };
    } else if (edgeEl) {
      select([], edgeEl.dataset.id);
      return;
    } else if (ev.shiftKey) {
      drag = { type: 'marquee', cx: ev.clientX, cy: ev.clientY, base: new Set(ui.selected) };
    } else {
      startPan(ev);
      drag.clearOnClick = true;
    }
    viewport.setPointerCapture(ev.pointerId);
  });

  function startPan(ev) {
    drag = { type: 'pan', cx: ev.clientX, cy: ev.clientY, vx: campaign.view.x, vy: campaign.view.y, moved: false };
    viewport.classList.add('panning');
    viewport.setPointerCapture(ev.pointerId);
  }

  viewport.addEventListener('pointermove', (ev) => {
    if (!drag) return;
    const p = screenToWorld(ev.clientX, ev.clientY);
    switch (drag.type) {
      case 'pan': {
        const dx = ev.clientX - drag.cx;
        const dy = ev.clientY - drag.cy;
        if (Math.abs(dx) + Math.abs(dy) > 3) drag.moved = true;
        campaign.view.x = drag.vx + dx;
        campaign.view.y = drag.vy + dy;
        applyView();
        break;
      }
      case 'move': {
        const dx = p.x - drag.px;
        const dy = p.y - drag.py;
        if (!drag.moved) {
          if (Math.abs(dx) + Math.abs(dy) < 3 / campaign.view.zoom) return;
          drag.moved = true;
          checkpoint();
          for (const id of drag.start.keys()) nodeEls.get(id)?.classList.add('dragging');
        }
        const snap = ev.altKey ? 1 : GRID / 2;
        for (const [id, s] of drag.start) {
          const n = nodeById(id);
          n.x = Math.round((s.x + dx) / snap) * snap;
          n.y = Math.round((s.y + dy) / snap) * snap;
          positionNode(n);
        }
        renderEdges();
        break;
      }
      case 'link': {
        draftEdge.setAttribute('d', curvePath(curve(drag.sx, drag.sy, p.x, p.y)));
        const under = document.elementFromPoint(ev.clientX, ev.clientY);
        const targetEl = under && under.closest('.node');
        const target = targetEl && Model.canConnect(campaign, drag.from, targetEl.dataset.id) ? targetEl.dataset.id : null;
        if (target !== drag.target) {
          if (drag.target) nodeEls.get(drag.target)?.classList.remove('link-target');
          if (target) nodeEls.get(target)?.classList.add('link-target');
          drag.target = target;
        }
        break;
      }
      case 'marquee': {
        const r = viewport.getBoundingClientRect();
        const x1 = Math.min(drag.cx, ev.clientX), y1 = Math.min(drag.cy, ev.clientY);
        const x2 = Math.max(drag.cx, ev.clientX), y2 = Math.max(drag.cy, ev.clientY);
        Object.assign(marquee.style, { left: x1 - r.left + 'px', top: y1 - r.top + 'px', width: x2 - x1 + 'px', height: y2 - y1 + 'px' });
        marquee.hidden = false;
        const a = screenToWorld(x1, y1);
        const b = screenToWorld(x2, y2);
        const next = new Set(drag.base);
        for (const n of visibleNodes()) {
          const s = sizes.get(n.id) || { w: 220, h: 100 };
          if (n.x < b.x && n.x + s.w > a.x && n.y < b.y && n.y + s.h > a.y) next.add(n.id);
        }
        ui.selected = next;
        ui.selectedEdge = null;
        applyHighlights();
        break;
      }
    }
  });

  function endDrag(ev) {
    if (!drag) return;
    const d = drag;
    drag = null;
    viewport.classList.remove('panning');
    if (d.type === 'pan') {
      if (d.moved) scheduleSave();
      else if (d.clearOnClick) select([]);
    } else if (d.type === 'move') {
      for (const el of nodeEls.values()) el.classList.remove('dragging');
      if (d.moved) scheduleSave();
      else if (!d.additive && ui.selected.size > 1) select([d.clickId]);
    } else if (d.type === 'link') {
      draftEdge.setAttribute('d', '');
      if (d.target) {
        nodeEls.get(d.target)?.classList.remove('link-target');
        connect(d.from, d.target);
      } else if (ev && ev.type === 'pointerup') {
        const under = document.elementFromPoint(ev.clientX, ev.clientY);
        if (!under || !under.closest('.node')) addMenuAt(ev.clientX, ev.clientY, d.from);
      }
    } else if (d.type === 'marquee') {
      marquee.hidden = true;
      renderInspector();
    }
  }

  // The board is positioned purely by transform; undo any native scrolling the browser
  // performs on the overflow:hidden viewport (e.g. when focusing an off-screen element).
  viewport.addEventListener('scroll', () => {
    viewport.scrollLeft = 0;
    viewport.scrollTop = 0;
  });

  viewport.addEventListener('pointerup', endDrag);
  viewport.addEventListener('pointercancel', endDrag);

  viewport.addEventListener('wheel', (ev) => {
    if (ev.target.closest('.context-menu')) return;
    ev.preventDefault();
    const factor = Math.exp(-ev.deltaY * (ev.deltaMode === 1 ? 0.05 : 0.0015));
    zoomAt(ev.clientX, ev.clientY, factor);
  }, { passive: false });

  viewport.addEventListener('dblclick', (ev) => {
    // Pointer capture retargets click events to the viewport, so hit-test by position instead.
    const under = document.elementFromPoint(ev.clientX, ev.clientY) || ev.target;
    const nodeEl = under.closest('.node');
    if (nodeEl) {
      select([nodeEl.dataset.id]);
      const title = inspector.querySelector('.title-input');
      if (title) { title.focus(); title.select(); }
    } else if (!under.closest('.edge-group, .context-menu, .zoom-controls')) {
      addMenuAt(ev.clientX, ev.clientY);
    }
  });

  viewport.addEventListener('contextmenu', (ev) => {
    ev.preventDefault();
    const nodeEl = ev.target.closest('.node');
    const edgeEl = ev.target.closest('.edge-group');
    if (nodeEl) nodeMenu(ev.clientX, ev.clientY, nodeEl.dataset.id);
    else if (edgeEl) {
      select([], edgeEl.dataset.id);
      openContextMenu(ev.clientX, ev.clientY, [
        { text: 'Reverse direction', run: reverseSelectedEdge },
        { text: 'Delete link', danger: true, run: deleteSelection },
      ]);
    } else addMenuAt(ev.clientX, ev.clientY);
  });

  // Palette drag & drop onto the board.
  viewport.addEventListener('dragover', (ev) => {
    if (ev.dataTransfer.types.includes('application/x-kana-type')) ev.preventDefault();
  });
  viewport.addEventListener('drop', (ev) => {
    const type = ev.dataTransfer.getData('application/x-kana-type');
    if (!NODE_TYPES[type]) return;
    ev.preventDefault();
    const p = screenToWorld(ev.clientX, ev.clientY);
    addNode(type, p.x - 110, p.y - 20);
  });

  // ---------- keyboard ----------
  document.addEventListener('keydown', (ev) => {
    const mod = ev.ctrlKey || ev.metaKey;
    if (ev.key === 'Escape') {
      closeContextMenu();
      $('#file-menu').hidden = true;
      if (document.activeElement === searchInput) {
        searchInput.value = '';
        ui.search = '';
        applyHighlights();
        viewport.focus();
      } else if (!isTyping(document.activeElement)) select([]);
      return;
    }
    if (isTyping(document.activeElement)) return;
    if (mod && ev.key.toLowerCase() === 'z') {
      ev.preventDefault();
      ev.shiftKey ? redo() : undo();
    } else if (mod && ev.key.toLowerCase() === 'y') {
      ev.preventDefault();
      redo();
    } else if (mod && ev.key.toLowerCase() === 'd') {
      ev.preventDefault();
      duplicateSelection();
    } else if (mod && ev.key.toLowerCase() === 'a') {
      ev.preventDefault();
      select(visibleNodes().map((n) => n.id));
    } else if (ev.key === 'Delete' || ev.key === 'Backspace') {
      ev.preventDefault();
      deleteSelection();
    } else if (ev.key === 'f' || ev.key === 'F') {
      fitView();
    } else if (ev.key === '/') {
      ev.preventDefault();
      searchInput.focus();
    } else if (ev.key === '+' || ev.key === '=') {
      zoomCenter(1.2);
    } else if (ev.key === '-') {
      zoomCenter(1 / 1.2);
    }
  });

  // ---------- inspector ----------
  function field(label, control, extraClass) {
    return h('div', { class: 'field ' + (extraClass || '') }, h('label', null, label), control);
  }

  function renderInspector() {
    inspector.textContent = '';
    if (ui.selectedEdge) return renderEdgeInspector(edgeById(ui.selectedEdge));
    if (ui.selected.size === 1) return renderNodeInspector(nodeById([...ui.selected][0]));
    if (ui.selected.size > 1) return renderMultiInspector();
    renderCampaignInspector();
  }

  function renderNodeInspector(node) {
    const def = NODE_TYPES[node.type];
    inspector.style.setProperty('--type-color', def.color);
    inspector.append(h('div', { class: 'type-banner' }, def.icon, ' ', def.label));

    inspector.append(h('div', { class: 'field' }, h('input', {
      class: 'title-input', type: 'text', value: node.title, 'aria-label': 'Title',
      oninput: (e) => updateNode(node.id, 'title', (n) => { n.title = e.target.value; }),
    })));

    inspector.append(field('Description', h('textarea', {
      rows: 3, value: node.description,
      oninput: (e) => updateNode(node.id, 'description', (n) => { n.description = e.target.value; }),
    })));

    inspector.append(field('Tags (comma separated)', h('input', {
      type: 'text', value: node.tags.join(', '),
      oninput: (e) => updateNode(node.id, 'tags', (n) => {
        n.tags = e.target.value.split(',').map((t) => t.trim()).filter(Boolean);
      }),
    })));

    // Typing in the type select changes which other fields are visible.
    const hasConditional = def.fields.some((f) => f.showIf);
    for (const f of def.fields) {
      if (f.showIf && !Object.entries(f.showIf).every(([k, v]) => node.data[k] === v)) continue;
      const value = node.data[f.key];
      const set = (v, rerender) => {
        updateNode(node.id, f.key, (n) => { n.data[f.key] = v; });
        if (rerender) renderInspector();
      };
      switch (f.kind) {
        case 'text':
          inspector.append(field(f.label, h('input', { type: 'text', value: value || '', placeholder: f.placeholder, oninput: (e) => set(e.target.value) })));
          break;
        case 'textarea':
          inspector.append(field(f.label, h('textarea', { rows: 3, value: value || '', oninput: (e) => set(e.target.value) })));
          break;
        case 'number':
          inspector.append(field(f.label, h('input', { type: 'number', value: value === '' || value == null ? '' : value, oninput: (e) => set(e.target.value === '' ? '' : Number(e.target.value)) })));
          break;
        case 'select':
          inspector.append(field(f.label, h('select', { onchange: (e) => set(e.target.value, hasConditional) },
            f.options.map((o) => h('option', { value: o, selected: o === value }, o)))));
          break;
        case 'checkbox':
          inspector.append(h('div', { class: 'field checkbox' }, h('label', null,
            h('input', { type: 'checkbox', checked: !!value, onchange: (e) => set(e.target.checked) }), f.label)));
          break;
        case 'monsters':
          inspector.append(monsterEditor(node));
          break;
      }
    }

    inspector.append(connectionsSection(node));

    inspector.append(h('div', { class: 'actions' },
      h('button', { onclick: duplicateSelection }, 'Duplicate'),
      h('button', { onclick: () => centerOn(node.id) }, 'Center'),
      h('button', { class: 'danger', onclick: deleteSelection }, 'Delete')));
  }

  function monsterEditor(node) {
    const wrap = h('div', { class: 'field' }, h('label', null, 'Monsters'));
    const list = h('div');
    const calcBox = h('div');
    const monsters = () => node.data.monsters || (node.data.monsters = []);

    function drawCalc() {
      calcBox.replaceChildren(encounterCalc(node));
    }

    function drawRows() {
      list.textContent = '';
      if (monsters().length) list.append(h('div', { class: 'monster-head' }, h('span', null, 'Name'), h('span', null, 'CR'), h('span', null, 'Qty'), h('span')));
      monsters().forEach((m, i) => {
        const change = (key, v) => {
          updateNode(node.id, 'monsters', (n) => { n.data.monsters[i][key] = v; });
          drawCalc();
        };
        list.append(h('div', { class: 'monster-row' },
          h('input', { type: 'text', value: m.name || '', placeholder: 'Monster', 'aria-label': 'Monster name', oninput: (e) => change('name', e.target.value) }),
          h('select', { 'aria-label': 'Challenge rating', onchange: (e) => change('cr', e.target.value) },
            Rules.CR_LIST.map((cr) => h('option', { value: cr, selected: cr === Rules.normalizeCR(m.cr) }, 'CR ' + cr))),
          h('input', { type: 'number', min: 1, value: m.count, 'aria-label': 'Quantity', oninput: (e) => change('count', Math.max(0, Number(e.target.value) || 0)) }),
          h('button', {
            title: 'Remove', class: 'danger',
            onclick: () => {
              checkpoint();
              node.data.monsters.splice(i, 1);
              history.lastKey = null;
              refreshNode(node.id);
              renderEdges();
              scheduleSave();
              drawRows();
            },
          }, '×')));
      });
      drawCalc();
    }

    wrap.append(list, h('button', {
      onclick: () => {
        checkpoint();
        monsters().push({ name: '', cr: '1', count: 1 });
        refreshNode(node.id);
        renderEdges();
        scheduleSave();
        drawRows();
        const inputs = list.querySelectorAll('.monster-row input[type=text]');
        inputs[inputs.length - 1]?.focus();
      },
    }, '+ Add monster'), calcBox);
    drawRows();
    return wrap;
  }

  function encounterCalc(node) {
    const res = Rules.evaluateEncounter(node.data.monsters || [], campaign.party);
    const t = res.thresholds;
    const box = h('div', { class: 'calc' });
    box.append(h('div', { class: 'verdict diff-' + res.difficulty }, res.monsterCount ? res.difficulty : 'No monsters yet'));
    box.append(h('div', { class: 'muted' }, `vs. party of ${campaign.party.size} at level ${campaign.party.level}`));

    // Meter: coloured bands up to "deadly", with a marker at the encounter's adjusted XP.
    const max = Math.max(t.deadly * 1.25, res.adjustedXP * 1.05, 1);
    const band = (from, to, color) => h('span', { style: { width: ((to - from) / max) * 100 + '%', background: color } });
    const meter = h('div', { class: 'meter', title: 'Easy / Medium / Hard / Deadly thresholds' },
      band(0, t.easy, 'var(--trivial)'), band(t.easy, t.medium, 'var(--easy)'), band(t.medium, t.hard, 'var(--medium)'),
      band(t.hard, t.deadly, 'var(--hard)'), band(t.deadly, max, 'var(--deadly)'));
    if (res.monsterCount) meter.append(h('i', { style: { left: `calc(${Math.min(100, (res.adjustedXP / max) * 100)}% - 1px)` } }));
    box.append(meter);

    const row = (k, v) => h('tr', null, h('td', null, k), h('td', null, v));
    box.append(h('table', null,
      row('Monsters', res.monsterCount),
      row('Base XP (awarded)', res.baseXP.toLocaleString()),
      row('Multiplier', '×' + res.multiplier),
      row('Adjusted XP (difficulty)', res.adjustedXP.toLocaleString()),
      row('XP per character', res.xpPerCharacter.toLocaleString()),
      row('Thresholds E / M / H / D', [t.easy, t.medium, t.hard, t.deadly].map((x) => x.toLocaleString()).join(' / '))));
    return box;
  }

  function connectionsSection(node) {
    const wrap = h('div');
    const incoming = campaign.edges.filter((e) => e.to === node.id);
    const outgoing = campaign.edges.filter((e) => e.from === node.id);
    const item = (e, otherId, arrow) => {
      const other = nodeById(otherId);
      return h('li', null,
        h('span', { class: 'rel' }, arrow + ' ' + (e.label || 'link')),
        h('a', { onclick: () => { select([otherId]); centerOn(otherId); } }, NODE_TYPES[other.type].icon + ' ' + other.title),
        h('button', { title: 'Edit link', onclick: () => select([], e.id) }, '✎'));
    };
    wrap.append(h('h3', null, `Links in (${incoming.length})`));
    wrap.append(incoming.length ? h('ul', { class: 'link-list' }, incoming.map((e) => item(e, e.from, '←'))) : h('div', { class: 'muted' }, 'None'));
    wrap.append(h('h3', null, `Links out (${outgoing.length})`));
    wrap.append(outgoing.length ? h('ul', { class: 'link-list' }, outgoing.map((e) => item(e, e.to, '→'))) : h('div', { class: 'muted' }, 'Drag the ● on the node\'s right edge to link it.'));

    // Link to an existing node without dragging (handy on large boards).
    const candidates = campaign.nodes.filter((n) => Model.canConnect(campaign, node.id, n.id));
    if (candidates.length) {
      wrap.append(h('select', {
        style: { width: '100%', marginTop: '8px' },
        onchange: (e) => { if (e.target.value) connect(node.id, e.target.value); },
      }, h('option', { value: '' }, '+ Link to…'),
      candidates.map((n) => h('option', { value: n.id }, NODE_TYPES[n.type].icon + ' ' + n.title))));
    }
    return wrap;
  }

  function reverseSelectedEdge() {
    const e = edgeById(ui.selectedEdge);
    if (!e || campaign.edges.some((o) => o.from === e.to && o.to === e.from)) return;
    checkpoint();
    [e.from, e.to] = [e.to, e.from];
    renderEdges();
    renderInspector();
    scheduleSave();
  }

  function renderEdgeInspector(edge) {
    const a = nodeById(edge.from);
    const b = nodeById(edge.to);
    inspector.style.removeProperty('--type-color');
    inspector.append(h('div', { class: 'type-banner', style: { color: 'var(--accent)' } }, '🔗 Link'));
    const nodeLink = (n) => h('a', { onclick: () => { select([n.id]); centerOn(n.id); } }, NODE_TYPES[n.type].icon + ' ' + n.title);
    inspector.append(h('ul', { class: 'link-list' },
      h('li', null, h('span', { class: 'rel' }, 'From'), nodeLink(a)),
      h('li', null, h('span', { class: 'rel' }, 'To'), nodeLink(b))));
    const listId = 'edge-kinds';
    inspector.append(field('Label', h('input', {
      type: 'text', value: edge.label, list: listId,
      oninput: (e) => {
        checkpoint(edge.id + ':label');
        edge.label = e.target.value;
        renderEdges();
        scheduleSave();
      },
    })));
    inspector.append(h('datalist', { id: listId }, EDGE_KINDS.map((k) => h('option', { value: k }))));
    inspector.append(h('div', { class: 'row' }, EDGE_KINDS.slice(0, 4).map((k) => h('button', {
      onclick: () => {
        checkpoint();
        edge.label = k;
        renderEdges();
        renderInspector();
        scheduleSave();
      },
    }, k))));
    inspector.append(h('div', { class: 'actions' },
      h('button', { onclick: reverseSelectedEdge }, '⇄ Reverse'),
      h('button', { class: 'danger', onclick: deleteSelection }, 'Delete link')));
  }

  function renderMultiInspector() {
    inspector.style.removeProperty('--type-color');
    inspector.append(h('h2', null, ui.selected.size + ' nodes selected'));
    inspector.append(h('ul', { class: 'link-list' }, [...ui.selected].map((id) => {
      const n = nodeById(id);
      return h('li', null, h('a', { onclick: () => select([id]) }, NODE_TYPES[n.type].icon + ' ' + n.title));
    })));
    inspector.append(h('div', { class: 'actions' },
      h('button', { onclick: duplicateSelection }, 'Duplicate'),
      h('button', { class: 'danger', onclick: deleteSelection }, 'Delete all')));
  }

  function renderCampaignInspector() {
    inspector.style.removeProperty('--type-color');
    inspector.append(h('h2', { style: { marginTop: 0 } }, 'Campaign overview'));
    inspector.append(h('div', { style: { fontSize: '16px', fontWeight: 600 } }, campaign.name));

    // Node counts.
    const counts = {};
    for (const n of campaign.nodes) counts[n.type] = (counts[n.type] || 0) + 1;
    inspector.append(h('h3', null, 'Board'));
    inspector.append(h('div', { class: 'stat-grid' },
      TYPE_ORDER.filter((t) => counts[t]).map((t) => [h('span', null, NODE_TYPES[t].icon + ' ' + NODE_TYPES[t].label), h('span', null, counts[t])])));
    if (!campaign.nodes.length) inspector.append(h('div', { class: 'muted' }, 'No nodes yet.'));

    // Quests.
    const quests = campaign.nodes.filter((n) => n.type === 'quest');
    if (quests.length) {
      const byStatus = {};
      for (const q of quests) byStatus[q.data.status] = (byStatus[q.data.status] || 0) + 1;
      inspector.append(h('h3', null, 'Quests'));
      inspector.append(h('div', { class: 'stat-grid' }, Object.entries(byStatus).map(([s, c]) => [h('span', null, s), h('span', null, c)])));
    }

    // Encounters & XP progression.
    const combats = campaign.nodes.map((n) => ({ n, r: encounterResult(n) })).filter((x) => x.r);
    const party = campaign.party;
    inspector.append(h('h3', null, 'Encounters & XP'));
    const diffCounts = {};
    let totalXP = 0;
    let remainingXP = 0;
    for (const { n, r } of combats) {
      diffCounts[r.difficulty] = (diffCounts[r.difficulty] || 0) + 1;
      totalXP += r.baseXP;
      if (!n.data.resolved) remainingXP += r.baseXP;
    }
    const perChar = Math.floor(totalXP / party.size);
    const startXP = Rules.xpForLevel(party.level);
    const projected = Rules.levelForXP(startXP + perChar);
    inspector.append(h('div', { class: 'stat-grid' },
      Rules.DIFFICULTIES.filter((d) => diffCounts[d]).map((d) => [h('span', null, h('span', { class: 'chip diff-' + d }, d)), h('span', null, diffCounts[d])]),
      h('span', null, 'Total XP on the board'), h('span', null, totalXP.toLocaleString()),
      h('span', null, 'Still unresolved'), h('span', null, remainingXP.toLocaleString()),
      h('span', null, 'XP per character'), h('span', null, perChar.toLocaleString()),
      h('span', null, 'Projected level'), h('span', null, `${party.level} → ${projected}`),
      h('span', null, 'Daily adjusted-XP budget'), h('span', null, Rules.dailyBudget(party).toLocaleString())));
    inspector.append(h('p', { class: 'hint', style: { marginTop: '6px' } },
      'Projection assumes the party starts at the minimum XP for its level and clears every combat encounter on the board.'));

    // Entry points.
    const roots = Model.roots(campaign);
    if (roots.length) {
      inspector.append(h('h3', null, 'Entry points'));
      inspector.append(h('ul', { class: 'link-list' }, roots.map((n) => h('li', null,
        h('a', { onclick: () => { select([n.id]); centerOn(n.id); } }, NODE_TYPES[n.type].icon + ' ' + n.title)))));
    }

    inspector.append(h('h3', null, 'Tips'));
    inspector.append(h('ul', { class: 'hint', style: { paddingLeft: '18px' } },
      h('li', null, 'Select a node to highlight its whole plot thread — everything leading to it and everything it unlocks.'),
      h('li', null, 'Drop a link on empty board to create a new connected node.'),
      h('li', null, 'Hold Alt while dragging to move without grid snapping.'),
      h('li', null, 'Your campaign autosaves in this browser. Export JSON to back it up or share it.')));
  }

  // ---------- palette & filters ----------
  function renderPalette() {
    const list = $('#palette-list');
    const filters = $('#filter-list');
    for (const t of TYPE_ORDER) {
      const def = NODE_TYPES[t];
      list.append(h('button', {
        class: 'palette-item', draggable: 'true', style: { '--type-color': def.color },
        title: 'Add a ' + def.label,
        onclick: () => addNodeAtCenter(t),
        ondragstart: (e) => {
          e.dataTransfer.setData('application/x-kana-type', t);
          e.dataTransfer.effectAllowed = 'copy';
        },
      }, h('span', null, def.icon), def.label));
      filters.append(h('label', { class: 'filter-item', style: { '--type-color': def.color } },
        h('input', {
          type: 'checkbox', checked: true,
          onchange: (e) => {
            e.target.checked ? ui.hiddenTypes.delete(t) : ui.hiddenTypes.add(t);
            for (const id of [...ui.selected]) if (nodeById(id).type === t && !e.target.checked) ui.selected.delete(id);
            refreshBoard();
            renderInspector();
          },
        }),
        h('span', { class: 'swatch' }), def.label));
    }
  }

  // ---------- top bar ----------
  $('#campaign-name').addEventListener('input', (e) => {
    checkpoint('campaign:name');
    campaign.name = e.target.value;
    if (!ui.selected.size && !ui.selectedEdge) renderInspector();
    scheduleSave();
  });

  function partyInput(key, min, max) {
    return (e) => {
      const v = Math.round(Number(e.target.value));
      if (!Number.isFinite(v) || v < min || v > max) return;
      checkpoint('party:' + key);
      campaign.party[key] = v;
      refreshBoard();
      // Keep focus in the party field: only redraw the inspector's derived numbers.
      renderInspector();
      scheduleSave();
    };
  }
  $('#party-size').addEventListener('input', partyInput('size', 1, 12));
  $('#party-level').addEventListener('input', partyInput('level', 1, 20));
  $('#party-size').addEventListener('change', renderTopbar);
  $('#party-level').addEventListener('change', renderTopbar);

  searchInput.addEventListener('input', () => {
    ui.search = searchInput.value;
    applyHighlights();
  });
  searchInput.addEventListener('keydown', (ev) => {
    if (ev.key !== 'Enter') return;
    const matches = searchMatches();
    const first = matches && campaign.nodes.find((n) => matches.has(n.id) && nodeEls.has(n.id));
    if (first) {
      centerOn(first.id);
      ui.selected = new Set([first.id]);
      ui.selectedEdge = null;
      renderInspector();
      applyHighlights();
    }
  });

  $('#btn-undo').addEventListener('click', undo);
  $('#btn-redo').addEventListener('click', redo);
  $('#btn-fit').addEventListener('click', () => fitView());
  $('#btn-layout').addEventListener('click', () => {
    checkpoint();
    Model.autoLayout(campaign);
    refreshBoard();
    fitView(READABLE_ZOOM);
    scheduleSave();
  });
  $('#zoom-in').addEventListener('click', () => zoomCenter(1.2));
  $('#zoom-out').addEventListener('click', () => zoomCenter(1 / 1.2));
  $('#focus-thread').addEventListener('change', (e) => {
    ui.focusThread = e.target.checked;
    applyHighlights();
  });

  // Campaign menu.
  const fileMenu = $('#file-menu');
  $('#btn-file').addEventListener('click', (e) => {
    e.stopPropagation();
    fileMenu.hidden = !fileMenu.hidden;
  });
  document.addEventListener('click', (e) => {
    if (!fileMenu.hidden && !fileMenu.contains(e.target)) fileMenu.hidden = true;
  });
  fileMenu.addEventListener('click', (e) => {
    const action = e.target.dataset.action;
    if (!action) return;
    fileMenu.hidden = true;
    switch (action) {
      case 'new':
        if (campaign.nodes.length && !confirm('Start a new campaign? (You can undo this.)')) return;
        replaceCampaign(Model.createCampaign(), true);
        break;
      case 'sample':
        if (campaign.nodes.length && !confirm('Replace the board with the sample campaign? (You can undo this.)')) return;
        replaceCampaign(buildSample(Model), true);
        break;
      case 'import':
        $('#import-file').click();
        break;
      case 'export-json':
        download(slug(campaign.name) + '.json', JSON.stringify(campaign, null, 2), 'application/json');
        break;
      case 'export-md':
        download(slug(campaign.name) + '.md', toMarkdown(), 'text/markdown');
        break;
    }
  });

  $('#import-file').addEventListener('change', async (e) => {
    const file = e.target.files[0];
    e.target.value = '';
    if (!file) return;
    try {
      const next = Model.normalizeCampaign(JSON.parse(await file.text()));
      replaceCampaign(next, true);
    } catch (err) {
      alert('Could not import that file: ' + err.message);
    }
  });

  function slug(s) {
    return (s || 'campaign').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'campaign';
  }

  function download(name, text, type) {
    const url = URL.createObjectURL(new Blob([text], { type }));
    const a = h('a', { href: url, download: name });
    document.body.append(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  // A readable prep document: every node grouped by type, with fields and links.
  function toMarkdown() {
    const lines = [`# ${campaign.name}`, '', `Party: ${campaign.party.size} characters, level ${campaign.party.level}`, ''];
    for (const t of TYPE_ORDER) {
      const nodes = campaign.nodes.filter((n) => n.type === t);
      if (!nodes.length) continue;
      const def = NODE_TYPES[t];
      lines.push(`## ${def.icon} ${def.label}s`, '');
      for (const n of nodes) {
        lines.push(`### ${n.title}`, '');
        if (n.description) lines.push(n.description, '');
        for (const f of def.fields) {
          const v = n.data[f.key];
          if (f.kind === 'monsters') {
            if (!v || !v.length) continue;
            const res = Rules.evaluateEncounter(v, campaign.party);
            lines.push(`- **${f.label}:** ${v.map((m) => `${m.count}× ${m.name || 'Unnamed'} (CR ${m.cr})`).join(', ')}`);
            lines.push(`- **Difficulty:** ${res.difficulty} (${res.adjustedXP.toLocaleString()} adjusted XP; ${res.baseXP.toLocaleString()} XP awarded)`);
          } else if (f.kind === 'checkbox') {
            lines.push(`- **${f.label}:** ${v ? 'yes' : 'no'}`);
          } else if (v !== '' && v != null) {
            lines.push(`- **${f.label}:** ${String(v).replace(/\n/g, ' ')}`);
          }
        }
        if (n.tags.length) lines.push(`- **Tags:** ${n.tags.map((x) => '#' + x).join(' ')}`);
        for (const e of campaign.edges.filter((x) => x.from === n.id)) {
          lines.push(`- → ${e.label || 'link'}: ${nodeById(e.to).title}`);
        }
        lines.push('');
      }
    }
    return lines.join('\n');
  }

  // ---------- boot ----------
  campaign = loadInitial();
  renderPalette();
  renderAll();
  const v = campaign.view;
  if (v.x === 0 && v.y === 0 && v.zoom === 1) fitView(READABLE_ZOOM);
  window.addEventListener('resize', applyView);
  window.addEventListener('beforeunload', () => { clearTimeout(saveTimer); saveNow(); });

  // Exposed for debugging from the console.
  window.kana = { get campaign() { return campaign; }, fitView, undo, redo };
})();
