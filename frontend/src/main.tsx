import React, { FormEvent, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { api, ApiError, errorMessage, resetCsrf } from './api';
import { ProfileView } from './profile';
import './styles.css';

type Account = { id: string; email: string; status: string };
type TrackingState = 'SAVED'|'DISCARDED'|'APPLIED'|'INTERVIEW'|'OFFER'|'ACCEPTED'|'REJECTED'|'WITHDRAWN';
type Activity = { jobId: string; new: boolean; trackingId?: string; state?: TrackingState; note?: string; version?: number };
type Job = { id?: string; jobId?: string; title: string; employer: string; roleFamilyId?: string; roleFamily?: string; seniority?: string; remoteMode?: string; employmentType?: string; salaryMinMonthly?: number; salaryMaxMonthly?: number; currency?: string; city?: string; state?: string; countryCode?: string; publishedAt?: string; score?: number; classification?: string };
type JobDetail = Required<Pick<Job,'id'|'title'|'employer'>> & { version:number; roleFamilyId?:string; roleFamily?:string; description:string; seniority?:string; remoteMode?:string; employmentType?:string; salaryMinMonthly?:number; salaryMaxMonthly?:number; currency?:string; publishedAt:string; locations:{id:string;countryCode:string;state?:string;city?:string}[]; sourceLinks:{postingId:string;source:string;url:string;type:string;preferred:boolean}[]; requirements:{id:string;text:string;category:string;mandatory:boolean}[]; skillRequirements:{id:string;skillId:string;skill:string;priority:string;evidenceText?:string}[] };
type CursorPage<T> = { items:T[]; nextCursor?:string };
type CatalogRole = { id:string; name:string };
type SearchFilters = { query:string; roles:CatalogRole[]; remoteModes:string[]; employmentTypes:string[]; countryCode:string; state:string; city:string; minimumMonthlySalary:string; publishedWithinDays:string; excludeEmployers:boolean };
type SavedSearch = { id:string; name:string; version:number; criteria:{ query?:string; roleFamilyIds?:string[]; remoteModes?:string[]; employmentTypes?:string[]; countryCode?:string; state?:string; city?:string; minimumMonthlySalary?:number; publishedWithinDays?:number; excludeEmployers?:boolean } };
type TrackingEvent = { id: string; fromState?: TrackingState; toState: TrackingState; note?: string; resultingVersion: number; occurredAt: string };
type TrackedJob = { id: string; jobId: string; title: string; employer: string; state: TrackingState; note?: string; version: number; updatedAt: string; history: TrackingEvent[] };
type Match = { score: number; classification: string; reasons: { id: string; type: string; explanation: string; points: number }[] };
type RecommendationFeed = { status:'UPDATING'|'READY'; profileVersion:number; generatedProfileVersion?:number; generatedAt?:string; items:Job[] };
type CvRun = { id: string; status: string; originalFilename: string; candidates: CvCandidate[]; safeErrorCode?: string };
type CvCandidate = { id: string; type: string; proposal: Record<string, unknown>; decision: string; decisionVersion: number; duplicate?: { similarityScore: number; resolution?: string } };

const stateLabel: Record<TrackingState, string> = { SAVED:'Guardada', DISCARDED:'Descartada', APPLIED:'Postulada', INTERVIEW:'Entrevista', OFFER:'Oferta', ACCEPTED:'Aceptada', REJECTED:'Rechazada', WITHDRAWN:'Retirada' };
const transitions: Record<TrackingState, TrackingState[]> = { SAVED:['DISCARDED','APPLIED'], DISCARDED:['SAVED'], APPLIED:['INTERVIEW','REJECTED','WITHDRAWN'], INTERVIEW:['OFFER','REJECTED','WITHDRAWN'], OFFER:['ACCEPTED','REJECTED','WITHDRAWN'], ACCEPTED:[], REJECTED:[], WITHDRAWN:[] };

function App() {
  const [account, setAccount] = useState<Account | null | undefined>();
  useEffect(() => { api<Account>('/me/account').then(setAccount).catch(() => setAccount(null)); }, []);
  if (account === undefined) return <Loading label="Preparando tu espacio" />;
  if (!account) return <Auth onAuthenticated={() => api<Account>('/me/account').then(setAccount)} />;
  return <Workspace account={account} onLogout={() => setAccount(null)} />;
}

function Auth({ onAuthenticated }: { onAuthenticated: () => Promise<void> }) {
  type AuthMode='login'|'register'|'forgot'|'reset'|'verify';
  const params=new URLSearchParams(window.location.search);const linkedToken=params.get('token')||'';
  const initialMode:AuthMode=window.location.pathname.includes('verify-email')&&linkedToken?'verify':window.location.pathname.includes('reset-password')&&linkedToken?'reset':'login';
  const [mode, setMode] = useState<AuthMode>(initialMode);
  const [email, setEmail] = useState(''); const [password, setPassword] = useState(''); const [token,setToken]=useState(linkedToken);
  const [busy, setBusy] = useState(false); const [message, setMessage] = useState('');
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setMessage('');
    try {
      if (mode === 'register') { await api('/auth/register', { method:'POST', body:JSON.stringify({ email, password }) }); setMessage('Revisa Mailpit o tu correo para verificar la cuenta.'); }
      else if(mode==='forgot'){await api('/auth/password/forgot',{method:'POST',body:JSON.stringify({email})});setMessage('Si la cuenta existe, recibirás un enlace de recuperación.');}
      else if(mode==='reset'){await api('/auth/password/reset',{method:'POST',body:JSON.stringify({token,newPassword:password})});setMessage('Contraseña actualizada. Ya puedes ingresar.');setMode('login');window.history.replaceState({},'', '/');}
      else if(mode==='verify'){await api('/auth/verify-email',{method:'POST',body:JSON.stringify({token})});setMessage('Correo verificado. Ya puedes ingresar.');setMode('login');window.history.replaceState({},'', '/');}
      else { await api('/auth/login', { method:'POST', body:JSON.stringify({ email, password }) }); await onAuthenticated(); }
    } catch (error) { setMessage(errorMessage(error)); } finally { setBusy(false); }
  }
  const actionLabel:Record<AuthMode,string>={login:'Entrar',register:'Crear cuenta',forgot:'Enviar enlace',reset:'Cambiar contraseña',verify:'Verificar correo'};
  return <main className="auth-shell"><section className="brand-panel"><p className="eyebrow">JobMatch México</p><h1>Tu búsqueda, con evidencia.</h1><p>Convierte experiencia real en recomendaciones explicables y un seguimiento claro.</p></section><form className="auth-card" onSubmit={submit}><div className="segmented"><button type="button" className={mode==='login'?'active':''} onClick={()=>setMode('login')}>Ingresar</button><button type="button" className={mode==='register'?'active':''} onClick={()=>setMode('register')}>Crear cuenta</button></div>{['login','register','forgot'].includes(mode)&&<label>Correo<input type="email" value={email} onChange={event=>setEmail(event.target.value)} required /></label>}{['login','register','reset'].includes(mode)&&<label>{mode==='reset'?'Nueva contraseña':'Contraseña'}<input type="password" value={password} onChange={event=>setPassword(event.target.value)} minLength={12} required /></label>}{['verify','reset'].includes(mode)&&!linkedToken&&<label>Token<input value={token} onChange={event=>setToken(event.target.value)} required /></label>}<button className="primary" disabled={busy}>{busy?'Procesando…':actionLabel[mode]}</button>{mode==='login'&&<button type="button" className="quiet" onClick={()=>setMode('forgot')}>Olvidé mi contraseña</button>}{mode==='forgot'&&<button type="button" className="quiet" onClick={()=>setMode('login')}>Volver al ingreso</button>}{message && <p className="notice">{message}</p>}</form></main>;
}

