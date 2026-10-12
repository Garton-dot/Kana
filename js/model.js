/*
 * Campaign data model: node types, campaign creation, graph queries and import validation.
 * Pure functions, no DOM — usable in the browser (window.Model) and in Node tests.
 */
(function (root) {
  'use strict';

  const SCHEMA_VERSION = 1;

  /*
   * Field kinds understood by the inspector:
   *   text, textarea, number, select (options), checkbox, monsters (encounter roster)
   */
  const NODE_TYPES = {
    session: {
      label: 'Session', icon: '📅', color: '#6c8cff',
      fields: [
        { key: 'number', label: 'Session #', kind: 'number' },
        { key: 'date', label: 'Date', kind: 'text', placeholder: 'YYYY-MM-DD' },
        { key: 'recap', label: 'Recap', kind: 'textarea' },
      ],
    },
    quest: {
      label: 'Quest', icon: '📜', color: '#e0b04a',
      fields: [
        { key: 'status', label: 'Status', kind: 'select', options: ['planned', 'active', 'completed', 'failed', 'abandoned'] },
        { key: 'giver', label: 'Quest giver', kind: 'text' },
        { key: 'hook', label: 'Hook', kind: 'textarea' },
        { key: 'reward', label: 'Reward', kind: 'text' },
      ],
    },
    beat: {
      label: 'Plot Beat', icon: '✒️', color: '#c27cff',
      fields: [
        { key: 'act', label: 'Act', kind: 'select', options: ['I', 'II', 'III', 'Epilogue'] },
        { key: 'status', label: 'Status', kind: 'select', options: ['planned', 'happened', 'skipped'] },
      ],
    },
    encounter: {
      label: 'Encounter', icon: '⚔️', color: '#ff6b6b',
      fields: [
        { key: 'kind', label: 'Type', kind: 'select', options: ['combat', 'social', 'exploration', 'puzzle', 'trap'] },
        { key: 'monsters', label: 'Monsters', kind: 'monsters', showIf: { kind: 'combat' } },
        { key: 'tactics', label: 'Tactics', kind: 'textarea' },
        { key: 'treasure', label: 'Treasure', kind: 'text' },
        { key: 'resolved', label: 'Resolved', kind: 'checkbox' },
      ],
    },
    decision: {
      label: 'Decision', icon: '🔀', color: '#4ad6c5',
      fields: [
        { key: 'question', label: 'Choice facing the party', kind: 'textarea' },
        { key: 'chosen', label: 'Outcome chosen', kind: 'text' },
      ],
    },
    location: {
      label: 'Location', icon: '🗺️', color: '#5fc96b',
      fields: [
        { key: 'region', label: 'Region', kind: 'text' },
        { key: 'terrain', label: 'Terrain', kind: 'select', options: ['urban', 'wilderness', 'dungeon', 'underdark', 'coastal', 'mountain', 'planar'] },
        { key: 'features', label: 'Notable features', kind: 'textarea' },
      ],
    },
    npc: {
      label: 'NPC', icon: '🧙', color: '#ff9f5a',
      fields: [
        { key: 'race', label: 'Race / ancestry', kind: 'text' },
        { key: 'role', label: 'Role / class', kind: 'text' },
        { key: 'alignment', label: 'Alignment', kind: 'select', options: ['LG', 'NG', 'CG', 'LN', 'N', 'CN', 'LE', 'NE', 'CE', 'Unaligned'] },
        { key: 'attitude', label: 'Attitude to party', kind: 'select', options: ['friendly', 'indifferent', 'hostile'] },
        { key: 'motivation', label: 'Motivation', kind: 'textarea' },
        { key: 'secret', label: 'Secret', kind: 'textarea' },
        { key: 'alive', label: 'Alive', kind: 'checkbox' },
      ],
    },
    faction: {
      label: 'Faction', icon: '🛡️', color: '#8f9bb3',
      fields: [
        { key: 'goal', label: 'Goal', kind: 'textarea' },
        { key: 'standing', label: 'Party renown', kind: 'number' },
      ],
    },
    item: {
      label: 'Item', icon: '💎', color: '#ffd166',
      fields: [
        { key: 'rarity', label: 'Rarity', kind: 'select', options: ['common', 'uncommon', 'rare', 'very rare', 'legendary', 'artifact'] },
        { key: 'attunement', label: 'Requires attunement', kind: 'checkbox' },
        { key: 'holder', label: 'Current holder', kind: 'text' },
      ],
    },
    note: {
      label: 'Note', icon: '📝', color: '#a0a7b8',
      fields: [],
    },
  };

  const EDGE_KINDS = ['leads to', 'requires', 'involves', 'located at', 'rewards', 'opposes', 'allied with'];

  let idCounter = 0;
  function uid(prefix) {
    idCounter = (idCounter + 1) % 1e6;
    return (prefix || 'n') + Date.now().toString(36) + Math.random().toString(36).slice(2, 6) + idCounter.toString(36);
  }

  function defaultData(type) {
    const def = NODE_TYPES[type];
    const data = {};
    if (!def) return data;
    def.fields.forEach(function (f) {
      if (f.kind === 'select') data[f.key] = f.options[0];
      else if (f.kind === 'checkbox') data[f.key] = f.key === 'alive';
      else if (f.kind === 'monsters') data[f.key] = [];
      else if (f.kind === 'number') data[f.key] = '';
      else data[f.key] = '';
    });
    return data;
  }

  function createNode(type, x, y, title) {
    if (!NODE_TYPES[type]) throw new Error('Unknown node type: ' + type);
    return {
      id: uid('n'),
      type: type,
      x: Math.round(x || 0),
      y: Math.round(y || 0),
      title: title || 'New ' + NODE_TYPES[type].label,
      description: '',
      tags: [],
      data: defaultData(type),
    };
  }

  function createEdge(from, to, label) {
    return { id: uid('e'), from: from, to: to, label: label || 'leads to' };
  }

  function createCampaign(name) {
    return {
      version: SCHEMA_VERSION,
      name: name || 'Untitled Campaign',
      party: { size: 4, level: 1 },
      nodes: [],
      edges: [],
      view: { x: 0, y: 0, zoom: 1 },
    };
  }

  function edgeExists(campaign, from, to) {
    return campaign.edges.some(function (e) { return e.from === from && e.to === to; });
  }

  function canConnect(campaign, from, to) {
    if (from === to) return false;
    const ids = new Set(campaign.nodes.map(function (n) { return n.id; }));
    return ids.has(from) && ids.has(to) && !edgeExists(campaign, from, to);
  }

  // Breadth-first walk along edges; direction 'out' (descendants) or 'in' (ancestors).
  function reachable(campaign, startId, direction) {
    const adj = new Map();
    campaign.edges.forEach(function (e) {
      const a = direction === 'in' ? e.to : e.from;
      const b = direction === 'in' ? e.from : e.to;
      if (!adj.has(a)) adj.set(a, []);
      adj.get(a).push(b);
    });
    const seen = new Set();
    const queue = [startId];
    while (queue.length) {
      const cur = queue.shift();
      (adj.get(cur) || []).forEach(function (next) {
        if (!seen.has(next) && next !== startId) {
          seen.add(next);
          queue.push(next);
        }
      });
    }
    return seen;
  }

  // Nodes with no incoming edges — the campaign's entry points.
  function roots(campaign) {
    const hasIncoming = new Set(campaign.edges.map(function (e) { return e.to; }));
    return campaign.nodes.filter(function (n) { return !hasIncoming.has(n.id); });
  }

  // Lay nodes out in columns by longest path from a root (cycles are tolerated).
  function autoLayout(campaign, opts) {
    const o = Object.assign({ colWidth: 300, rowHeight: 170 }, opts || {});
    const depth = new Map();
    campaign.nodes.forEach(function (n) { depth.set(n.id, 0); });
    // Bellman-Ford style relaxation bounded by node count so cycles can't loop forever.
    for (let pass = 0; pass < campaign.nodes.length; pass++) {
      let changed = false;
      campaign.edges.forEach(function (e) {
        if (!depth.has(e.from) || !depth.has(e.to)) return;
        const d = depth.get(e.from) + 1;
        if (d > depth.get(e.to) && d < campaign.nodes.length) {
          depth.set(e.to, d);
          changed = true;
        }
      });
      if (!changed) break;
    }
    const columns = new Map();
    campaign.nodes.forEach(function (n) {
      const d = depth.get(n.id);
      if (!columns.has(d)) columns.set(d, []);
      columns.get(d).push(n);
    });
    columns.forEach(function (col, d) {
      col.forEach(function (n, i) {
        n.x = d * o.colWidth;
        n.y = Math.round((i - (col.length - 1) / 2) * o.rowHeight);
      });
    });
    return campaign;
  }

  // Validate and normalise an imported campaign object. Throws on unusable input.
  function normalizeCampaign(raw) {
    if (!raw || typeof raw !== 'object') throw new Error('Campaign file is not a JSON object.');
    if (!Array.isArray(raw.nodes)) throw new Error('Campaign file has no "nodes" array.');
    const c = createCampaign(typeof raw.name === 'string' ? raw.name : undefined);
    if (raw.party && typeof raw.party === 'object') {
      c.party.size = Math.min(12, Math.max(1, Math.round(Number(raw.party.size)) || 4));
      c.party.level = Math.min(20, Math.max(1, Math.round(Number(raw.party.level)) || 1));
    }
    const ids = new Set();
    raw.nodes.forEach(function (n) {
      if (!n || !NODE_TYPES[n.type] || typeof n.id !== 'string' || ids.has(n.id)) return;
      ids.add(n.id);
      c.nodes.push({
        id: n.id,
        type: n.type,
        x: Number(n.x) || 0,
        y: Number(n.y) || 0,
        title: String(n.title || NODE_TYPES[n.type].label),
        description: String(n.description || ''),
        tags: Array.isArray(n.tags) ? n.tags.map(String) : [],
        data: Object.assign(defaultData(n.type), n.data && typeof n.data === 'object' ? n.data : {}),
      });
    });
    (Array.isArray(raw.edges) ? raw.edges : []).forEach(function (e) {
      if (!e || !ids.has(e.from) || !ids.has(e.to) || e.from === e.to) return;
      if (edgeExists(c, e.from, e.to)) return;
      c.edges.push({ id: typeof e.id === 'string' ? e.id : uid('e'), from: e.from, to: e.to, label: String(e.label || '') });
    });
    if (raw.view && typeof raw.view === 'object') {
      c.view = { x: Number(raw.view.x) || 0, y: Number(raw.view.y) || 0, zoom: Number(raw.view.zoom) || 1 };
    }
    return c;
  }

  const api = {
    SCHEMA_VERSION: SCHEMA_VERSION,
    NODE_TYPES: NODE_TYPES,
    EDGE_KINDS: EDGE_KINDS,
    uid: uid,
    createNode: createNode,
    createEdge: createEdge,
    createCampaign: createCampaign,
    defaultData: defaultData,
    canConnect: canConnect,
    reachable: reachable,
    roots: roots,
    autoLayout: autoLayout,
    normalizeCampaign: normalizeCampaign,
  };

  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.Model = api;
})(typeof window !== 'undefined' ? window : globalThis);
