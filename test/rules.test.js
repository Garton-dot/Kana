const test = require('node:test');
const assert = require('node:assert/strict');
const Rules = require('../js/rules.js');

test('xpForCR handles fractions, decimals and unknowns', () => {
  assert.equal(Rules.xpForCR('1/4'), 50);
  assert.equal(Rules.xpForCR('0.5'), 100);
  assert.equal(Rules.xpForCR(5), 1800);
  assert.equal(Rules.xpForCR('30'), 155000);
  assert.equal(Rules.xpForCR('banana'), 0);
});

test('encounter multiplier follows the DMG table with party-size adjustments', () => {
  assert.equal(Rules.encounterMultiplier(1, 4), 1);
  assert.equal(Rules.encounterMultiplier(2, 4), 1.5);
  assert.equal(Rules.encounterMultiplier(6, 4), 2);
  assert.equal(Rules.encounterMultiplier(7, 4), 2.5);
  assert.equal(Rules.encounterMultiplier(11, 4), 3);
  assert.equal(Rules.encounterMultiplier(15, 4), 4);
  // Small party: next multiplier up; large party: next one down.
  assert.equal(Rules.encounterMultiplier(1, 2), 1.5);
  assert.equal(Rules.encounterMultiplier(15, 2), 5);
  assert.equal(Rules.encounterMultiplier(1, 6), 0.5);
  assert.equal(Rules.encounterMultiplier(2, 6), 1);
});

test('4 level-3 PCs vs a CR 1 + three CR 1/4 monsters is medium', () => {
  // 200 + 3*50 = 350 base; 4 monsters => x2 => 700 adjusted.
  // Level 3 x4 thresholds: easy 300, medium 600, hard 900, deadly 1600.
  const r = Rules.evaluateEncounter(
    [{ name: 'Bugbear', cr: '1', count: 1 }, { name: 'Goblin', cr: '1/4', count: 3 }],
    { size: 4, level: 3 },
  );
  assert.equal(r.baseXP, 350);
  assert.equal(r.multiplier, 2);
  assert.equal(r.adjustedXP, 700);
  assert.deepEqual(r.thresholds, { easy: 300, medium: 600, hard: 900, deadly: 1600 });
  assert.equal(r.difficulty, 'medium');
  assert.equal(r.xpPerCharacter, 87);
});

test('mixed-level parties sum individual thresholds', () => {
  const t = Rules.partyThresholds([1, 2, 5]);
  assert.deepEqual(t, { easy: 325, medium: 650, hard: 975, deadly: 1400 });
});

test('empty encounters are trivial and ignore bad counts', () => {
  assert.equal(Rules.evaluateEncounter([], { size: 4, level: 1 }).difficulty, 'trivial');
  const r = Rules.evaluateEncounter([{ cr: '1', count: -3 }, { cr: '1', count: 'x' }], [1, 1]);
  assert.equal(r.monsterCount, 0);
  assert.equal(r.difficulty, 'trivial');
});

test('deadly threshold reached', () => {
  const r = Rules.evaluateEncounter([{ cr: '5', count: 1 }], { size: 4, level: 1 });
  assert.equal(r.difficulty, 'deadly');
});

test('level progression helpers', () => {
  assert.equal(Rules.levelForXP(0), 1);
  assert.equal(Rules.levelForXP(299), 1);
  assert.equal(Rules.levelForXP(300), 2);
  assert.equal(Rules.levelForXP(1e9), 20);
  assert.equal(Rules.xpForLevel(5), 6500);
  assert.equal(Rules.xpForLevel(99), 355000);
  assert.equal(Rules.dailyBudget({ size: 4, level: 5 }), 14000);
});