type View = 'recommendations'|'search'|'tracking'|'profile'|'cv';
function Workspace({ account, onLogout }: { account: Account; onLogout: () => void }) {
  const [view, setView] = useState<View>('recommendations'); const [notice, setNotice] = useState('');
  async function logout() { await api('/auth/logout', { method:'POST' }); resetCsrf(); onLogout(); }
  const titles: Record<View,string>={recommendations:'Recomendaciones',search:'Explorar vacantes',tracking:'Tu seguimiento',profile:'Perfil profesional',cv:'Importar CV'};
  return <div className="app-shell"><aside><div><p className="eyebrow">JobMatch</p><h2>Hola</h2><small>{account.email}</small></div><nav>{([['recommendations','Para ti'],['search','Explorar'],['tracking','Seguimiento'],['profile','Perfil'],['cv','Importar CV']] as [View,string][]).map(([id,label])=><button key={id} className={view===id?'active':''} onClick={()=>setView(id)}>{label}</button>)}</nav><button className="quiet" onClick={logout}>Cerrar sesión</button></aside><div className="workspace"><header><div><p className="eyebrow">Panel personal</p><h1>{titles[view]}</h1></div></header>{notice && <div className="toast" role="status">{notice}<button onClick={()=>setNotice('')}>×</button></div>}{view==='recommendations' && <JobsView notify={setNotice} />}{view==='search' && <ExploreView notify={setNotice} />}{view==='tracking' && <TrackingView notify={setNotice} />}{view==='profile' && <ProfileView notify={setNotice} />}{view==='cv' && <CvView notify={setNotice} />}</div></div>;
}

