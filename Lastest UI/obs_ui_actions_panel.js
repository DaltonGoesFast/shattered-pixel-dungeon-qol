/**
 * If/Then OBS ui_state editor for points-config.html.
 * Owns #obsUiActionsRoot. Uses GET/POST /api/obs-ui-actions.
 * Groups actions that share the same OBS scene + when trigger.
 */
(function (global) {
  'use strict';

  const UI_SCENES = [
    ['game', 'Game'],
    ['alchemy', 'Alchemy'],
    ['journal', 'Journal scene'],
    ['interlevel', 'Interlevel'],
    ['title', 'Title'],
    ['start', 'Start'],
    ['hero_select', 'Hero select'],
    ['rankings', 'Rankings'],
    ['welcome', 'Welcome'],
  ];
  const UI_WINDOWS = [
    ['journal', 'Journal window'],
    ['inventory', 'Bag / quick-bag'],
    ['item_info', 'Item inspect'],
  ];
  const TRANSFORM_FIELDS = [
    ['positionX', 'X'],
    ['positionY', 'Y'],
    ['scaleX', 'Scale X'],
    ['scaleY', 'Scale Y'],
    ['rotation', 'Rot'],
  ];
  const SCENE_LABEL = new Map(UI_SCENES);
  const WINDOW_LABEL = new Map(UI_WINDOWS);

  let dirty = false;
  let loaded = { enabled: false, obs_ws_url: 'ws://127.0.0.1:4456', actions: [] };

  function root() {
    return document.getElementById('obsUiActionsRoot');
  }

  function setDirty(on) {
    dirty = !!on;
    if (typeof global.updateDirtyChips === 'function') global.updateDirtyChips();
  }

  function emptySource() {
    return {
      enabled: true,
      source_name: '',
      scene_item_id: null,
      then: { visible: false },
    };
  }

  function emptyGroup() {
    return {
      scene_name: '',
      when: { scenes: [], windows: [] },
      sources: [emptySource()],
    };
  }

  function emptyAction() {
    return {
      enabled: true,
      scene_name: '',
      source_name: '',
      scene_item_id: null,
      when: { scenes: [], windows: [] },
      then: { visible: false },
    };
  }

  function normalizeList(list) {
    return [...new Set((list || []).map((s) => String(s).trim().toLowerCase()).filter(Boolean))];
  }

  function groupKey(action) {
    const when = action.when || {};
    return JSON.stringify([
      String(action.scene_name || '').trim(),
      normalizeList(when.scenes).slice().sort(),
      normalizeList(when.windows).slice().sort(),
    ]);
  }

  function toGroups(actions) {
    const groups = [];
    const map = new Map();
    (actions || []).forEach((action) => {
      const key = groupKey(action);
      let group = map.get(key);
      if (!group) {
        const when = action.when || {};
        group = {
          scene_name: action.scene_name || '',
          when: {
            scenes: normalizeList(when.scenes),
            windows: normalizeList(when.windows),
          },
          sources: [],
        };
        map.set(key, group);
        groups.push(group);
      }
      group.sources.push({
        enabled: action.enabled !== false,
        source_name: action.source_name || '',
        scene_item_id: action.scene_item_id == null ? null : action.scene_item_id,
        then: action.then && typeof action.then === 'object' ? action.then : {},
      });
    });
    return groups.length ? groups : [emptyGroup()];
  }

  function visibilityOf(then) {
    if (!then) return 'leave';
    if (then.visible === true) return 'show';
    if (then.visible === false) return 'hide';
    return 'leave';
  }

  function transformOf(then) {
    const xf = (then && then.transform) || {};
    return {
      on: !!(xf && Object.keys(xf).length),
      values: xf,
    };
  }

  function filterOf(then) {
    const list = (then && Array.isArray(then.filters)) ? then.filters : [];
    const first = list.find((f) => f && f.name && f.enabled != null) || {};
    let vis = 'leave';
    if (first.enabled === true) vis = 'show';
    if (first.enabled === false) vis = 'hide';
    return {
      name: first.name || '',
      vis,
      rest: list.filter((f) => f && f !== first && f.name),
    };
  }

  function whenLabel(scenes, windows) {
    const parts = [];
    (scenes || []).forEach((id) => parts.push(SCENE_LABEL.get(id) || id));
    (windows || []).forEach((id) => parts.push(WINDOW_LABEL.get(id) || id));
    return parts.length ? parts.join(' or ') : 'pick a scene or window';
  }

  function collectThen(row, adv) {
    const then = {};
    const vis = row.querySelector('[data-visibility]')?.value || 'leave';
    if (vis === 'hide') then.visible = false;
    if (vis === 'show') then.visible = true;
    const xfRoot = adv || row;
    if (xfRoot.querySelector('[data-transform-on]')?.checked) {
      const transform = {};
      TRANSFORM_FIELDS.forEach(([key]) => {
        const input = xfRoot.querySelector('[data-xf="' + key + '"]');
        if (!input || input.value === '') return;
        const n = parseFloat(input.value);
        if (!Number.isNaN(n)) transform[key] = n;
      });
      if (Object.keys(transform).length) then.transform = transform;
    }
    const filters = [];
    const filterName = row.querySelector('[data-filter-name]')?.value.trim() || '';
    const filterVis = row.querySelector('[data-filter-vis]')?.value || 'leave';
    if (filterName && filterVis === 'hide') filters.push({ name: filterName, enabled: false });
    if (filterName && filterVis === 'show') filters.push({ name: filterName, enabled: true });
    const restRaw = row.getAttribute('data-filters-rest');
    if (restRaw) {
      try {
        const rest = JSON.parse(restRaw);
        if (Array.isArray(rest)) {
          rest.forEach((f) => {
            if (f && f.name && !filters.some((x) => x.name === f.name)) filters.push(f);
          });
        }
      } catch (e) { /* first filter still saves */ }
    }
    if (filters.length) then.filters = filters;
    return then;
  }

  function collect() {
    const el = root();
    if (!el) return loaded;
    const actions = [];
    el.querySelectorAll('.obs-ui-group').forEach((card) => {
      const whenScenes = [...card.querySelectorAll('input[data-when-scene]')]
        .filter((i) => i.checked)
        .map((i) => i.value);
      const extra = (card.querySelector('[data-extra-scenes]')?.value || '')
        .split(',')
        .map((s) => s.trim().toLowerCase())
        .filter(Boolean);
      extra.forEach((s) => {
        if (!whenScenes.includes(s)) whenScenes.push(s);
      });
      const whenWindows = [...card.querySelectorAll('input[data-when-window]')]
        .filter((i) => i.checked)
        .map((i) => i.value);
      const sceneName = card.querySelector('[data-scene-name]')?.value.trim() || '';
      card.querySelectorAll('.obs-ui-source').forEach((row) => {
        const adv = row.nextElementSibling && row.nextElementSibling.classList.contains('obs-ui-src-adv')
          ? row.nextElementSibling
          : null;
        const itemRaw = (adv && adv.querySelector('[data-item-id]')?.value.trim()) || '';
        actions.push({
          enabled: !!row.querySelector('[data-rule-enabled]')?.checked,
          scene_name: sceneName,
          source_name: row.querySelector('[data-source-name]')?.value.trim() || '',
          scene_item_id: itemRaw === '' ? null : itemRaw,
          when: { scenes: whenScenes, windows: whenWindows },
          then: collectThen(row, adv),
        });
      });
    });
    return {
      enabled: !!el.querySelector('#obsUiMasterEnabled')?.checked,
      obs_ws_url: el.querySelector('#obsUiWsUrl')?.value.trim() || 'ws://127.0.0.1:4456',
      actions,
    };
  }

  function chipHtml(kind, id, label, checked) {
    return (
      '<label class="obs-ui-chip"><input type="checkbox" data-when-' + kind + ' value="' + id + '"' +
      (checked ? ' checked' : '') + '> ' + label + '</label>'
    );
  }

  function sourceRowHtml(src, srcIdx, groupIdx) {
    const then = src.then || {};
    const vis = visibilityOf(then);
    const xf = transformOf(then);
    const filt = filterOf(then);
    const hasAdvanced = src.scene_item_id != null || xf.on;
    const xfInputs = TRANSFORM_FIELDS.map(([key, label]) =>
      '<label class="obs-ui-xf">' + label +
      ' <input type="number" step="any" data-xf="' + key + '" value="' +
      (xf.values[key] != null ? xf.values[key] : '') + '"></label>'
    ).join('');
    const restAttr = filt.rest.length
      ? ' data-filters-rest="' + escapeAttr(JSON.stringify(filt.rest)) + '"'
      : '';
    return (
      '<tr class="obs-ui-source" data-src="' + srcIdx + '"' + restAttr + '>' +
        '<td class="obs-ui-src-on"><input type="checkbox" data-rule-enabled title="Include this source"' +
          (src.enabled !== false ? ' checked' : '') + '></td>' +
        '<td><input type="text" data-source-name placeholder="INGAME - INV" value="' +
          escapeAttr(src.source_name) + '"></td>' +
        '<td><select data-visibility>' +
          '<option value="hide"' + (vis === 'hide' ? ' selected' : '') + '>Hide</option>' +
          '<option value="show"' + (vis === 'show' ? ' selected' : '') + '>Show</option>' +
          '<option value="leave"' + (vis === 'leave' ? ' selected' : '') + '>Leave</option>' +
        '</select></td>' +
        '<td><input type="text" data-filter-name placeholder="Image Mask/Blend" value="' +
          escapeAttr(filt.name) + '"></td>' +
        '<td><select data-filter-vis>' +
          '<option value="leave"' + (filt.vis === 'leave' ? ' selected' : '') + '>Leave</option>' +
          '<option value="hide"' + (filt.vis === 'hide' ? ' selected' : '') + '>Hide</option>' +
          '<option value="show"' + (filt.vis === 'show' ? ' selected' : '') + '>Show</option>' +
        '</select></td>' +
        '<td class="obs-ui-src-actions">' +
          '<button type="button" class="obs-ui-mini" data-toggle-adv="' + groupIdx + '-' + srcIdx + '">' +
            (hasAdvanced ? 'Advanced ▾' : 'Advanced') + '</button>' +
          '<button type="button" class="obs-ui-mini obs-ui-mini-danger" data-del-src="' + srcIdx + '">Remove</button>' +
        '</td>' +
      '</tr>' +
      '<tr class="obs-ui-src-adv" data-adv="' + groupIdx + '-' + srcIdx + '"' +
        (hasAdvanced ? '' : ' hidden') + '>' +
        '<td></td><td colspan="5">' +
          '<div class="obs-ui-adv-grid">' +
            '<label>Item id <input type="number" data-item-id placeholder="auto" value="' +
              (src.scene_item_id != null ? src.scene_item_id : '') + '"></label>' +
            '<label class="obs-ui-xf-toggle"><input type="checkbox" data-transform-on' +
              (xf.on ? ' checked' : '') + '> Also transform</label>' +
          '</div>' +
          '<div class="obs-ui-xf-row"' + (xf.on ? '' : ' hidden') + '>' + xfInputs +
            '<span class="sub" style="margin:0;">Else restores the transform captured on first apply.</span>' +
          '</div>' +
        '</td>' +
      '</tr>'
    );
  }

  function groupHtml(group, idx) {
    const knownScene = new Set(UI_SCENES.map(([id]) => id));
    const scenes = group.when.scenes || [];
    const windows = group.when.windows || [];
    const extras = scenes.filter((s) => !knownScene.has(s)).join(', ');
    const sceneBoxes = UI_SCENES.map(([id, label]) =>
      chipHtml('scene', id, label, scenes.includes(id))
    ).join('');
    const windowBoxes = UI_WINDOWS.map(([id, label]) =>
      chipHtml('window', id, label, windows.includes(id))
    ).join('');
    const rows = (group.sources || []).map((src, srcIdx) =>
      sourceRowHtml(src, srcIdx, idx)
    ).join('');
    return (
      '<article class="obs-ui-rule obs-ui-group" data-group="' + idx + '">' +
        '<div class="obs-ui-rule-head">' +
          '<strong>When: ' + escapeAttr(whenLabel(scenes, windows)) + '</strong>' +
          '<button type="button" class="obs-ui-mini obs-ui-mini-danger" data-del-group="' + idx + '">Remove group</button>' +
        '</div>' +
        '<label class="obs-ui-scene">OBS scene <input type="text" data-scene-name placeholder="V01 LIVE - MAIN" value="' +
          escapeAttr(group.scene_name) + '"></label>' +
        '<p class="obs-ui-if">If any checked scene <em>or</em> window is active</p>' +
        '<div class="obs-ui-chips">' + sceneBoxes + '</div>' +
        '<div class="obs-ui-chips">' + windowBoxes + '</div>' +
        '<details class="obs-ui-advanced"' + (extras ? ' open' : '') + '>' +
          '<summary>Other scenes</summary>' +
          '<input type="text" data-extra-scenes placeholder="about, surface, amulet" value="' +
            escapeAttr(extras) + '">' +
        '</details>' +
        '<table class="obs-ui-table">' +
          '<thead><tr>' +
            '<th class="obs-ui-src-on">On</th>' +
            '<th>Source</th>' +
            '<th>Visibility</th>' +
            '<th>Filter</th>' +
            '<th>Filter vis</th>' +
            '<th></th>' +
          '</tr></thead>' +
          '<tbody>' + rows + '</tbody>' +
        '</table>' +
        '<button type="button" class="obs-ui-mini" data-add-source="' + idx + '">Add source</button>' +
      '</article>'
    );
  }

  function render() {
    const el = root();
    if (!el) return;
    const groups = toGroups(loaded.actions);
    el.innerHTML =
      '<div class="row"><label><input type="checkbox" id="obsUiMasterEnabled"' +
      (loaded.enabled ? ' checked' : '') + '> Apply If/Then rules to OBS</label></div>' +
      '<div class="row"><label for="obsUiWsUrl">OBS WebSocket</label>' +
      '<input type="text" id="obsUiWsUrl" style="width:min(100%,260px);" value="' +
      escapeAttr(loaded.obs_ws_url || 'ws://127.0.0.1:4456') + '"></div>' +
      '<div id="obsUiRules">' + groups.map(groupHtml).join('') + '</div>' +
      '<div class="row" style="margin-top:0.6rem;gap:0.5rem;flex-wrap:wrap;">' +
        '<button type="button" id="obsUiAdd">Add group</button>' +
        '<button type="button" id="obsUiSave">Save OBS rules</button>' +
        '<button type="button" id="obsUiReload" style="background:var(--spd-btn-secondary);">Reload</button>' +
      '</div>';

    el.querySelector('#obsUiAdd')?.addEventListener('click', () => {
      loaded = collect();
      const next = toGroups(loaded.actions);
      next.push(emptyGroup());
      loaded.actions = flattenGroups(next);
      render();
      setDirty(true);
    });
    el.querySelector('#obsUiSave')?.addEventListener('click', save);
    el.querySelector('#obsUiReload')?.addEventListener('click', () => load(true));
    el.querySelectorAll('[data-del-group]').forEach((btn) => {
      btn.addEventListener('click', () => {
        loaded = collect();
        const groups = toGroups(loaded.actions);
        groups.splice(parseInt(btn.getAttribute('data-del-group'), 10), 1);
        loaded.actions = flattenGroups(groups);
        if (!loaded.actions.length) loaded.actions = [emptyAction()];
        render();
        setDirty(true);
      });
    });
    el.querySelectorAll('[data-add-source]').forEach((btn) => {
      btn.addEventListener('click', () => {
        loaded = collect();
        const groups = toGroups(loaded.actions);
        const idx = parseInt(btn.getAttribute('data-add-source'), 10);
        if (!groups[idx]) return;
        groups[idx].sources.push(emptySource());
        loaded.actions = flattenGroups(groups);
        render();
        setDirty(true);
      });
    });
    el.querySelectorAll('[data-del-src]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const card = btn.closest('.obs-ui-group');
        const groupIdx = parseInt(card && card.getAttribute('data-group'), 10);
        const srcIdx = parseInt(btn.getAttribute('data-del-src'), 10);
        loaded = collect();
        const groups = toGroups(loaded.actions);
        if (!groups[groupIdx]) return;
        groups[groupIdx].sources.splice(srcIdx, 1);
        if (!groups[groupIdx].sources.length) groups.splice(groupIdx, 1);
        loaded.actions = flattenGroups(groups);
        if (!loaded.actions.length) loaded.actions = [emptyAction()];
        render();
        setDirty(true);
      });
    });
    el.querySelectorAll('[data-toggle-adv]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const key = btn.getAttribute('data-toggle-adv');
        const adv = el.querySelector('[data-adv="' + key + '"]');
        if (!adv) return;
        adv.hidden = !adv.hidden;
        btn.textContent = adv.hidden ? 'Advanced' : 'Advanced ▾';
      });
    });
    el.querySelectorAll('[data-transform-on]').forEach((box) => {
      box.addEventListener('change', () => {
        const row = box.closest('.obs-ui-src-adv')?.querySelector('.obs-ui-xf-row');
        if (row) row.hidden = !box.checked;
        setDirty(true);
      });
    });
    if (!el.dataset.bound) {
      el.dataset.bound = '1';
      el.addEventListener('input', () => setDirty(true));
      el.addEventListener('change', () => setDirty(true));
    }
  }

  function flattenGroups(groups) {
    const actions = [];
    (groups || []).forEach((group) => {
      (group.sources || []).forEach((src) => {
        actions.push({
          enabled: src.enabled !== false,
          scene_name: group.scene_name || '',
          source_name: src.source_name || '',
          scene_item_id: src.scene_item_id == null ? null : src.scene_item_id,
          when: {
            scenes: normalizeList(group.when && group.when.scenes),
            windows: normalizeList(group.when && group.when.windows),
          },
          then: src.then && typeof src.then === 'object' ? src.then : {},
        });
      });
    });
    return actions;
  }

  function escapeAttr(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/"/g, '&quot;')
      .replace(/</g, '&lt;');
  }

  function applyLoaded(data) {
    loaded = {
      enabled: !!data.enabled,
      obs_ws_url: data.obs_ws_url || 'ws://127.0.0.1:4456',
      actions: Array.isArray(data.actions) ? data.actions : [],
    };
    if (!loaded.actions.length) loaded.actions.push(emptyAction());
  }

  function load(showStatus) {
    return fetch('/api/obs-ui-actions')
      .then((r) => {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.json();
      })
      .then((data) => {
        if (data && data.error) throw new Error(data.error);
        applyLoaded(data);
        render();
        setDirty(false);
        if (showStatus && global.showMsg) global.showMsg('OBS rules reloaded', true);
        return loaded;
      })
      .catch((e) => {
        console.warn('obs-ui-actions load', e);
        if (!loaded.actions.length) loaded.actions.push(emptyAction());
        render();
        if (showStatus && global.showMsg) global.showMsg('OBS rules load failed: ' + e.message, false);
      });
  }

  function save() {
    const payload = collect();
    return fetch('/api/obs-ui-actions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })
      .then((r) => r.json())
      .then((data) => {
        if (data.error) throw new Error(data.error);
        if (data.config) applyLoaded(data.config);
        render();
        setDirty(false);
        if (global.showMsg) global.showMsg('OBS rules saved', true);
        if (global.logActivity) global.logActivity('OBS ui_state rules saved');
        return true;
      })
      .catch((e) => {
        if (global.showMsg) global.showMsg('OBS rules save failed: ' + e.message, false);
        return false;
      });
  }

  function init() {
    if (!root()) return;
    if (!loaded.actions.length) loaded.actions.push(emptyAction());
    render();
    load(false);
  }

  global.ObsUiActionsPanel = { init: init, load: load, save: save, isDirty: function () { return dirty; } };
})(window);
