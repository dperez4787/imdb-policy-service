// IMDb Graph Governance admin UI. No framework, no build step: the policy
// service serves these files and the same-origin API. Read surfaces work
// without credentials; mutations, the audit log, and registration detail
// need the admin token (sessionStorage only).

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const esc = s => String(s ?? '').replace(/[&<>"']/g,
  c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const state = {
  get token() { return sessionStorage.getItem('adminToken') || ''; },
  set token(v) { v ? sessionStorage.setItem('adminToken', v) : sessionStorage.removeItem('adminToken'); },
  view: 'fields',
  personas: [],
  schemaCache: null,
};

async function api(path, { method = 'GET', body, admin = false } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (admin && state.token) {
    headers['Authorization'] = `Bearer ${state.token}`;
    headers['X-Admin-Actor'] = 'admin-ui';
  }
  const res = await fetch(path, { method, headers, body: body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* non-JSON error body */ }
  if (!res.ok && !(path === '/v1/playground/query')) {
    throw new Error(json?.error || json?.message || `${res.status} on ${path}`);
  }
  return { status: res.status, json, headers: res.headers };
}

function toast(msg, isError = false) {
  const t = $('#toast');
  t.textContent = msg;
  t.className = 'toast' + (isError ? ' error' : '');
  t.hidden = false;
  clearTimeout(t._timer);
  t._timer = setTimeout(() => { t.hidden = true; }, 3500);
}

const fmtTime = iso => iso ? new Date(iso).toLocaleString() : '—';

// ---------------------------------------------------------------- revision
async function refreshRevision() {
  try {
    const { json } = await api('/v1/bundle');
    $('#rev-chip').textContent = `bundle rev ${json.revision}`;
  } catch { $('#rev-chip').textContent = 'bundle rev ?'; }
}

// ------------------------------------------------------------------ fields
async function viewFields(main) {
  main.innerHTML = `<div class="view-head">
    <h2>Governed fields</h2>
    <p class="dim">Every coordinate a subgraph declared with <span class="coord">@governed</span>.
    Ungoverned fields flow freely (allow-unless-governed); a governed field with no roles denies everyone.
    Grants take effect at the router within one poll interval — no deploy.</p>
  </div>`;

  if (!state.token) {
    const { json: bundle } = await api('/v1/bundle');
    main.insertAdjacentHTML('beforeend', `
      <div class="notice">Read-only view from the public bundle. <b>Set the admin token</b> (top right) to edit grants and see declaration detail.</div>
      <div class="card"><div class="tbl-wrap"><table>
        <tr><th>Coordinate</th><th>Subgraph</th><th>Allowed roles</th></tr>
        ${Object.entries(bundle.fields).map(([coordinate, f]) => `
          <tr><td class="coord">${esc(coordinate)}</td><td>${esc(f.subgraph)}</td>
          <td>${f.allowedRoles.length ? f.allowedRoles.map(r => `<span class="pill active">${esc(r)}</span>`).join(' ')
            : '<span class="pill deny">deny-everyone</span>'}</td></tr>`).join('')}
      </table></div></div>`);
    return;
  }

  const { json: fields } = await api('/v1/admin/overview', { admin: true });
  const card = document.createElement('div');
  card.className = 'card';
  card.innerHTML = `<div class="tbl-wrap"><table>
    <tr><th>Coordinate</th><th>Subgraph</th><th>Status</th><th>Reason</th><th>Allowed roles</th></tr>
  </table></div>`;
  const table = $('table', card);

  for (const f of fields) {
    const tr = document.createElement('tr');
    const roles = [...f.allowedRoles];
    tr.innerHTML = `
      <td class="coord">${esc(f.coordinate)}</td>
      <td>${esc(f.subgraph)}</td>
      <td><span class="pill ${f.status.toLowerCase()}">${f.status.toLowerCase()}</span>
          <span class="pill ${esc(f.source)}">${esc(f.source)}</span></td>
      <td class="dim">${esc(f.reason || '—')}<div class="meta">registered ${fmtTime(f.lastRegisteredAt)}</div></td>
      <td>
        <div class="role-editor"></div>
        <div class="row-actions">
          <button class="primary small save">Save</button>
          <button class="ghost small clear" ${f.allowedRoles.length || f.policyEnabled ? '' : 'disabled'}>Revoke all</button>
        </div>
        <div class="meta">${f.policyUpdatedBy ? `policy by ${esc(f.policyUpdatedBy)} · ${fmtTime(f.policyUpdatedAt)}` : 'no policy yet — deny-everyone'}</div>
      </td>`;

    const editor = $('.role-editor', tr);
    const renderChips = () => {
      editor.innerHTML = roles.map(r =>
        `<span class="role-chip">${esc(r)}<button data-role="${esc(r)}" title="remove">×</button></span>`).join('')
        + `<span class="role-add"><input placeholder="add role…"></span>`;
      $$('.role-chip button', editor).forEach(b => b.onclick = () => {
        roles.splice(roles.indexOf(b.dataset.role), 1); renderChips();
      });
      const input = $('input', editor);
      input.onkeydown = e => {
        if (e.key === 'Enter' && input.value.trim()) {
          if (!roles.includes(input.value.trim())) roles.push(input.value.trim());
          renderChips();
        }
      };
    };
    renderChips();

    $('.save', tr).onclick = async () => {
      try {
        await api(`/v1/admin/policies/${f.coordinate}`, { method: 'PUT', admin: true, body: { allowedRoles: roles, enabled: true } });
        toast(`Saved ${f.coordinate} → [${roles.join(', ') || 'deny-everyone'}]`);
        refreshRevision();
      } catch (e) { toast(e.message, true); }
    };
    $('.clear', tr).onclick = async () => {
      try {
        await api(`/v1/admin/policies/${f.coordinate}`, { method: 'DELETE', admin: true });
        toast(`Revoked all grants on ${f.coordinate}`);
        render();
      } catch (e) { toast(e.message, true); }
    };
    table.appendChild(tr);
  }
  main.appendChild(card);
}

// --------------------------------------------------------------- subgraphs
async function viewSubgraphs(main) {
  main.innerHTML = `<div class="view-head">
    <h2>Subgraph declarations</h2>
    <p class="dim">What each subgraph's schema declares via <span class="coord">@governed</span>, exactly as
    registered at startup. The schema owns <em>what</em> is governed; the policy service owns <em>who</em> may read it.
    Orphaned = declared once, missing from the latest registration.</p>
  </div>`;

  let rows;
  if (state.token) {
    ({ json: rows } = await api('/v1/admin/overview', { admin: true }));
  } else {
    const { json: bundle } = await api('/v1/bundle');
    rows = Object.entries(bundle.fields).map(([coordinate, f]) =>
      ({ coordinate, subgraph: f.subgraph, status: 'ACTIVE', source: 'bundle', reason: null }));
    main.insertAdjacentHTML('beforeend',
      '<div class="notice">Limited view from the public bundle — set the admin token for reasons, timestamps, and orphans.</div>');
  }

  const bySubgraph = {};
  rows.forEach(f => (bySubgraph[f.subgraph] ??= []).push(f));
  for (const [subgraph, fields] of Object.entries(bySubgraph).sort()) {
    main.insertAdjacentHTML('beforeend', `<div class="card">
      <div class="subgraph-head"><h3>subgraph-${esc(subgraph)}</h3>
        <span class="meta">${fields.length} governed field${fields.length === 1 ? '' : 's'}</span></div>
      ${fields.map(f => `
        <div class="field-row ${f.status === 'ORPHANED' ? '' : 'governed'}">
          <span class="fname">${esc(f.coordinate)}</span>
          <span class="ftype">${esc(f.reason || '')}</span>
          <span class="pill ${f.status.toLowerCase()}">${f.status.toLowerCase()}</span>
          ${f.lastRegisteredAt ? `<span class="meta">${fmtTime(f.lastRegisteredAt)}</span>` : ''}
        </div>`).join('')}
    </div>`);
  }
}

// -------------------------------------------------------------- playground
const CANNED = {
  'Ratings — governed numVotes':
    '{ title(tconst: "tt0944947") {\n    primaryTitle\n    rating { averageRating numVotes }\n  } }',
  'Names — governed PII (birthYear, deathYear)':
    '{ name(nconst: "nm0000233") {\n    primaryName\n    birthYear\n    deathYear\n  } }',
  'Cross-subgraph — title → directors → birthYear':
    '{ title(tconst: "tt0944947") {\n    primaryTitle\n    directors { primaryName birthYear }\n  } }',
};

async function mint(persona) {
  const { json } = await api('/v1/token', { method: 'POST', body: { persona } });
  return json;
}

function highlight(jsonText) {
  return esc(jsonText)
    .replace(/PERMISSION_DENIED/g, '<span class="denied">PERMISSION_DENIED</span>')
    .replace(/&quot;(data)&quot;:/g, '&quot;<span class="ok">$1</span>&quot;:');
}

async function viewPlayground(main) {
  if (!state.personas.length) {
    state.personas = (await api('/v1/personas')).json;
  }
  const personaOptions = sel => state.personas.map(p =>
    `<option value="${esc(p.id)}" ${p.id === sel ? 'selected' : ''}>${esc(p.displayName || p.id)} (${p.roles.join(', ')})</option>`).join('');

  main.innerHTML = `<div class="view-head">
    <h2>Playground</h2>
    <p class="dim">Same query, two personas, against the <em>real router</em>. Tokens are minted here and the
    operation is proxied same-origin; the router's decision and bundle revision come back untouched.</p>
  </div>
  <div class="pg-controls">
    <select id="canned">${Object.keys(CANNED).map(k => `<option>${esc(k)}</option>`).join('')}</select>
    <button class="primary" id="run">Run as both personas</button>
  </div>
  <textarea class="query" id="query">${esc(Object.values(CANNED)[0])}</textarea>
  <div class="pg-grid">
    ${['A', 'B'].map((side, i) => `
      <div class="card">
        <div class="pg-col-head">
          <select data-side="${side}">${personaOptions(state.personas[Math.min(i, state.personas.length - 1)]?.id)}</select>
          <span class="roles" id="roles-${side}"></span>
        </div>
        <pre class="result" id="result-${side}">—</pre>
        <div class="meta" id="meta-${side}"></div>
      </div>`).join('')}
  </div>`;

  $('#canned').onchange = e => { $('#query').value = CANNED[e.target.value]; };
  $('#run').onclick = async () => {
    const query = $('#query').value;
    await Promise.all(['A', 'B'].map(async side => {
      const persona = $(`select[data-side=${side}]`).value;
      const result = $(`#result-${side}`);
      result.textContent = '…';
      try {
        const minted = await mint(persona);
        $(`#roles-${side}`).textContent = `roles: ${minted.roles.join(', ')}`;
        const res = await api('/v1/playground/query', { method: 'POST', body: { token: minted.token, query } });
        result.innerHTML = highlight(JSON.stringify(res.json, null, 2));
        const rev = res.headers.get('X-Imdb-Policy-Revision');
        $(`#meta-${side}`).textContent = `HTTP ${res.status}${rev ? ` · decided by bundle revision ${rev}` : ''}`;
      } catch (e) {
        result.textContent = e.message;
      }
    }));
  };
}

// ------------------------------------------------------------------ schema
const INTROSPECTION = `{ __schema { types { kind name description fields { name description
  type { kind name ofType { kind name ofType { kind name ofType { kind name } } } } } } } }`;

function typeName(t) {
  if (!t) return '?';
  if (t.kind === 'NON_NULL') return typeName(t.ofType) + '!';
  if (t.kind === 'LIST') return '[' + typeName(t.ofType) + ']';
  return t.name || '?';
}

async function viewSchema(main) {
  main.innerHTML = `<div class="view-head">
    <h2>Live schema</h2>
    <p class="dim">The federated supergraph, introspected from the router <em>at runtime</em> — always the schema
    that's actually serving. Highlighted fields are governed (from subgraph <span class="coord">@governed</span>
    registrations); everything else is open by posture. Governance can only attach to declared fields — annotate
    the owning subgraph's schema to make a field governable.</p>
  </div>
  <div class="schema-toolbar"><input id="schema-filter" placeholder="filter types and fields…"></div>
  <div id="schema-body"><div class="dim">Introspecting via the router…</div></div>`;

  try {
    if (!state.schemaCache) {
      if (!state.personas.length) state.personas = (await api('/v1/personas')).json;
      const minted = await mint(state.personas[0].id);
      const res = await api('/v1/playground/query', { method: 'POST', body: { token: minted.token, query: INTROSPECTION } });
      if (!res.json?.data) throw new Error('introspection failed: ' + JSON.stringify(res.json?.errors?.[0]?.message || res.status));
      state.schemaCache = res.json.data.__schema.types
        .filter(t => t.kind === 'OBJECT' && !t.name.startsWith('__') && !['_Service'].includes(t.name) && t.fields)
        .sort((a, b) => (a.name === 'Query' ? -1 : b.name === 'Query' ? 1 : a.name.localeCompare(b.name)));
    }
    const { json: bundle } = await api('/v1/bundle');

    const renderTypes = filter => {
      const q = (filter || '').toLowerCase();
      $('#schema-body').innerHTML = state.schemaCache.map(t => {
        const fields = t.fields.filter(f =>
          !q || t.name.toLowerCase().includes(q) || f.name.toLowerCase().includes(q));
        if (!fields.length) return '';
        return `<div class="card type-card">
          <h3>${esc(t.name)} <span class="kind">OBJECT</span></h3>
          ${fields.map(f => {
            const coordinate = `${t.name}.${f.name}`;
            const gov = bundle.fields[coordinate];
            return `<div class="field-row ${gov ? 'governed' : ''}">
              <span class="fname">${esc(f.name)}</span>
              <span class="ftype">${esc(typeName(f.type))}</span>
              ${gov ? (gov.allowedRoles.length
                ? gov.allowedRoles.map(r => `<span class="pill active">${esc(r)}</span>`).join(' ')
                : '<span class="pill deny">deny-everyone</span>')
                : '<span class="pill open">open</span>'}
            </div>`;
          }).join('')}
        </div>`;
      }).join('');
    };
    renderTypes('');
    $('#schema-filter').oninput = e => renderTypes(e.target.value);
  } catch (e) {
    $('#schema-body').innerHTML = `<div class="notice">${esc(e.message)}</div>`;
  }
}

// ---------------------------------------------------------------- personas
function chipEditor(container, items, placeholder) {
  const render = () => {
    container.innerHTML = items.map(v =>
      `<span class="role-chip">${esc(v)}<button data-v="${esc(v)}" title="remove">×</button></span>`).join('')
      + `<span class="role-add"><input placeholder="${esc(placeholder)}"></span>`;
    $$('.role-chip button', container).forEach(b => b.onclick = () => {
      items.splice(items.indexOf(b.dataset.v), 1); render();
    });
    const input = $('input', container);
    input.onkeydown = e => {
      if (e.key === 'Enter' && input.value.trim()) {
        if (!items.includes(input.value.trim())) items.push(input.value.trim());
        render();
      }
    };
  };
  render();
}

async function viewPersonas(main) {
  main.innerHTML = `<div class="view-head">
    <h2>Personas</h2>
    <p class="dim">Who is who. A signed-in Google user's role = the persona whose <b>subjects</b> list contains
    their email — map an email here and the router applies the new roles within one poll interval. A user mapped
    nowhere has <em>no roles</em> (not even <span class="coord">public</span>) and sees only ungoverned fields.
    Personas also mint playground tokens carrying their roles directly.</p>
  </div>`;
  if (!state.token) {
    main.insertAdjacentHTML('beforeend', '<div class="notice">Managing personas requires the admin token.</div>');
    return;
  }

  const { json: personas } = await api('/v1/admin/personas', { admin: true });

  const savePersona = async (id, displayName, roles, subjects) => {
    try {
      await api(`/v1/admin/personas/${encodeURIComponent(id)}`, {
        method: 'PUT', admin: true,
        body: { displayName, roles, subjects },
      });
      toast(`Saved persona ${id}`);
      refreshRevision();
      state.personas = []; // playground picker refreshes next visit
    } catch (e) { toast(e.message, true); }
  };

  for (const p of personas) {
    const roles = [...(p.roles || [])];
    const subjects = [...(p.subjects || [])];
    const card = document.createElement('div');
    card.className = 'card';
    card.innerHTML = `
      <div class="subgraph-head"><h3>${esc(p.id)}</h3>
        <input class="display-name" value="${esc(p.displayName || '')}" placeholder="display name"
               style="font:inherit;font-size:13px;padding:4px 8px;border:1px solid var(--line);border-radius:6px;background:var(--surface);color:var(--ink)"></div>
      <div class="meta" style="margin-bottom:4px">roles (carried in minted tokens)</div>
      <div class="role-editor roles-editor" style="margin-bottom:10px"></div>
      <div class="meta" style="margin-bottom:4px">subjects — Google emails mapped to this persona</div>
      <div class="role-editor subjects-editor" style="margin-bottom:10px"></div>
      <div class="row-actions"><button class="primary small save">Save</button></div>`;
    chipEditor($('.roles-editor', card), roles, 'add role…');
    chipEditor($('.subjects-editor', card), subjects, 'add email…');
    $('.save', card).onclick = () =>
      savePersona(p.id, $('.display-name', card).value.trim(), roles, subjects);
    main.appendChild(card);
  }

  const create = document.createElement('div');
  create.className = 'card';
  create.innerHTML = `
    <div class="subgraph-head"><h3>New persona</h3></div>
    <div class="pg-controls">
      <input class="new-id" placeholder="id (kebab-case)">
      <button class="primary small create">Create</button>
    </div>`;
  $('.create', create).onclick = async () => {
    const id = $('.new-id', create).value.trim();
    if (!id) return;
    await savePersona(id, id, [], []);
    render();
  };
  main.appendChild(create);
}

// ------------------------------------------------------------------- audit
async function viewAudit(main) {
  main.innerHTML = `<div class="view-head">
    <h2>Audit trail</h2>
    <p class="dim">Append-only record of everything that changed the bundle: seeds, subgraph registrations,
    and admin policy decisions — each stamped with the revision it produced.</p>
  </div>`;
  if (!state.token) {
    main.insertAdjacentHTML('beforeend', '<div class="notice">The audit log requires the admin token.</div>');
    return;
  }
  const { json: entries } = await api('/v1/admin/audit?limit=100', { admin: true });
  main.insertAdjacentHTML('beforeend', `<div class="card"><div class="tbl-wrap"><table>
    <tr><th>When</th><th>Actor</th><th>Action</th><th>Target</th><th>Detail</th><th>Rev</th></tr>
    ${entries.map(e => `<tr>
      <td class="meta">${fmtTime(e.at)}</td>
      <td class="coord">${esc(e.actor)}</td>
      <td><span class="pill ${e.action.startsWith('registration') ? 'registration' : e.action.startsWith('seed') ? 'seed' : 'active'}">${esc(e.action)}</span></td>
      <td class="coord">${esc(e.target)}</td>
      <td class="dim">${esc(e.detail || '')}</td>
      <td class="meta">${e.revision}</td></tr>`).join('')}
  </table></div></div>`);
}

// ------------------------------------------------------------------- shell
const VIEWS = { fields: viewFields, subgraphs: viewSubgraphs, playground: viewPlayground, schema: viewSchema, personas: viewPersonas, audit: viewAudit };

async function render() {
  const main = $('#main');
  try {
    await VIEWS[state.view](main);
  } catch (e) {
    main.innerHTML = `<div class="notice">${esc(e.message)}</div>`;
  }
}

function initShell() {
  // JS-owned tabs are injected here so index.html stays stable if JS fails.
  const tabs = $('#tabs');
  const auditTab = $$('.tab', tabs).find(t => t.dataset.view === 'audit');
  for (const [view, label] of [['schema', 'Schema'], ['personas', 'Personas']]) {
    const tab = document.createElement('button');
    tab.className = 'tab';
    tab.dataset.view = view;
    tab.textContent = label;
    tabs.insertBefore(tab, view === 'schema' ? $$('.tab', tabs)[2] : auditTab);
  }

  $$('.tab').forEach(tab => tab.onclick = () => {
    $$('.tab').forEach(t => t.classList.toggle('active', t === tab));
    state.view = tab.dataset.view;
    render();
  });

  const tokenBtn = $('#token-btn');
  const syncTokenBtn = () => {
    tokenBtn.textContent = state.token ? 'Admin token set ✓' : 'Set admin token';
    tokenBtn.classList.toggle('authed', !!state.token);
  };
  tokenBtn.onclick = () => {
    $('#token-input').value = state.token;
    $('#token-dialog').showModal();
  };
  $('#token-dialog').onclose = () => {
    const v = $('#token-dialog').returnValue;
    if (v === 'save') state.token = $('#token-input').value.trim();
    if (v === 'clear') state.token = '';
    syncTokenBtn();
    render();
  };
  syncTokenBtn();

  refreshRevision();
  setInterval(refreshRevision, 15000);
  render();
}

initShell();