function JobsView({ notify }: { notify:(value:string)=>void }) {
  const [jobs, setJobs] = useState<Job[]>([]); const [activity, setActivity] = useState<Record<string,Activity>>({});
  const [loading, setLoading] = useState(true); const [error, setError] = useState('');
  const [recommendationStatus,setRecommendationStatus]=useState<'UPDATING'|'READY'>('READY');
  async function load(background=false) {
    if(!background)setLoading(true); setError('');
    try {
      let result:Job[];
      const feed=await api<RecommendationFeed>('/me/recommendations?limit=50');setRecommendationStatus(feed.status);result=feed.items;
      const ids = result.map(job=>job.jobId || job.id!).filter(Boolean);
      if (ids.length) { const params = ids.map(id=>`jobId=${encodeURIComponent(id)}`).join('&'); const before = await api<Activity[]>(`/me/job-activity?${params}`); setActivity(Object.fromEntries(before.map(item=>[item.jobId,item]))); }
      setJobs(result);
    } catch (failure) { setError(errorMessage(failure)); } finally { if(!background)setLoading(false); }
  }
  useEffect(()=>{ void load(); },[]);
  useEffect(()=>{if(recommendationStatus!=='UPDATING')return;const timer=setInterval(()=>void load(true),2000);return()=>clearInterval(timer);},[recommendationStatus]);
  useEffect(()=>{
    if(loading||!jobs.length)return;
    const ids=jobs.map(job=>job.jobId||job.id!).filter(Boolean);
    const frame=requestAnimationFrame(()=>{void api('/me/job-impressions',{method:'POST',body:JSON.stringify({jobIds:ids})}).catch(failure=>notify(errorMessage(failure)));});
    return()=>cancelAnimationFrame(frame);
  },[loading,jobs]);
  async function refreshActivity(jobId:string){const found=await api<Activity[]>(`/me/job-activity?jobId=${jobId}`);setActivity(values=>({...values,[jobId]:found[0]}));}
  async function transition(jobId:string, state:TrackingState) {
    const current=activity[jobId];
    try { const tracked=await api<TrackedJob>(`/me/jobs/${jobId}/tracking`, { method:'PUT', headers:{'If-Match':String(current?.version||0),'Idempotency-Key':crypto.randomUUID()}, body:JSON.stringify({state}) }); setActivity(values=>({...values,[jobId]:{...values[jobId],jobId,new:false,trackingId:tracked.id,state:tracked.state,version:tracked.version,note:tracked.note}})); notify(`Vacante actualizada: ${stateLabel[state]}.`); }
    catch (failure) { if (failure instanceof ApiError && failure.problem.code==='VERSION_CONFLICT') await refreshActivity(jobId); notify(errorMessage(failure)); }
  }
  return <section>{loading?<Loading label="Buscando oportunidades"/>:error?<Retry message={error} onRetry={()=>void load()}/>:recommendationStatus==='UPDATING'?<div className="recommendation-updating"><span></span><div><h3>Actualizando tus recomendaciones</h3><p>Aplicamos los cambios de tu perfil. Esta lista se reemplazará automáticamente cuando termine.</p></div></div>:jobs.length===0?<Empty title="Aún no hay recomendaciones" detail="Agrega al menos un rol objetivo en tu perfil o espera a que existan vacantes compatibles."/>:<div className="job-grid">{jobs.map(job=>{const id=job.jobId||job.id!;return <JobCard key={id} job={job} activity={activity[id]} onTransition={state=>void transition(id,state)} notify={notify}/>})}</div>}</section>;
}

const emptySearchFilters = (): SearchFilters => ({ query:'', roles:[], remoteModes:[], employmentTypes:[], countryCode:'', state:'', city:'', minimumMonthlySalary:'', publishedWithinDays:'', excludeEmployers:false });

