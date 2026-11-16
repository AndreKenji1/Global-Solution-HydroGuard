'use strict';

const $ = (id) => document.getElementById(id);
const STATUS = {ABERTA:'Aberta', EM_ANALISE:'Em análise', EM_ATENDIMENTO:'Em atendimento', RESOLVIDA:'Resolvida'};
const PRIORITY = {ALTA:'Alta', MEDIA:'Média', BAIXA:'Baixa'};
const ANALYSIS = {IA_GEMINI:'IA Gemini · sugestão textual', REGRAS:'Automática por regras', REGRAS_CONTINGENCIA:'Automática por regras · IA indisponível'};
const pages = {inicio:'Visão geral', nova:'Nova ocorrência', ocorrencias:'Acompanhar', gestao:'Gestão'};
const state = {history:new Map(), user:null, csrf:null, page:0, totalPages:0, adminRows:[], previewUrl:null, photoValid:false, adminRequest:0};
let toastTimer;

function node(tag, cls, text) {
  const element = document.createElement(tag);
  if (cls) element.className = cls;
  if (text != null) element.textContent = text;
  return element;
}
function feedback(id, message='', error=false) {
  const element = $(id);
  element.textContent = message;
  element.hidden = !message;
  element.classList.toggle('error', error);
}
function toast(message, error=false) {
  const element = $('toast');
  element.textContent = message;
  element.className = 'toast show' + (error ? ' error' : '');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {element.className = 'toast';}, 6500);
}
async function csrf() {
  const response = await fetch('/api/auth/csrf', {credentials:'same-origin', cache:'no-store'});
  if (!response.ok) throw new Error('Não foi possível iniciar uma sessão segura. Atualize a página.');
  state.csrf = await response.json();
  return state.csrf;
}
async function api(path, options={}) {
  const method = (options.method || 'GET').toUpperCase();
  const headers = new Headers(options.headers || {});
  if (!['GET','HEAD','OPTIONS'].includes(method)) {
    const token = state.csrf || await csrf();
    headers.set(token.headerName || 'X-CSRF-TOKEN', token.token);
  }
  let response;
  try {
    response = await fetch('/api' + path, {...options, headers, credentials:'same-origin', cache:'no-store'});
  } catch {
    throw new Error('Sem conexão com o servidor. Verifique a conexão e tente novamente.');
  }
  let data = null;
  const body = await response.text();
  try {data = body ? JSON.parse(body) : null;} catch { /* A mensagem padrão evita expor uma página interna do servidor. */ }
  if (!response.ok) {
    const error = new Error(data?.message || `Não foi possível concluir a solicitação (HTTP ${response.status}).`);
    error.status = response.status;
    if (response.status === 403) state.csrf = null;
    throw error;
  }
  return data;
}
function formatDate(value) {
  return value ? new Date(value).toLocaleString('pt-BR', {dateStyle:'short', timeStyle:'short'}) : '—';
}
function badge(value, isStatus=false) {
  return node('span', 'pill ' + value, (isStatus ? STATUS : PRIORITY)[value] || value);
}
function analysisBadge(value) {
  return node('span', 'analysis-badge ' + (value === 'IA_GEMINI' ? 'gemini' : 'rules'), ANALYSIS[value] || 'Classificação indisponível');
}
function go(page) {
  if (!pages[page]) page = 'inicio';
  document.querySelectorAll('.page').forEach(element => element.classList.toggle('visible', element.id === 'page-' + page));
  document.querySelectorAll('.nav-item').forEach(button => {
    button.classList.toggle('active', button.dataset.page === page);
    if (button.dataset.page === page) button.setAttribute('aria-current','page');
    else button.removeAttribute('aria-current');
  });
  $('page-crumb').textContent = pages[page];
  history.replaceState(null, '', '#' + page);
  window.scrollTo(0,0);
  if (page === 'inicio') refreshDashboard();
  if (page === 'gestao' && state.user) loadAdmin();
}
async function refreshDashboard() {
  try {
    const dashboard = await api('/dashboard');
    $('stat-total').textContent = dashboard.total;
    $('stat-open').textContent = dashboard.abertas;
    $('stat-high').textContent = dashboard.prioridadeAlta;
    $('stat-done').textContent = dashboard.resolvidas;
    $('api-health').textContent = 'Servidor conectado';
    $('api-health').previousElementSibling.classList.remove('down');
  } catch (error) {
    ['stat-total','stat-open','stat-high','stat-done'].forEach(id => {$(id).textContent = '—';});
    $('api-health').textContent = 'Servidor indisponível';
    $('api-health').previousElementSibling.classList.add('down');
    toast(error.message, true);
  }
}
function detail(label, value, full=false) {
  const wrap = node('div', 'detail-row' + (full ? ' full' : ''));
  wrap.append(node('div','detail-label',label));
  const content = node('div','detail-value');
  if (value instanceof Node) content.append(value);
  else content.textContent = value || '—';
  wrap.append(content);
  return wrap;
}
function details(o, admin=false) {
  const grid = node('div','detail-grid');
  grid.append(detail('Protocolo', o.protocolo, true));
  grid.append(detail('Status',badge(o.status,true)), detail('Prioridade preliminar',badge(o.prioridade)));
  if (admin && o.endereco) grid.append(detail('Endereço',o.endereco,true));
  grid.append(detail('Bairro',o.bairro), detail('Registrado em',formatDate(o.criadoEm)));
  if (admin && o.descricao) grid.append(detail('Descrição',o.descricao,true));
  grid.append(detail('Categoria sugerida',o.categoria), detail('Tipo de análise',analysisBadge(o.tipoAnalise)));
  grid.append(detail('Justificativa sugerida',o.justificativa,true));
  if (admin && o.possuiFoto) {
    const image = node('img','detail-image');
    image.src = '/api/ocorrencias/' + o.id + '/foto';
    image.alt = 'Fotografia do vazamento enviada pela pessoa relatora';
    image.loading = 'lazy';
    image.addEventListener('error', () => {image.replaceWith(node('p','feedback error','Não foi possível carregar a fotografia.'));}, {once:true});
    grid.append(detail('Fotografia',image,true));
  } else if (o.possuiFoto) {
    grid.append(detail('Fotografia','Recebida; acesso restrito à equipe autorizada.',true));
  }
  if (o.historico?.length) {
    const list = node('ol','status-history');
    o.historico.forEach(item => {
      const row = node('li');
      row.append(node('strong','',STATUS[item.status] || item.status), node('span','',formatDate(item.alteradoEm)));
      if (admin && item.autor) row.append(node('small','',item.autor));
      list.append(row);
    });
    grid.append(detail('Histórico de status',list,true));
  }
  grid.append(detail('Última atualização',formatDate(o.atualizadoEm),true));
  return grid;
}
function showDetails(o, admin=false) {
  $('detail-title').textContent = 'Detalhes da ocorrência';
  $('detail-body').replaceChildren(details(o,admin));
  $('detail-dialog').showModal();
}
function remember(o) {
  // O histórico da aba contém apenas o resumo público; endereço, descrição e tokens não são guardados.
  const {protocolo,bairro,status,prioridade,categoria,tipoAnalise,justificativa,possuiFoto,criadoEm,atualizadoEm,historico} = o;
  const entry = {protocolo,bairro,status,prioridade,categoria,tipoAnalise,justificativa,possuiFoto,criadoEm,atualizadoEm,
    historico:historico?.map(({status,alteradoEm}) => ({status,alteradoEm}))};
  state.history.delete(protocolo);
  state.history.set(protocolo,entry);
  if (state.history.size > 30) state.history.delete(state.history.keys().next().value);
  renderHistory();
  if (!$('protocol-result').hidden && $('protocol').value.trim().toLowerCase() === protocolo) {
    $('protocol-result').replaceChildren(details(entry));
  }
}
function emptyList(element, message) {
  element.replaceChildren(node('div','loading-state',message));
}
function record(o, admin=false) {
  const row = node('article','record');
  row.append(node('div','record-mark','⌁'));
  const info = node('div','record-info');
  info.append(node('strong','',admin ? o.endereco : (o.categoria || 'Vazamento urbano')));
  info.append(node('small','record-protocol',o.protocolo));
  info.append(node('small','record-date',`${o.bairro ? o.bairro + ' · ' : ''}${formatDate(o.criadoEm)}`));
  row.append(info);
  const tags = node('div','record-tags');
  tags.append(badge(o.prioridade),badge(o.status,true),analysisBadge(o.tipoAnalise));
  row.append(tags);
  if (admin && state.user?.roles?.includes('ADMIN')) {
    const select = node('select','status-select');
    select.setAttribute('aria-label','Alterar status do protocolo ' + o.protocolo);
    Object.entries(STATUS).forEach(([value,label]) => {
      const option = node('option','',label);option.value = value;select.append(option);
    });
    select.value = o.status;
    select.addEventListener('change', async () => {
      select.disabled = true;
      try {
        const updated = await api(`/ocorrencias/${o.id}/status`, {method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({status:select.value})});
        if (state.history.has(updated.protocolo)) remember(updated);
        toast('Status atualizado com sucesso.');
        await loadAdmin();
        refreshDashboard();
      } catch (error) {
        select.value = o.status;
        feedback('admin-feedback',error.message,true);
        if (error.status === 401) setUser(null);
      } finally {select.disabled = false;}
    });
    row.append(select);
  }
  const open = node('button','record-open','Ver');
  open.type = 'button';
  open.setAttribute('aria-label','Abrir detalhes do protocolo ' + o.protocolo);
  open.addEventListener('click',() => admin ? openAdminDetails(o,open) : lookupProtocol(o.protocolo,true));
  row.append(open);
  return row;
}
async function openAdminDetails(occurrence, button) {
  const request = state.adminRequest;
  const user = state.user;
  const current = () => request === state.adminRequest && user === state.user && location.hash === '#gestao';
  button.disabled = true;
  button.setAttribute('aria-busy','true');
  feedback('admin-feedback','Carregando os detalhes e o histórico da ocorrência…');
  try {
    const full = await api('/ocorrencias/' + occurrence.id);
    if (!current()) return;
    feedback('admin-feedback');
    showDetails(full,true);
  } catch (error) {
    if (!current()) return;
    feedback('admin-feedback',error.message,true);
    if (error.status === 401) {setUser(null);feedback('login-feedback','Sua sessão terminou. Entre novamente.',true);}
  } finally {
    button.disabled = false;
    button.removeAttribute('aria-busy');
  }
}
function renderHistory() {
  const rows = [...state.history.values()].reverse();
  for (const [id,limit] of [['recent-list',4],['all-list',30]]) {
    const element = $(id);
    element.replaceChildren();
    if (!rows.length) emptyList(element,'Nenhum protocolo nesta aba. Registre um vazamento ou consulte um protocolo.');
    else rows.slice(0,limit).forEach(o => element.append(record(o)));
  }
}
async function lookupProtocol(value, openDialog=false) {
  const protocol = value.trim().toLowerCase();
  if (!/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(protocol)) {
    feedback('lookup-feedback','Informe o protocolo completo no formato indicado.',true);
    return;
  }
  $('lookup-protocol').disabled = true;
  $('protocol-result').hidden = true;
  feedback('lookup-feedback','Consultando protocolo…');
  try {
    const response = await api('/ocorrencias/protocolo/' + encodeURIComponent(protocol));
    remember(response);
    $('protocol').value = response.protocolo;
    feedback('lookup-feedback','Ocorrência localizada.');
    $('protocol-result').replaceChildren(details(response));
    $('protocol-result').hidden = false;
    if (openDialog) showDetails(response);
  } catch (error) {
    feedback('lookup-feedback',error.status === 404 ? 'Protocolo não encontrado. Confira o código e tente novamente.' : error.message,true);
    toast(error.message,true);
  } finally {$('lookup-protocol').disabled = false;}
}
function clearPhoto() {
  if (state.previewUrl) URL.revokeObjectURL(state.previewUrl);
  state.previewUrl = null;state.photoValid = false;
  $('foto').value = '';
  $('photo-preview').removeAttribute('src');
  $('photo-preview-wrap').hidden = true;
  $('file-name').textContent = 'Clique para anexar uma imagem';
}
async function previewPhoto() {
  const file = $('foto').files[0];
  if (!file) {clearPhoto();return;}
  if (state.previewUrl) URL.revokeObjectURL(state.previewUrl);
  state.photoValid = false;
  $('photo-preview-wrap').hidden = true;
  if (!['image/jpeg','image/png'].includes(file.type) || file.size > 4*1024*1024 || file.size === 0) {
    clearPhoto();feedback('form-feedback','Selecione uma fotografia JPEG ou PNG válida com até 4 MB.',true);return;
  }
  const url = URL.createObjectURL(file);state.previewUrl = url;
  const image = new Image();
  image.onload = () => {
    if (state.previewUrl !== url) return;
    if (image.width > 4096 || image.height > 4096 || image.width*image.height > 12000000) {
      clearPhoto();feedback('form-feedback','A fotografia deve ter até 4096 pixels por lado e 12 milhões de pixels no total.',true);return;
    }
    state.photoValid = true;
    $('photo-preview').src = url;
    $('photo-preview-wrap').hidden = false;
    $('file-name').textContent = file.name;
    feedback('form-feedback');
  };
  image.onerror = () => {
    if (state.previewUrl !== url) return;
    clearPhoto();feedback('form-feedback','Não foi possível ler a fotografia. Selecione outro JPEG ou PNG.',true);
  };
  image.src = url;
}
async function sendForm(event) {
  event.preventDefault();
  const button = $('submit-report');
  const photo = $('foto').files[0];
  if (photo && !state.photoValid) {feedback('form-feedback','Aguarde a validação da fotografia ou selecione outra imagem.',true);return;}
  const payload = {endereco:$('endereco').value.trim(),bairro:$('bairro').value.trim(),descricao:$('descricao').value.trim()};
  if (payload.descricao.length < 12 || payload.endereco.length < 5) {feedback('form-feedback','Informe uma descrição com ao menos 12 caracteres e um local com ao menos 5 caracteres.',true);return;}
  button.disabled = true;button.textContent = 'Enviando ocorrência…';
  feedback('form-feedback','Registrando o relato e classificando a descrição…');
  try {
    const created = await api('/ocorrencias', {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
    let uploadError = '';
    if (photo) {
      try {
        const data = new FormData();data.append('foto',photo);
        await api(`/ocorrencias/${created.id}/foto`, {method:'POST',headers:{'X-Upload-Token':created.tokenUpload},body:data});
        created.possuiFoto = true;
      } catch (error) {uploadError = ' O relato foi salvo, mas a fotografia não foi recebida: ' + error.message;}
    }
    remember(created);
    $('new-form').reset();clearPhoto();
    feedback('form-feedback');
    $('protocol').value = created.protocolo;
    $('protocol-result').replaceChildren(details(created));
    $('protocol-result').hidden = false;
    feedback('lookup-feedback','Ocorrência registrada. Guarde seu protocolo: ' + created.protocolo + '.' + uploadError,!!uploadError);
    go('ocorrencias');
    toast(uploadError ? 'Relato registrado; confira o aviso sobre a fotografia.' : 'Ocorrência registrada com sucesso.',!!uploadError);
    refreshDashboard();
  } catch (error) {feedback('form-feedback',error.message,true);toast(error.message,true);}
  finally {button.disabled = false;button.replaceChildren(document.createTextNode('Enviar ocorrência '),node('span','','→'));}
}
function setUser(user) {
  state.user = user;
  $('login-form').hidden = !!user;
  $('admin-panel').hidden = !user;
  $('session-label').textContent = user ? `Sessão de ${user.username} · ${(user.roles || []).join(', ')}` : '';
  if (!user) {
    state.adminRequest++;
    $('admin-list').replaceChildren();state.adminRows = [];
    $('detail-dialog').close();$('detail-body').replaceChildren();
  }
}
async function login(event) {
  event.preventDefault();$('login-button').disabled = true;
  feedback('login-feedback','Autenticando…');
  try {
    const user = await api('/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:$('admin-username').value.trim(),password:$('admin-password').value})});
    $('admin-password').value = '';
    state.csrf = null;
    await csrf();
    setUser(user);state.page = 0;
    feedback('login-feedback');
    await loadAdmin();
    toast('Acesso autorizado.');
  } catch (error) {feedback('login-feedback',error.status === 401 ? 'Usuário ou senha inválidos.' : error.message,true);}
  finally {$('login-button').disabled = false;}
}
async function logout() {
  $('logout-button').disabled = true;
  try {
    await api('/auth/logout',{method:'POST'});
    state.csrf = null;setUser(null);await csrf();
    toast('Sessão encerrada.');
  } catch (error) {feedback('admin-feedback',error.message,true);}
  finally {$('logout-button').disabled = false;}
}
async function loadAdmin() {
  if (!state.user) return;
  const request = ++state.adminRequest;
  feedback('admin-feedback','Carregando ocorrências…');
  $('admin-list').setAttribute('aria-busy','true');
  const params = new URLSearchParams({page:state.page,size:'10'});
  for (const [name,id] of [['status','filter-status'],['prioridade','filter-priority'],['q','search']]) {
    const value = $(id).value.trim();if (value) params.set(name,value);
  }
  try {
    const page = await api('/admin/ocorrencias?' + params);
    if (request !== state.adminRequest) return;
    state.adminRows = page.content;
    state.page = page.number;state.totalPages = page.totalPages;
    const element = $('admin-list');element.replaceChildren();
    if (!page.content.length) emptyList(element,'Nenhuma ocorrência corresponde aos filtros.');
    else page.content.forEach(o => element.append(record(o,true)));
    $('page-counter').textContent = page.totalElements ? `Página ${page.number+1} de ${page.totalPages} · ${page.totalElements} ocorrências` : 'Nenhuma ocorrência';
    $('previous-page').disabled = page.number === 0;
    $('next-page').disabled = page.number+1 >= page.totalPages;
    feedback('admin-feedback');
  } catch (error) {
    if (request !== state.adminRequest) return;
    feedback('admin-feedback',error.message,true);
    if (error.status === 401) {setUser(null);feedback('login-feedback','Sua sessão terminou. Entre novamente.',true);}
  } finally {if (request === state.adminRequest) $('admin-list').setAttribute('aria-busy','false');}
}
async function init() {
  document.querySelectorAll('[data-page]').forEach(button => button.addEventListener('click',() => go(button.dataset.page)));
  document.querySelectorAll('[data-go]').forEach(button => button.addEventListener('click',() => go(button.dataset.go)));
  $('new-form').addEventListener('submit',sendForm);
  $('foto').addEventListener('change',previewPhoto);
  $('remove-photo').addEventListener('click',clearPhoto);
  $('protocol-form').addEventListener('submit',event => {event.preventDefault();lookupProtocol($('protocol').value);});
  $('copy-protocol').addEventListener('click',async () => {
    const protocol = $('protocol').value.trim();
    if (!protocol) {feedback('lookup-feedback','Informe ou consulte um protocolo para copiar.',true);return;}
    try {await navigator.clipboard.writeText(protocol);toast('Protocolo copiado. Guarde-o para acompanhar a ocorrência.');}
    catch {$('protocol').focus();$('protocol').select();feedback('lookup-feedback','Protocolo selecionado. Use a opção Copiar do seu dispositivo.');}
  });
  $('clear-history').addEventListener('click',() => {state.history.clear();renderHistory();toast('Histórico desta aba apagado.');});
  $('login-form').addEventListener('submit',login);
  $('logout-button').addEventListener('click',logout);
  $('filter-form').addEventListener('submit',event => {event.preventDefault();state.page = 0;loadAdmin();});
  $('refresh').addEventListener('click',loadAdmin);
  $('previous-page').addEventListener('click',() => {if (state.page > 0) {state.page--;loadAdmin();}});
  $('next-page').addEventListener('click',() => {if (state.page+1 < state.totalPages) {state.page++;loadAdmin();}});
  $('close-detail').addEventListener('click',() => $('detail-dialog').close());
  $('detail-dialog').addEventListener('click',event => {if (event.target === $('detail-dialog')) $('detail-dialog').close();});
  window.addEventListener('hashchange',() => go(location.hash.slice(1)));
  window.addEventListener('pagehide',() => {if (state.previewUrl) URL.revokeObjectURL(state.previewUrl);});
  setUser(null);renderHistory();go(location.hash.slice(1) || 'inicio');
  try {
    await csrf();
    try {setUser(await api('/auth/me'));if (location.hash === '#gestao') loadAdmin();} catch (error) {if (error.status !== 401) toast(error.message,true);}
  } catch (error) {toast(error.message,true);}
}
document.addEventListener('DOMContentLoaded',init);
