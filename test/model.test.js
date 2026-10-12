const test = require('node:test');
const assert = require('node:assert/strict');
const Model = require('../js/model.js');
const buildSample = require('../js/sample.js');

function chain() {
  const c = Model.createCampaign('t');
  const [a, b, d, x] = ['quest', 'encounter', 'location', 'npc'].map((t) => Model.createNode(t));
  c.nodes.push(a, b, d, x);
  c.edges.push(Model.createEdge(a.id, b.id), Model.createEdge(b.id, d.id));
  return { c, a, b, d, x };
}

test('createNode fills type defaults', () => {
  const n = Model.createNode('encounter', 10.4, 20.6);
  assert.equal(n.x, 10);
  assert.equal(n.y, 21);
  assert.equal(n.data.kind, 'combat');
  assert.deepEqual(n.data.monsters, []);
  assert.equal(Model.createNode('npc').data.alive, true);
  assert.throws(() => Model.createNode('dragon'));
});

test('canConnect rejects self-links, duplicates and unknown nodes', () => {
  const { c, a, b, x } = chain();
  assert.equal(Model.canConnect(c, a.id, a.id), false);
  assert.equal(Model.canConnect(c, a.id, b.id), false);
  assert.equal(Model.canConnect(c, a.id, 'nope'), false);
  assert.equal(Model.canConnect(c, a.id, x.id), true);
  assert.equal(Model.canConnect(c, b.id, a.id), true);
});

test('reachable walks both directions and survives cycles', () => {
  const { c, a, b, d, x } = chain();
  c.edges.push(Model.createEdge(d.id, a.id)); // cycle
  assert.deepEqual([...Model.reachable(c, a.id, 'out')].sort(), [b.id, d.id].sort());
  assert.deepEqual([...Model.reachable(c, d.id, 'in')].sort(), [a.id, b.id].sort());
  assert.equal(Model.reachable(c, x.id, 'out').size, 0);
});

test('roots are nodes with no incoming links', () => {
  const { c, a, x } = chain();
  assert.deepEqual(Model.roots(c).map((n) => n.id).sort(), [a.id, x.id].sort());
});

test('autoLayout places nodes in columns by depth', () => {
  const { c, a, b, d, x } = chain();
  Model.autoLayout(c, { colWidth: 100 });
  assert.deepEqual([a.x, b.x, d.x, x.x], [0, 100, 200, 0]);
});

test('autoLayout terminates on cycles', () => {
  const { c, a, d } = chain();
  c.edges.push(Model.createEdge(d.id, a.id));
  Model.autoLayout(c);
  for (const n of c.nodes) assert.ok(Number.isFinite(n.x) && Number.isFinite(n.y));
});

test('normalizeCampaign drops bad nodes and dangling edges', () => {
  const raw = {
    name: 'Imported',
    party: { size: '5', level: 99 },
    nodes: [
      { id: 'a', type: 'quest', title: 'Q', x: '5' },
      { id: 'a', type: 'npc' },           // duplicate id
      { id: 'b', type: 'not-a-type' },    // unknown type
      { id: 'c', type: 'npc', data: { attitude: 'hostile' } },
    ],
    edges: [
      { from: 'a', to: 'c', label: 'involves' },
      { from: 'a', to: 'c' },             // duplicate
      { from: 'a', to: 'b' },             // dangling
      { from: 'c', to: 'c' },             // self
    ],
  };
  const c = Model.normalizeCampaign(raw);
  assert.equal(c.name, 'Imported');
  assert.deepEqual(c.party, { size: 5, level: 20 });
  assert.deepEqual(c.nodes.map((n) => n.id), ['a', 'c']);
  assert.equal(c.nodes[0].x, 5);
  assert.equal(c.nodes[1].data.attitude, 'hostile');
  assert.equal(c.nodes[1].data.alive, true);
  assert.equal(c.edges.length, 1);
  assert.throws(() => Model.normalizeCampaign(null));
  assert.throws(() => Model.normalizeCampaign({}));
});

test('sample campaign round-trips through normalizeCampaign', () => {
  const s = buildSample(Model);
  const back = Model.normalizeCampaign(JSON.parse(JSON.stringify(s)));
  assert.equal(back.nodes.length, s.nodes.length);
  assert.equal(back.edges.length, s.edges.length);
});