function ExploreView({ notify }: { notify:(value:string)=>void }) {
  const [filters,setFilters]=useState<SearchFilters>(emptySearchFilters);
  const [pages,setPages]=useState<CursorPage<Job>[]>([]); const [pageIndex,setPageIndex]=useState(0);
  const [selectedId,setSelectedId]=useState<string>(); const [detail,setDetail]=useState<JobDetail>();
  const [activity,setActivity]=useState<Record<string,Activity>>({}); const [loading,setLoading]=useState(true); const [detailLoading,setDetailLoading]=useState(false);
  const [error,setError]=useState(''); const [roleTerm,setRoleTerm]=useState(''); const [roleSuggestions,setRoleSuggestions]=useState<CatalogRole[]>([]);
  const [saveName,setSaveName]=useState(''); const [savedSearches,setSavedSearches]=useState<SavedSearch[]>([]);const [catalogRoles,setCatalogRoles]=useState<CatalogRole[]>([]);
  const currentPage=pages[pageIndex]; const jobs=currentPage?.items||[];

  async function fetchPage(active:SearchFilters,cursor?:string){
    const params=new URLSearchParams();
    if(active.query.trim())params.set('q',active.query.trim());
    active.roles.forEach(role=>params.append('roleFamilyId',role.id));
    active.remoteModes.forEach(mode=>params.append('remoteMode',mode));
    active.employmentTypes.forEach(type=>params.append('employmentType',type));
    if(active.countryCode.trim())params.set('countryCode',active.countryCode.trim());
    if(active.state.trim())params.set('state',active.state.trim());
    if(active.city.trim())params.set('city',active.city.trim());
    if(active.minimumMonthlySalary.trim())params.set('minimumMonthlySalary',active.minimumMonthlySalary.trim());
    if(active.publishedWithinDays)params.set('publishedWithinDays',active.publishedWithinDays);
    if(active.excludeEmployers)params.set('excludeEmployers','true');
    if(cursor)params.set('cursor',cursor); params.set('limit','25');
    return api<CursorPage<Job>>(`/jobs/search?${params}`);
  }

  async function populateActivity(items:Job[]){
    const ids=items.map(job=>job.id).filter((id):id is string=>Boolean(id));
    if(!ids.length){setActivity({});return;}
    const params=ids.map(id=>`jobId=${encodeURIComponent(id)}`).join('&');
    const found=await api<Activity[]>(`/me/job-activity?${params}`);
    setActivity(Object.fromEntries(found.map(item=>[item.jobId,item])));
  }

  async function search(active=filters,cursor?:string,next=false){
    setLoading(true);setError('');setDetail(undefined);
    try {
      const result=await fetchPage(active,cursor); await populateActivity(result.items);
      if(next){setPages(previous=>[...previous,result]);setPageIndex(previous=>previous+1);}else{setPages([result]);setPageIndex(0);}
      setSelectedId(result.items[0]?.id);
    } catch(failure){setError(errorMessage(failure));} finally {setLoading(false);}
  }

  async function loadSavedSearches(){setSavedSearches(await api<SavedSearch[]>('/me/saved-searches'));}
  useEffect(()=>{void search(emptySearchFilters());void loadSavedSearches().catch(failure=>notify(errorMessage(failure)));void api<CatalogRole[]>('/catalog/roles?limit=50').then(setCatalogRoles);},[]);
  useEffect(()=>{
    if(roleTerm.trim().length<2){setRoleSuggestions([]);return;}
    const timer=window.setTimeout(()=>{void api<CatalogRole[]>(`/catalog/roles?q=${encodeURIComponent(roleTerm.trim())}&limit=10`)
      .then(items=>setRoleSuggestions(items.filter(item=>!filters.roles.some(role=>role.id===item.id))))
      .catch(()=>setRoleSuggestions([]));},250);
    return()=>window.clearTimeout(timer);
  },[roleTerm,filters.roles]);
  useEffect(()=>{
    if(!selectedId){setDetail(undefined);return;}
    let cancelled=false;setDetailLoading(true);
    void api<JobDetail>(`/jobs/${selectedId}`).then(result=>{if(!cancelled)setDetail(result);}).catch(failure=>{if(!cancelled)notify(errorMessage(failure));}).finally(()=>{if(!cancelled)setDetailLoading(false);});
    return()=>{cancelled=true;};
  },[selectedId,notify]);
  useEffect(()=>{
    if(loading||!jobs.length)return;
    const ids=jobs.map(job=>job.id).filter((id):id is string=>Boolean(id));
    const frame=requestAnimationFrame(()=>{void api('/me/job-impressions',{method:'POST',body:JSON.stringify({jobIds:ids})}).catch(failure=>notify(errorMessage(failure)));});
    return()=>cancelAnimationFrame(frame);
  },[loading,jobs,notify]);

  function updateFilter<K extends keyof SearchFilters>(key:K,value:SearchFilters[K]){setFilters(current=>({...current,[key]:value}));}
  function toggle(key:'remoteModes'|'employmentTypes',value:string){setFilters(current=>({...current,[key]:current[key].includes(value)?current[key].filter(item=>item!==value):[...current[key],value]}));}
  function addRole(role:CatalogRole){if(filters.roles.length>=10)return;setFilters(current=>({...current,roles:[...current.roles,role]}));setRoleTerm('');setRoleSuggestions([]);}
  async function transition(jobId:string,state:TrackingState){
    const current=activity[jobId];
    try {
      const tracked=await api<TrackedJob>(`/me/jobs/${jobId}/tracking`,{method:'PUT',headers:{'If-Match':String(current?.version||0),'Idempotency-Key':crypto.randomUUID()},body:JSON.stringify({state})});
      setActivity(values=>({...values,[jobId]:{...values[jobId],jobId,new:false,trackingId:tracked.id,state:tracked.state,version:tracked.version,note:tracked.note}}));
      if(state==='DISCARDED'){
        const remaining=jobs.filter(job=>job.id!==jobId);setPages(all=>all.map((page,index)=>index===pageIndex?{...page,items:remaining}:page));setSelectedId(remaining[0]?.id);setDetail(undefined);
      }
      notify(`Vacante actualizada: ${stateLabel[state]}.`);
    } catch(failure){if(failure instanceof ApiError&&failure.problem.code==='VERSION_CONFLICT')await populateActivity(jobs);notify(errorMessage(failure));}
  }
  async function saveSearch(){
    if(!saveName.trim()){notify('Escribe un nombre para guardar esta búsqueda.');return;}
    try {await api('/me/saved-searches',{method:'POST',body:JSON.stringify({name:saveName.trim(),criteria:criteria(filters)})});setSaveName('');await loadSavedSearches();notify('Búsqueda guardada.');}
    catch(failure){notify(errorMessage(failure));}
  }
  async function applySaved(item:SavedSearch){const c=item.criteria;const next:SearchFilters={query:c.query||'',roles:(c.roleFamilyIds||[]).map(id=>catalogRoles.find(role=>role.id===id)||{id,name:'Rol guardado'}),remoteModes:c.remoteModes||[],employmentTypes:c.employmentTypes||[],countryCode:c.countryCode||'',state:c.state||'',city:c.city||'',minimumMonthlySalary:c.minimumMonthlySalary?.toString()||'',publishedWithinDays:c.publishedWithinDays?.toString()||'',excludeEmployers:Boolean(c.excludeEmployers)};setFilters(next);await search(next);}
  async function renameSaved(item:SavedSearch){const name=window.prompt('Nuevo nombre de la búsqueda',item.name)?.trim();if(!name||name===item.name)return;try{await api(`/me/saved-searches/${item.id}`,{method:'PUT',headers:{'If-Match':String(item.version)},body:JSON.stringify({name,criteria:item.criteria})});await loadSavedSearches();notify('Búsqueda renombrada.');}catch(failure){notify(errorMessage(failure));}}
  async function deleteSaved(item:SavedSearch){if(!window.confirm(`¿Eliminar la búsqueda “${item.name}”?`))return;try{await api(`/me/saved-searches/${item.id}`,{method:'DELETE',headers:{'If-Match':String(item.version)}});await loadSavedSearches();notify('Búsqueda eliminada.');}catch(failure){notify(errorMessage(failure));}}
  function clear(){const next=emptySearchFilters();setFilters(next);void search(next);}
  const selectedActivity=selectedId?activity[selectedId]:undefined;
  return <section className="explore-page">
    {savedSearches.length>0&&<section className="saved-searches"><div><h2>Búsquedas guardadas</h2><p>Recupera en un clic una combinación de filtros que utilizas con frecuencia.</p></div><div className="saved-search-list">{savedSearches.map(item=><article key={item.id}><strong>{item.name}</strong><div><button type="button" className="primary" onClick={()=>void applySaved(item)}>Aplicar</button><button type="button" onClick={()=>void renameSaved(item)}>Renombrar</button><button type="button" className="quiet" onClick={()=>void deleteSaved(item)}>Eliminar</button></div></article>)}</div></section>}
    <form className="explore-filters" onSubmit={event=>{event.preventDefault();void search();}}>
      <div className="filter-main"><label>Busca por rol, tecnología o empresa<input value={filters.query} onChange={event=>updateFilter('query',event.target.value)} placeholder="Java, React, empresa…"/></label><button className="primary">Aplicar filtros</button><button type="button" className="quiet" onClick={clear}>Limpiar</button></div>
      <details className="filter-details"><summary>Filtros avanzados</summary><div className="filter-grid">
        <div className="picker"><label>Roles <small>Hasta 10</small><input value={roleTerm} disabled={filters.roles.length>=10} onChange={event=>setRoleTerm(event.target.value)} placeholder="Escribe al menos dos letras"/></label>{roleSuggestions.length>0&&<div className="suggestions">{roleSuggestions.map(role=><button type="button" key={role.id} onClick={()=>addRole(role)}><span>{role.name}</span></button>)}</div>}<div className="read-chips">{filters.roles.map(role=><button type="button" key={role.id} onClick={()=>updateFilter('roles',filters.roles.filter(item=>item.id!==role.id))}>{role.name} ×</button>)}</div></div>
        <FilterChoices label="Modalidad" values={filters.remoteModes} options={[['REMOTE','Remoto'],['HYBRID','Híbrido'],['ONSITE','Presencial']]} onToggle={value=>toggle('remoteModes',value)}/>
        <FilterChoices label="Tipo de empleo" values={filters.employmentTypes} options={[['FULL_TIME','Tiempo completo'],['PART_TIME','Medio tiempo'],['CONTRACT','Contrato'],['INTERNSHIP','Prácticas']]} onToggle={value=>toggle('employmentTypes',value)}/>
        <label>País <input value={filters.countryCode} maxLength={2} onChange={event=>updateFilter('countryCode',event.target.value.toUpperCase())} placeholder="MX"/></label>
        <label>Estado <input value={filters.state} onChange={event=>updateFilter('state',event.target.value)} placeholder="Jalisco"/></label>
        <label>Ciudad <input value={filters.city} onChange={event=>updateFilter('city',event.target.value)} placeholder="Guadalajara"/></label>
        <label>Salario mensual desde <input type="number" min="0" value={filters.minimumMonthlySalary} onChange={event=>updateFilter('minimumMonthlySalary',event.target.value)} placeholder="30000"/></label>
        <label>Publicada en <select value={filters.publishedWithinDays} onChange={event=>updateFilter('publishedWithinDays',event.target.value)}><option value="">Cualquier fecha</option><option value="1">Últimas 24 horas</option><option value="7">Última semana</option><option value="30">Último mes</option><option value="90">Últimos 3 meses</option></select></label>
        <label className="check-field"><input type="checkbox" checked={filters.excludeEmployers} onChange={event=>updateFilter('excludeEmployers',event.target.checked)}/><span>Ocultar empresas excluidas en mi perfil</span></label>
      </div></details>
      <div className="save-search"><label>Guardar esta búsqueda<input value={saveName} onChange={event=>setSaveName(event.target.value)} placeholder="Ej. Backend remoto México"/></label><button type="button" onClick={()=>void saveSearch()}>Guardar</button></div>
    </form>
    {loading?<Loading label="Buscando vacantes"/>:error?<Retry message={error} onRetry={()=>void search()}/>:!jobs.length?<Empty title="No encontramos vacantes" detail="Prueba con menos filtros o términos más amplios."/>:<div className="explore-workspace">
      <div className="job-stack"><div className="job-stack-head"><strong>{jobs.length} vacantes</strong><span>Página {pageIndex+1}</span></div>{jobs.map(job=><button type="button" key={job.id} className={`job-row ${selectedId===job.id?'selected':''}`} onClick={()=>setSelectedId(job.id)}><span>{job.employer}</span><strong>{job.title}</strong><small>{jobFacts(job)}</small></button>)}<div className="pagination"><button type="button" disabled={pageIndex===0} onClick={()=>{const previous=pages[pageIndex-1];if(previous){void populateActivity(previous.items);setSelectedId(previous.items[0]?.id);}setPageIndex(index=>index-1);}}>Anterior</button><span>{pageIndex+1}</span><button type="button" disabled={!currentPage?.nextCursor} onClick={()=>void search(filters,currentPage?.nextCursor,true)}>Siguiente</button></div></div>
      <ExploreDetail detail={detail} loading={detailLoading} activity={selectedActivity} onTransition={state=>{if(selectedId)void transition(selectedId,state);}} notify={notify}/>
    </div>}</section>;
}

