/* A small original starter campaign used on first launch and via "Load sample". */
(function (root) {
  'use strict';

  function buildSample(Model) {
    const c = Model.createCampaign('The Drowned Bell');
    c.party = { size: 4, level: 3 };

    function add(type, title, description, data, tags) {
      const n = Model.createNode(type, 0, 0, title);
      n.description = description || '';
      Object.assign(n.data, data || {});
      n.tags = tags || [];
      c.nodes.push(n);
      return n.id;
    }
    function link(from, to, label) {
      c.edges.push(Model.createEdge(from, to, label));
    }

    const s1 = add('session', 'Session 1: Fog on Saltmere', 'The party arrives in the fishing village of Saltmere.', { number: 1 }, ['act-1']);
    const town = add('location', 'Saltmere', 'A fog-bound fishing village. Its chapel bell sank in a storm 30 years ago — and has started ringing underwater.', { region: 'Grey Coast', terrain: 'coastal', features: 'Crooked docks, the Gull & Anchor tavern, a ruined chapel.' });
    const mira = add('npc', 'Mira Holloway', 'Harbourmaster. Hires the party.', { race: 'Human', role: 'Harbourmaster', alignment: 'NG', attitude: 'friendly', motivation: 'Keep the fleet fishing — the village is starving.', secret: 'Her father helped sink the bell on purpose.' });
    const quest = add('quest', 'Silence the Bell', 'Find out why the drowned bell rings and make it stop.', { status: 'active', giver: 'Mira Holloway', hook: 'Boats that hear the bell never come home.', reward: '150 gp and free passage on any Saltmere ship' });
    const docks = add('encounter', 'Ambush at the Docks', 'Wreck-scavengers try to scare off the newcomers.', {
      kind: 'combat',
      monsters: [{ name: 'Bandit', cr: '1/8', count: 4 }, { name: 'Bandit Captain', cr: '2', count: 1 }],
      tactics: 'Captain stays on the boat with a crossbow; bandits cut the mooring lines.',
      treasure: '2d6 gp, a waterlogged map of the bay',
    });
    const choice = add('decision', 'How to reach the bell?', 'The map shows two routes to the wreck.', { question: 'Dive the reef at low tide, or bargain with the sea hag for a water-breathing charm?' });
    const reef = add('encounter', 'The Reef Dive', 'Underwater exploration among kelp and coral.', {
      kind: 'combat',
      monsters: [{ name: 'Reef Shark', cr: '1/2', count: 3 }],
      tactics: 'Sharks circle, attacking whoever is bleeding.',
    });
    const hag = add('npc', 'Granny Brine', 'A sea hag who trades in drowned secrets.', { race: 'Sea Hag', role: 'Bargainer', alignment: 'CE', attitude: 'indifferent', motivation: 'Wants Mira\'s true name.', secret: 'She is the one ringing the bell.' });
    const bargain = add('encounter', 'A Bargain with Granny Brine', 'Social encounter in the hag\'s tidal cave.', { kind: 'social', tactics: 'She will not fight unless cornered; she lies with every third sentence.' });
    const charm = add('item', 'Pearl of the Drowned', 'Grants water breathing for 1 hour, once per dawn.', { rarity: 'uncommon', attunement: true });
    const wreck = add('location', 'Wreck of the Chapel Bell', 'The bell lies in a sunken chapel 60 ft below the bay.', { region: 'Saltmere Bay', terrain: 'coastal' });
    const finale = add('encounter', 'The Bell Tolls', 'The drowned congregation rises.', {
      kind: 'combat',
      monsters: [{ name: 'Ghoul', cr: '1', count: 1 }, { name: 'Ghast', cr: '2', count: 1 }],
      tactics: 'Each round the bell rings, every creature must make a DC 12 Wisdom save or be frightened.',
      treasure: 'Chapel reliquary (250 gp), +1 shield',
    });
    const beat = add('beat', 'The Truth Surfaces', 'Whatever the outcome, Mira\'s secret comes out.', { act: 'I', status: 'planned' });
    const note = add('note', 'DM reminder', 'Foreshadow the bell in Session 1: have dock-hands flinch at a distant chime.');

    link(s1, town, 'located at');
    link(s1, quest, 'leads to');
    link(mira, quest, 'involves');
    link(quest, docks, 'leads to');
    link(docks, choice, 'leads to');
    link(choice, reef, 'option A');
    link(choice, bargain, 'option B');
    link(hag, bargain, 'involves');
    link(bargain, charm, 'rewards');
    link(reef, wreck, 'leads to');
    link(charm, wreck, 'leads to');
    link(wreck, finale, 'leads to');
    link(finale, beat, 'leads to');

    Model.autoLayout(c, { colWidth: 280, rowHeight: 180 });
    // The DM note isn't part of the plot flow; tuck it above the first session.
    const noteNode = c.nodes.find((n) => n.id === note);
    const first = c.nodes.find((n) => n.id === s1);
    noteNode.x = first.x;
    noteNode.y = first.y - 220;
    return c;
  }

  if (typeof module !== 'undefined' && module.exports) module.exports = buildSample;
  else root.buildSample = buildSample;
})(typeof window !== 'undefined' ? window : globalThis);
