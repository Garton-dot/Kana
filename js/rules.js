/*
 * D&D 5e encounter-building rules (2014 Dungeon Master's Guide, ch. 3).
 * Pure functions, no DOM — usable in the browser (window.Rules) and in Node tests.
 */
(function (root) {
  'use strict';

  // Per-character XP thresholds: [easy, medium, hard, deadly], index = level - 1.
  const XP_THRESHOLDS = [
    [25, 50, 75, 100], [50, 100, 150, 200], [75, 150, 225, 400], [125, 250, 375, 500],
    [250, 500, 750, 1100], [300, 600, 900, 1400], [350, 750, 1100, 1700], [450, 900, 1400, 2100],
    [550, 1100, 1600, 2400], [600, 1200, 1900, 2800], [800, 1600, 2400, 3600], [1000, 2000, 3000, 4500],
    [1100, 2200, 3400, 5100], [1250, 2500, 3800, 5700], [1400, 2800, 4300, 6400], [1600, 3200, 4800, 7200],
    [2000, 3900, 5900, 8800], [2100, 4200, 6300, 9500], [2400, 4900, 7300, 10900], [2800, 5700, 8500, 12700],
  ];

  // Adjusted XP per character for a full adventuring day, index = level - 1.
  const DAILY_XP = [
    300, 600, 1200, 1700, 3500, 4000, 5000, 6000, 7500, 9000,
    10500, 11500, 13500, 15000, 18000, 20000, 25000, 27000, 30000, 40000,
  ];

  // Total XP required to reach each level (PHB), index = level - 1.
  const LEVEL_XP = [
    0, 300, 900, 2700, 6500, 14000, 23000, 34000, 48000, 64000,
    85000, 100000, 120000, 140000, 165000, 195000, 225000, 265000, 305000, 355000,
  ];

  const CR_XP = {
    '0': 10, '1/8': 25, '1/4': 50, '1/2': 100,
    '1': 200, '2': 450, '3': 700, '4': 1100, '5': 1800, '6': 2300, '7': 2900, '8': 3900,
    '9': 5000, '10': 5900, '11': 7200, '12': 8400, '13': 10000, '14': 11500, '15': 13000,
    '16': 15000, '17': 18000, '18': 20000, '19': 22000, '20': 25000, '21': 33000, '22': 41000,
    '23': 50000, '24': 62000, '25': 75000, '26': 90000, '27': 105000, '28': 120000,
    '29': 135000, '30': 155000,
  };
  const CR_LIST = Object.keys(CR_XP);

  const MULTIPLIERS = [0.5, 1, 1.5, 2, 2.5, 3, 4, 5];
  const DIFFICULTIES = ['trivial', 'easy', 'medium', 'hard', 'deadly'];

  function clampLevel(level) {
    const n = Math.round(Number(level));
    if (!Number.isFinite(n)) return 1;
    return Math.min(20, Math.max(1, n));
  }

  function normalizeCR(cr) {
    const s = String(cr == null ? '' : cr).trim();
    if (s in CR_XP) return s;
    const decimals = { '0.125': '1/8', '.125': '1/8', '0.25': '1/4', '.25': '1/4', '0.5': '1/2', '.5': '1/2' };
    return decimals[s] || null;
  }

  function xpForCR(cr) {
    const key = normalizeCR(cr);
    return key ? CR_XP[key] : 0;
  }

  // Index into MULTIPLIERS for a monster count, before party-size adjustment.
  function multiplierIndex(count) {
    if (count <= 0) return 1;
    if (count === 1) return 1;
    if (count === 2) return 2;
    if (count <= 6) return 3;
    if (count <= 10) return 4;
    if (count <= 14) return 5;
    return 6;
  }

  function encounterMultiplier(monsterCount, partySize) {
    let i = multiplierIndex(monsterCount);
    if (partySize < 3) i += 1;
    else if (partySize >= 6) i -= 1;
    return MULTIPLIERS[Math.max(0, Math.min(MULTIPLIERS.length - 1, i))];
  }

  function partyThresholds(levels) {
    const t = [0, 0, 0, 0];
    levels.forEach(function (lvl) {
      const row = XP_THRESHOLDS[clampLevel(lvl) - 1];
      for (let i = 0; i < 4; i++) t[i] += row[i];
    });
    return { easy: t[0], medium: t[1], hard: t[2], deadly: t[3] };
  }

  // Build a party level list from either an explicit array or {size, level}.
  function partyLevels(party) {
    if (Array.isArray(party)) return party.map(clampLevel);
    const size = Math.max(1, Math.round(Number(party && party.size) || 1));
    const level = clampLevel(party && party.level);
    return Array.from({ length: size }, function () { return level; });
  }

  /*
   * monsters: [{ name, cr, count }]
   * party: [levels] or { size, level }
   */
  function evaluateEncounter(monsters, party) {
    const levels = partyLevels(party);
    let baseXP = 0;
    let count = 0;
    (monsters || []).forEach(function (m) {
      const n = Math.max(0, Math.round(Number(m.count) || 0));
      baseXP += xpForCR(m.cr) * n;
      count += n;
    });
    const multiplier = count ? encounterMultiplier(count, levels.length) : 1;
    const adjustedXP = Math.round(baseXP * multiplier);
    const thresholds = partyThresholds(levels);

    let difficulty = 'trivial';
    if (count > 0) {
      if (adjustedXP >= thresholds.deadly) difficulty = 'deadly';
      else if (adjustedXP >= thresholds.hard) difficulty = 'hard';
      else if (adjustedXP >= thresholds.medium) difficulty = 'medium';
      else if (adjustedXP >= thresholds.easy) difficulty = 'easy';
    }

    return {
      baseXP: baseXP,
      monsterCount: count,
      multiplier: multiplier,
      adjustedXP: adjustedXP,
      thresholds: thresholds,
      difficulty: difficulty,
      xpPerCharacter: levels.length ? Math.floor(baseXP / levels.length) : 0,
    };
  }

  function dailyBudget(party) {
    return partyLevels(party).reduce(function (sum, lvl) { return sum + DAILY_XP[lvl - 1]; }, 0);
  }

  function levelForXP(xp) {
    let level = 1;
    for (let i = 0; i < LEVEL_XP.length; i++) if (xp >= LEVEL_XP[i]) level = i + 1;
    return level;
  }

  function xpForLevel(level) {
    return LEVEL_XP[clampLevel(level) - 1];
  }

  const api = {
    CR_LIST: CR_LIST,
    DIFFICULTIES: DIFFICULTIES,
    xpForCR: xpForCR,
    normalizeCR: normalizeCR,
    encounterMultiplier: encounterMultiplier,
    partyThresholds: partyThresholds,
    evaluateEncounter: evaluateEncounter,
    dailyBudget: dailyBudget,
    levelForXP: levelForXP,
    xpForLevel: xpForLevel,
  };

  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.Rules = api;
})(typeof window !== 'undefined' ? window : globalThis);