function FilterChoices({label,values,options,onToggle}:{label:string;values:string[];options:[string,string][];onToggle:(value:string)=>void}){
  return <fieldset className="choice-field"><legend>{label}</legend>{options.map(([value,name])=><label key={value}><input type="checkbox" checked={values.includes(value)} onChange={()=>onToggle(value)}/>{name}</label>)}</fieldset>;
}

function ExploreDetail({detail,loading,activity,onTransition,notify}:{detail?:JobDetail;loading:boolean;activity?:Activity;onTransition:(state:TrackingState)=>void;notify:(value:string)=>void}){
  const [match,setMatch]=useState<Match>(); const [busy,setBusy]=useState(false);
  useEffect(()=>setMatch(undefined),[detail?.id]);
  if(loading)return <div className="job-detail"><Loading label="Cargando detalle"/></div>;
  if(!detail)return <div className="job-detail job-detail-empty"><Empty title="Selecciona una vacante" detail="Su descripción y requisitos aparecerán aquí."/></div>;
  const available:TrackingState[]=activity?.state?transitions[activity.state]:['SAVED','DISCARDED','APPLIED'];
  const detailId=detail.id;const salary=salaryText(detail);const location=detail.locations.map(item=>[item.city,item.state,item.countryCode].filter(Boolean).join(', ')).join(' · ');
  async function inspect(){setBusy(true);try{setMatch(await api<Match>(`/jobs/${detailId}/match`));}catch(error){notify(errorMessage(error));}finally{setBusy(false);}}
  return <article className="job-detail"><header><div><p className="eyebrow">{detail.employer}</p><h2>{detail.title}</h2><p className="job-facts">{[detail.roleFamily,detail.seniority,detail.remoteMode,detail.employmentType].filter(Boolean).join(' · ')}</p></div>{activity?.state&&<span className={`state state-${activity.state.toLowerCase()}`}>{stateLabel[activity.state]}</span>}</header><div className="detail-actions">{available.map(state=><button key={state} onClick={()=>onTransition(state)}>{stateLabel[state]}</button>)}<button className="quiet" disabled={busy} onClick={()=>void inspect()}>{busy?'Calculando…':'Ver evidencia'}</button></div><dl className="job-detail-facts"><div><dt>Ubicación</dt><dd>{location||'No especificada'}</dd></div><div><dt>Salario</dt><dd>{salary||'No especificado'}</dd></div><div><dt>Publicada</dt><dd>{new Date(detail.publishedAt).toLocaleDateString('es-MX')}</dd></div></dl><section><h3>Descripción</h3><p className="long-copy">{detail.description}</p></section>{detail.requirements.length>0&&<section><h3>Requisitos</h3><ul className="detail-list">{detail.requirements.map(item=><li key={item.id}>{item.text}{item.mandatory&&<strong> Obligatorio</strong>}</li>)}</ul></section>}{detail.skillRequirements.length>0&&<section><h3>Habilidades</h3><div className="read-chips">{detail.skillRequirements.map(item=><span key={item.id}>{item.skill}</span>)}</div></section>}{detail.sourceLinks.length>0&&<section><h3>Postularse</h3><div className="source-links">{detail.sourceLinks.map(link=><a key={link.postingId} href={link.url} target="_blank" rel="noreferrer">{link.source}{link.preferred?' · Principal':''}</a>)}</div></section>}{match&&<section className="match-detail"><strong>{Math.round(Number(match.score))}/100 · {match.classification}</strong><ul>{match.reasons.slice(0,5).map(reason=><li key={reason.id} className={reason.type.toLowerCase()}>{reason.explanation}</li>)}</ul></section>}</article>;
}

function criteria(filters:SearchFilters){return {query:filters.query.trim()||null,roleFamilyIds:filters.roles.map(role=>role.id),remoteModes:filters.remoteModes,employmentTypes:filters.employmentTypes,countryCode:filters.countryCode.trim()||null,state:filters.state.trim()||null,city:filters.city.trim()||null,minimumMonthlySalary:filters.minimumMonthlySalary?Number(filters.minimumMonthlySalary):null,publishedWithinDays:filters.publishedWithinDays?Number(filters.publishedWithinDays):null,excludeEmployers:filters.excludeEmployers};}
function salaryText(job:Pick<Job,'salaryMinMonthly'|'salaryMaxMonthly'|'currency'>){if(job.salaryMinMonthly==null&&job.salaryMaxMonthly==null)return '';const currency=job.currency||'MXN';return `${new Intl.NumberFormat('es-MX',{style:'currency',currency,maximumFractionDigits:0}).format(job.salaryMinMonthly||0)} – ${new Intl.NumberFormat('es-MX',{style:'currency',currency,maximumFractionDigits:0}).format(job.salaryMaxMonthly||job.salaryMinMonthly||0)}`;}
function jobFacts(job:Job){return [job.city||job.state,[job.remoteMode,job.employmentType].filter(Boolean).join(' · '),salaryText(job)].filter(Boolean).join(' · ');}

function JobCard({ job, activity, onTransition, notify }: { job:Job; activity?:Activity; onTransition:(state:TrackingState)=>void; notify:(value:string)=>void }) {
  const [match,setMatch]=useState<Match>(); const [busy,setBusy]=useState(false); const id=job.jobId||job.id!;
  const available: TrackingState[]=activity?.state ? transitions[activity.state] : ['SAVED','DISCARDED','APPLIED'];
  async function inspect(){setBusy(true);try{setMatch(await api<Match>(`/jobs/${id}/match`));}catch(e){notify(errorMessage(e));}finally{setBusy(false)}}
  return <article className="job-card"><div className="job-top"><div>{activity?.new&&<span className="new-badge">Nueva</span>}<p className="meta">{job.employer}</p><h3>{job.title}</h3></div>{job.score!==undefined&&<div className="score"><strong>{Math.round(Number(job.score))}</strong><span>/100</span></div>}</div><p className="job-facts">{[job.seniority,job.remoteMode,job.employmentType].filter(Boolean).join(' · ')}</p>{activity?.state&&<span className={`state state-${activity.state.toLowerCase()}`}>{stateLabel[activity.state]}</span>}<div className="actions">{available.map(state=><button key={state} onClick={()=>onTransition(state)}>{stateLabel[state]}</button>)}<button className="quiet" disabled={busy} onClick={()=>void inspect()}>{busy?'Calculando…':'Ver evidencia'}</button></div>{match&&<div className="match-detail"><strong>{Math.round(Number(match.score))}/100 · {match.classification}</strong><ul>{match.reasons.slice(0,5).map(reason=><li key={reason.id} className={reason.type.toLowerCase()}>{reason.explanation}</li>)}</ul></div>}</article>;
}

function TrackingView({ notify }: { notify:(value:string)=>void }) {
  const [items,setItems]=useState<TrackedJob[]>([]);const [loading,setLoading]=useState(true);const [error,setError]=useState('');
  const [states,setStates]=useState<TrackingState[]>(['APPLIED','INTERVIEW','REJECTED','WITHDRAWN']);
  async function load(selected=states){setLoading(true);try{const params=new URLSearchParams({limit:'100'});selected.forEach(state=>params.append('state',state));setItems(await api<TrackedJob[]>(`/me/tracking?${params}`));setError('');}catch(e){setError(errorMessage(e));}finally{setLoading(false)}}
  useEffect(()=>{void load()},[states]);
  async function move(item:TrackedJob,target:TrackingState){try{await api(`/me/jobs/${item.jobId}/tracking`,{method:'PUT',headers:{'If-Match':String(item.version),'Idempotency-Key':crypto.randomUUID()},body:JSON.stringify({state:target,note:item.note})});notify(`Seguimiento actualizado a ${stateLabel[target]}.`);await load();}catch(e){notify(errorMessage(e));await load();}}
  function toggle(state:TrackingState){setStates(current=>current.includes(state)?current.filter(value=>value!==state):[...current,state]);}
  const choices:TrackingState[]=['APPLIED','INTERVIEW','REJECTED','WITHDRAWN','SAVED'];
  return <section className="tracking-page"><fieldset className="tracking-filter"><legend>Mostrar estados</legend>{choices.map(state=><label key={state}><input type="checkbox" checked={states.includes(state)} onChange={()=>toggle(state)}/>{stateLabel[state]}</label>)}</fieldset>{loading?<Loading label="Cargando seguimiento"/>:error?<Retry message={error} onRetry={()=>void load()}/>:!items.length?<Empty title="No hay vacantes en estas categorías" detail="Selecciona Guardada o registra una postulación desde recomendaciones o búsqueda."/>:<div className="tracking-list">{items.map(item=><article className="tracking-card" key={item.id}><div><span className={`state state-${item.state.toLowerCase()}`}>{stateLabel[item.state]}</span><h3>{item.title}</h3><p>{item.employer}</p></div><div className="actions">{transitions[item.state].map(state=><button key={state} onClick={()=>void move(item,state)}>{stateLabel[state]}</button>)}</div><details><summary>Historial · {item.history.length} eventos</summary><ol>{item.history.map(event=><li key={event.id}><time>{new Date(event.occurredAt).toLocaleDateString('es-MX')}</time> {event.fromState?`${stateLabel[event.fromState]} → `:''}{stateLabel[event.toState]}</li>)}</ol></details></article>)}</div>}</section>;
}

function CvView({ notify }: { notify:(value:string)=>void }) {
  const [run,setRun]=useState<CvRun>();const [busy,setBusy]=useState(false);
  async function upload(event:FormEvent<HTMLFormElement>){event.preventDefault();const input=event.currentTarget.elements.namedItem('cv') as HTMLInputElement;if(!input.files?.[0])return;const body=new FormData();body.append('file',input.files[0]);setBusy(true);try{setRun(await api<CvRun>('/me/cv-imports',{method:'POST',body}));notify('CV recibido; la extracción continúa en segundo plano.');}catch(e){notify(errorMessage(e));}finally{setBusy(false)}}
  async function refresh(){if(run)setRun(await api<CvRun>(`/me/cv-imports/${run.id}`))}
  async function decide(candidate:CvCandidate,decision:'ACCEPTED'|'SKIPPED'){if(!run)return;try{setRun(await api<CvRun>(`/me/cv-imports/${run.id}/candidates/${candidate.id}`,{method:'PUT',headers:{'If-Match':String(candidate.decisionVersion)},body:JSON.stringify({decision,duplicateResolution:candidate.duplicate?'KEEP_BOTH':null})}));}catch(e){notify(errorMessage(e));await refresh();}}
  async function confirm(){if(!run)return;try{await api(`/me/cv-imports/${run.id}/confirm`,{method:'POST'});notify('Propuestas confirmadas y perfil actualizado.');await refresh();}catch(e){notify(errorMessage(e));}}
  return <section className="cv-panel"><form onSubmit={upload}><label className="dropzone">PDF textual o DOCX, máximo 5 MB<input name="cv" type="file" accept=".pdf,.docx" required/></label><button className="primary" disabled={busy}>{busy?'Subiendo…':'Analizar CV'}</button></form>{run&&<div className="cv-run"><div className="job-top"><div><p className="meta">{run.originalFilename}</p><h3>Estado: {run.status}</h3></div><button onClick={()=>void refresh()}>Actualizar</button></div>{run.safeErrorCode&&<p className="notice">No fue posible extraer el documento: {run.safeErrorCode}</p>}{run.candidates.map(candidate=><article key={candidate.id} className="candidate"><div><strong>{candidate.type}</strong><pre>{JSON.stringify(candidate.proposal,null,2)}</pre></div><span>{candidate.decision}</span>{candidate.decision==='PENDING'&&<div className="actions"><button onClick={()=>void decide(candidate,'ACCEPTED')}>Aceptar</button><button onClick={()=>void decide(candidate,'SKIPPED')}>Omitir</button></div>}</article>)}{run.status==='REVIEW'&&run.candidates.every(candidate=>candidate.decision!=='PENDING')&&<button className="primary" onClick={()=>void confirm()}>Confirmar cambios</button>}</div>}</section>;
}

function Loading({label}:{label:string}){return <div className="loading"><span></span><p>{label}…</p></div>}
function Retry({message,onRetry}:{message:string;onRetry:()=>void}){return <div className="empty"><h3>No pudimos cargar esta sección</h3><p>{message}</p><button onClick={onRetry}>Reintentar</button></div>}
function Empty({title,detail}:{title:string;detail:string}){return <div className="empty"><h3>{title}</h3><p>{detail}</p></div>}

createRoot(document.getElementById('root')!).render(<React.StrictMode><App /></React.StrictMode>);
