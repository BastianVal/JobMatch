import React, { FormEvent, useCallback, useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { api, ApiError, errorMessage, resetCsrf } from './api';
import { ProfileView } from './profile';
import { publicationLabel } from './publication';
import './styles.css';

type Account = { id: string; email: string; status: string };
type TrackingState = 'SAVED'|'DISCARDED'|'APPLIED'|'INTERVIEW'|'OFFER'|'ACCEPTED'|'REJECTED'|'WITHDRAWN';
type Activity = { jobId: string; new: boolean; trackingId?: string; state?: TrackingState; note?: string; version?: number };
type Job = { id?: string; jobId?: string; title: string; employer: string; roleFamilyId?: string; roleFamily?: string; seniority?: string; remoteMode?: string; employmentType?: string; salaryMinMonthly?: number; salaryMaxMonthly?: number; currency?: string; city?: string; state?: string; countryCode?: string; publishedAt?: string; score?: number; classification?: string };
type JobDetail = Required<Pick<Job,'id'|'title'|'employer'>> & { version:number; roleFamilyId?:string; roleFamily?:string; description:string; seniority?:string; remoteMode?:string; employmentType?:string; salaryMinMonthly?:number; salaryMaxMonthly?:number; currency?:string; publishedAt:string; locations:{id:string;countryCode:string;state?:string;city?:string}[]; sourceLinks:{postingId:string;source:string;url:string;type:string;preferred:boolean}[]; requirements:{id:string;text:string;category:string;mandatory:boolean}[]; skillRequirements:{id:string;skillId:string;skill:string;priority:string;evidenceText?:string}[] };
type CursorPage<T> = { items:T[]; nextCursor?:string };
type CatalogRole = { id:string; name:string };
type SearchFilters = { query:string; roles:CatalogRole[]; remoteModes:string[]; employmentTypes:string[]; minimumMonthlySalary:string; publishedWithinDays:string };
type SavedSearch = { id:string; name:string; version:number; criteria:{ query?:string; roleFamilyIds?:string[]; remoteModes?:string[]; employmentTypes?:string[]; minimumMonthlySalary?:number; publishedWithinDays?:number } };
type TrackingEvent = { id: string; fromState?: TrackingState; toState: TrackingState; note?: string; resultingVersion: number; occurredAt: string };
type TrackedJob = { id: string; jobId: string; title: string; employer: string; state: TrackingState; note?: string; version: number; updatedAt: string; history: TrackingEvent[] };
type Match = { score: number; classification: string; components: Record<string,number>; reasons: { id: string; component:string; type: 'MATCH'|'GAP'|'CONSIDERATION'; requirement?:Record<string,unknown>; evidence?:Record<string,unknown>; explanation: string; points: number }[] };
type RecommendationFeed = { status:'UPDATING'|'READY'; profileVersion:number; generatedProfileVersion?:number; generatedAt?:string; items:Job[] };
type Notice = { id:number; message:string };

const stateLabel: Record<TrackingState, string> = { SAVED:'Guardada', DISCARDED:'Descartada', APPLIED:'Postulada', INTERVIEW:'Entrevista', OFFER:'Oferta', ACCEPTED:'Aceptada', REJECTED:'Rechazada', WITHDRAWN:'Retirada' };
const transitions: Record<TrackingState, TrackingState[]> = { SAVED:['DISCARDED','APPLIED'], DISCARDED:['SAVED'], APPLIED:['INTERVIEW','REJECTED','WITHDRAWN'], INTERVIEW:['OFFER','REJECTED','WITHDRAWN'], OFFER:['ACCEPTED','REJECTED','WITHDRAWN'], ACCEPTED:[], REJECTED:[], WITHDRAWN:[] };
const explorationActionLabel: Partial<Record<TrackingState, string>> = { SAVED:'Guardar', APPLIED:'Postulada', DISCARDED:'Descartar' };
const matchComponents = [
  ['ROLE_RESPONSIBILITIES','Rol y responsabilidades',25],
  ['TECHNOLOGIES_KNOWLEDGE','Tecnologías y conocimientos',25],
  ['SENIORITY_EXPERIENCE','Seniority y experiencia',20],
  ['RELEVANT_PROJECTS','Proyectos relevantes',15],
  ['EXPERIENCE_TYPE','Tipo de experiencia',10],
  ['PREFERENCES_QUALITY','Preferencias y vigencia',5],
] as const;
const classificationLabel:Record<string,string>={EXCELLENT:'Compatibilidad excelente',STRONG:'Compatibilidad alta',POSSIBLE:'Compatibilidad posible',LOW:'Compatibilidad baja'};

function App() {
  const [account, setAccount] = useState<Account | null | undefined>();
  useEffect(() => { api<Account>('/me/account').then(setAccount).catch(() => setAccount(null)); }, []);
  useEffect(() => {
    const redirectToLogin = () => { resetCsrf(); window.history.replaceState({}, '', '/'); setAccount(null); };
    window.addEventListener('jobmatch:session-expired', redirectToLogin);
    return () => window.removeEventListener('jobmatch:session-expired', redirectToLogin);
  }, []);
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

type View = 'recommendations'|'search'|'tracking'|'profile';
type ExploreFilterTab = 'date'|'remoteMode'|'employmentType'|'salary'|'roles';
function Workspace({ account, onLogout }: { account: Account; onLogout: () => void }) {
  const [view, setView] = useState<View>('recommendations'); const [notice, setNotice] = useState<Notice>(); const noticeId=useRef(0);
  const notify=useCallback((message:string)=>{const normalized=message.trim();if(normalized)setNotice({id:++noticeId.current,message:normalized});},[]);
  useEffect(()=>{if(!notice)return;const timer=window.setTimeout(()=>setNotice(undefined),5000);return()=>window.clearTimeout(timer);},[notice?.id]);
  function navigate(next:View){setView(next);setNotice(undefined);}
  async function logout() { await api('/auth/logout', { method:'POST' }); resetCsrf(); onLogout(); }
  const titles: Record<View,string>={recommendations:'Recomendaciones',search:'Explorar vacantes',tracking:'Tu seguimiento',profile:'Perfil profesional'};
  return <div className="app-shell"><aside><div><p className="eyebrow">JobMatch</p><h2>Hola</h2><small>{account.email}</small></div><nav>{([['recommendations','Para ti'],['search','Explorar'],['tracking','Seguimiento'],['profile','Perfil']] as [View,string][]).map(([id,label])=><button key={id} className={view===id?'active':''} onClick={()=>navigate(id)}>{label}</button>)}</nav><button className="quiet" onClick={logout}>Cerrar sesión</button></aside><div className="workspace"><header><div><p className="eyebrow">Panel personal</p><h1>{titles[view]}</h1></div></header>{notice && <div className="toast" role="status" aria-live="polite" aria-atomic="true"><span>{notice.message}</span><button type="button" aria-label="Cerrar notificación" onClick={()=>setNotice(undefined)}>×</button></div>}{view==='recommendations' && <JobsView notify={notify} />}{view==='search' && <ExploreView notify={notify} />}{view==='tracking' && <TrackingView notify={notify} />}{view==='profile' && <ProfileView notify={notify} />}</div></div>;
}

function JobsView({ notify }: { notify:(value:string)=>void }) {
  const [jobs, setJobs] = useState<Job[]>([]); const [activity, setActivity] = useState<Record<string,Activity>>({});
  const [loading, setLoading] = useState(true); const [error, setError] = useState('');
  const [recommendationStatus,setRecommendationStatus]=useState<'UPDATING'|'READY'>('READY');
  const [selectedId,setSelectedId]=useState<string>(); const [detail,setDetail]=useState<JobDetail>(); const [detailLoading,setDetailLoading]=useState(false);
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
  useEffect(()=>{if(!jobs.length){setSelectedId(undefined);return;}setSelectedId(current=>jobs.some(job=>(job.jobId||job.id)===current)?current:(jobs[0].jobId||jobs[0].id));},[jobs]);
  useEffect(()=>{if(!selectedId){setDetail(undefined);return;}let cancelled=false;setDetailLoading(true);void api<JobDetail>(`/jobs/${selectedId}`).then(result=>{if(cancelled)return;setDetail(result);void api('/me/job-impressions',{method:'POST',body:JSON.stringify({jobIds:[selectedId]})}).then(()=>{if(!cancelled)setActivity(values=>markViewed(values,[selectedId]));}).catch(failure=>{if(!cancelled)notify(errorMessage(failure));});}).catch(failure=>{if(!cancelled)notify(errorMessage(failure));}).finally(()=>{if(!cancelled)setDetailLoading(false);});return()=>{cancelled=true;};},[selectedId,notify]);
  async function refreshActivity(jobId:string){const found=await api<Activity[]>(`/me/job-activity?jobId=${jobId}`);setActivity(values=>({...values,[jobId]:found[0]}));}
  async function transition(jobId:string, state:TrackingState) {
    const current=activity[jobId];
    try { const tracked=await api<TrackedJob>(`/me/jobs/${jobId}/tracking`, { method:'PUT', headers:{'If-Match':String(current?.version||0),'Idempotency-Key':crypto.randomUUID()}, body:JSON.stringify({state}) }); setActivity(values=>({...values,[jobId]:{...values[jobId],jobId,new:false,trackingId:tracked.id,state:tracked.state,version:tracked.version,note:tracked.note}})); notify(`Vacante actualizada: ${stateLabel[state]}.`); }
    catch (failure) { if (failure instanceof ApiError && failure.problem.code==='VERSION_CONFLICT') await refreshActivity(jobId); notify(errorMessage(failure)); }
  }
  async function allowRecommendation(jobId:string){const current=activity[jobId];if(!current?.trackingId||current.state!=='DISCARDED'||current.version==null)return;try{await api(`/me/jobs/${jobId}/tracking`,{method:'DELETE',headers:{'If-Match':String(current.version)}});setActivity(values=>({...values,[jobId]:{...values[jobId],jobId,new:false,trackingId:undefined,state:undefined,version:undefined,note:undefined}}));notify('La vacante podrá volver a recomendarse.');}catch(failure){if(failure instanceof ApiError&&failure.problem.code==='VERSION_CONFLICT')await refreshActivity(jobId);notify(errorMessage(failure));}}
  const selectedActivity=selectedId?activity[selectedId]:undefined;
  return <section>{loading?<Loading label="Buscando oportunidades"/>:error?<Retry message={error} onRetry={()=>void load()}/>:recommendationStatus==='UPDATING'?<div className="recommendation-updating"><span></span><div><h3>Actualizando tus recomendaciones</h3><p>Aplicamos los cambios de tu perfil. Esta lista se reemplazará automáticamente cuando termine.</p></div></div>:jobs.length===0?<Empty title="Aún no hay recomendaciones" detail="Agrega al menos un rol objetivo en tu perfil o espera a que existan vacantes compatibles."/>:<div className="explore-workspace"><div className="job-stack"><div className="job-stack-head"><strong>{jobs.length} vacantes</strong><span>Para ti</span></div>{jobs.map(job=>{const id=job.jobId||job.id!;return <DiscoveryRow key={id} job={job} activity={activity[id]} selected={selectedId===id} onSelect={()=>setSelectedId(id)}/>})}</div><ExploreDetail detail={detail} loading={detailLoading} activity={selectedActivity} onTransition={state=>{if(selectedId)void transition(selectedId,state);}} onAllowRecommendation={()=>{if(selectedId)void allowRecommendation(selectedId);}}/></div>}</section>;
}

const emptySearchFilters = (): SearchFilters => ({ query:'', roles:[], remoteModes:[], employmentTypes:[], minimumMonthlySalary:'', publishedWithinDays:'' });

function ExploreView({ notify }: { notify:(value:string)=>void }) {
  const [filters,setFilters]=useState<SearchFilters>(emptySearchFilters);
  const [pages,setPages]=useState<CursorPage<Job>[]>([]); const [pageIndex,setPageIndex]=useState(0);
  const [selectedId,setSelectedId]=useState<string>(); const [detail,setDetail]=useState<JobDetail>();
  const [activity,setActivity]=useState<Record<string,Activity>>({}); const [loading,setLoading]=useState(true); const [detailLoading,setDetailLoading]=useState(false);
  const [error,setError]=useState(''); const [roleTerm,setRoleTerm]=useState(''); const [roleSuggestions,setRoleSuggestions]=useState<CatalogRole[]>([]); const [activeFilterTab,setActiveFilterTab]=useState<ExploreFilterTab>('date');
  const [searchDisplay,setSearchDisplay]=useState('');const [savedSearches,setSavedSearches]=useState<SavedSearch[]>([]);const [catalogRoles,setCatalogRoles]=useState<CatalogRole[]>([]);const [savedSearchOpen,setSavedSearchOpen]=useState(false);const [appliedSavedSearchName,setAppliedSavedSearchName]=useState('');const [saveDialogOpen,setSaveDialogOpen]=useState(false);const [saveName,setSaveName]=useState('');const searchFieldRef=useRef<HTMLLabelElement>(null);
  const currentPage=pages[pageIndex]; const jobs=currentPage?.items||[];

  async function fetchPage(active:SearchFilters,cursor?:string){
    const params=new URLSearchParams();
    if(active.query.trim())params.set('q',active.query.trim());
    active.roles.forEach(role=>params.append('roleFamilyId',role.id));
    active.remoteModes.forEach(mode=>params.append('remoteMode',mode));
    active.employmentTypes.forEach(type=>params.append('employmentType',type));
    if(active.minimumMonthlySalary.trim())params.set('minimumMonthlySalary',active.minimumMonthlySalary.trim());
    if(active.publishedWithinDays)params.set('publishedWithinDays',active.publishedWithinDays);
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
  useEffect(()=>{if(!selectedId){setDetail(undefined);return;}let cancelled=false;setDetailLoading(true);void api<JobDetail>(`/jobs/${selectedId}`).then(result=>{if(cancelled)return;setDetail(result);void api('/me/job-impressions',{method:'POST',body:JSON.stringify({jobIds:[selectedId]})}).then(()=>{if(!cancelled)setActivity(values=>markViewed(values,[selectedId]));}).catch(failure=>{if(!cancelled)notify(errorMessage(failure));});}).catch(failure=>{if(!cancelled)notify(errorMessage(failure));}).finally(()=>{if(!cancelled)setDetailLoading(false);});return()=>{cancelled=true;};},[selectedId,notify]);

  function updateFilter<K extends keyof SearchFilters>(key:K,value:SearchFilters[K]){setFilters(current=>({...current,[key]:value}));}
  function toggle(key:'remoteModes'|'employmentTypes',value:string){setFilters(current=>({...current,[key]:current[key].includes(value)?current[key].filter(item=>item!==value):[...current[key],value]}));}
  function addRole(role:CatalogRole){if(filters.roles.length>=10)return;setFilters(current=>({...current,roles:[...current.roles,role]}));setRoleTerm('');setRoleSuggestions([]);}
  async function transition(jobId:string,state:TrackingState){
    const current=activity[jobId];
    try {
      const tracked=await api<TrackedJob>(`/me/jobs/${jobId}/tracking`,{method:'PUT',headers:{'If-Match':String(current?.version||0),'Idempotency-Key':crypto.randomUUID()},body:JSON.stringify({state})});
      setActivity(values=>({...values,[jobId]:{...values[jobId],jobId,new:false,trackingId:tracked.id,state:tracked.state,version:tracked.version,note:tracked.note}}));
      notify(`Vacante actualizada: ${stateLabel[state]}.`);
    } catch(failure){if(failure instanceof ApiError&&failure.problem.code==='VERSION_CONFLICT')await populateActivity(jobs);notify(errorMessage(failure));}
  }
  async function allowRecommendation(jobId:string){
    const current=activity[jobId];
    if(!current?.trackingId || current.state!=='DISCARDED' || current.version==null)return;
    try {
      await api(`/me/jobs/${jobId}/tracking`,{method:'DELETE',headers:{'If-Match':String(current.version)}});
      setActivity(values=>({...values,[jobId]:{...values[jobId],jobId,new:false,trackingId:undefined,state:undefined,version:undefined,note:undefined}}));
      notify('La vacante podrá volver a recomendarse.');
    } catch(failure){if(failure instanceof ApiError&&failure.problem.code==='VERSION_CONFLICT')await populateActivity(jobs);notify(errorMessage(failure));}
  }
  async function saveSearch(){
    const name=saveName.trim();if(!name)return;
    try {await api('/me/saved-searches',{method:'POST',body:JSON.stringify({name,criteria:criteria(filters)})});setSaveDialogOpen(false);setSaveName('');await loadSavedSearches();notify('Búsqueda guardada.');}
    catch(failure){notify(errorMessage(failure));}
  }
  async function applySaved(item:SavedSearch){const c=item.criteria;const next:SearchFilters={query:c.query||'',roles:(c.roleFamilyIds||[]).map(id=>catalogRoles.find(role=>role.id===id)||{id,name:'Rol guardado'}),remoteModes:c.remoteModes||[],employmentTypes:c.employmentTypes||[],minimumMonthlySalary:c.minimumMonthlySalary?.toString()||'',publishedWithinDays:c.publishedWithinDays?.toString()||''};setSearchDisplay(item.name);setAppliedSavedSearchName(item.name);setSavedSearchOpen(false);setFilters(next);await search(next);}
  async function renameSaved(item:SavedSearch){const name=window.prompt('Nuevo nombre de la búsqueda',item.name)?.trim();if(!name||name===item.name)return;try{await api(`/me/saved-searches/${item.id}`,{method:'PUT',headers:{'If-Match':String(item.version)},body:JSON.stringify({name,criteria:item.criteria})});await loadSavedSearches();notify('Búsqueda renombrada.');}catch(failure){notify(errorMessage(failure));}}
  async function deleteSaved(item:SavedSearch){if(!window.confirm(`¿Eliminar la búsqueda “${item.name}”?`))return;try{await api(`/me/saved-searches/${item.id}`,{method:'DELETE',headers:{'If-Match':String(item.version)}});await loadSavedSearches();notify('Búsqueda eliminada.');}catch(failure){notify(errorMessage(failure));}}
  useEffect(()=>{const close=(event:PointerEvent)=>{if(!searchFieldRef.current?.contains(event.target as Node))setSavedSearchOpen(false);};document.addEventListener('pointerdown',close);return()=>document.removeEventListener('pointerdown',close);},[]);
  function clear(){const next=emptySearchFilters();setSearchDisplay('');setAppliedSavedSearchName('');setFilters(next);void search(next);}
  const selectedActivity=selectedId?activity[selectedId]:undefined;
  return <section className="explore-page">
    <form className="explore-filters" onSubmit={event=>{event.preventDefault();void search();}}>
      <div className="filter-main"><label className="search-field" ref={searchFieldRef}>Busca por rol, tecnología o empresa<input value={searchDisplay} onFocus={()=>{if(!filters.query.trim()||appliedSavedSearchName)setSavedSearchOpen(true)}} onClick={()=>{if(!filters.query.trim()||appliedSavedSearchName)setSavedSearchOpen(true)}} onChange={event=>{const value=event.target.value;setSearchDisplay(value);setAppliedSavedSearchName('');updateFilter('query',value);setSavedSearchOpen(!value.trim())}} placeholder="Java, React, empresa…"/>{savedSearchOpen&&savedSearches.length>0&&<div className="saved-search-suggestions"><small>Búsquedas guardadas</small>{savedSearches.map(item=><article key={item.id}><button type="button" className="saved-search-apply" onMouseDown={event=>event.preventDefault()} onClick={()=>void applySaved(item)}>{item.name}</button><div><button type="button" aria-label={`Renombrar ${item.name}`} onMouseDown={event=>event.preventDefault()} onClick={()=>void renameSaved(item)}>Renombrar</button><button type="button" className="quiet" aria-label={`Eliminar ${item.name}`} onMouseDown={event=>event.preventDefault()} onClick={()=>void deleteSaved(item)}>Eliminar</button></div></article>)}</div>}</label><button className="primary">Aplicar filtros</button><button type="button" onClick={()=>{setSaveName(filters.query.trim()||'Mi búsqueda');setSaveDialogOpen(true)}}>Guardar</button><button type="button" className="quiet" onClick={clear}>Limpiar</button></div>
      <div className="filter-tabs" role="tablist" aria-label="Filtros de vacantes">{([['date','Fecha'],['remoteMode','Modalidad'],['employmentType','Tipo de empleo'],['salary','Salario'],['roles','Roles']] as [ExploreFilterTab,string][]).map(([tab,label])=><button type="button" key={tab} role="tab" aria-selected={activeFilterTab===tab} className={activeFilterTab===tab?'active':''} onClick={()=>setActiveFilterTab(tab)}>{label}</button>)}</div>
      <div className="filter-tab-panel" role="tabpanel">
        {activeFilterTab==='date'&&<label>Fecha<select value={filters.publishedWithinDays} onChange={event=>updateFilter('publishedWithinDays',event.target.value)}><option value="">Cualquier fecha</option><option value="1">Últimas 24 horas</option><option value="7">Última semana</option><option value="30">Último mes</option><option value="90">Últimos 3 meses</option></select></label>}
        {activeFilterTab==='remoteMode'&&<FilterChoices label="Modalidad" values={filters.remoteModes} options={[['REMOTE','Remoto'],['HYBRID','Híbrido'],['ONSITE','Presencial']]} onToggle={value=>toggle('remoteModes',value)}/>}
        {activeFilterTab==='employmentType'&&<FilterChoices label="Tipo de empleo" values={filters.employmentTypes} options={[['FULL_TIME','Tiempo completo'],['PART_TIME','Medio tiempo'],['CONTRACT','Contrato'],['INTERNSHIP','Prácticas']]} onToggle={value=>toggle('employmentTypes',value)}/>}
        {activeFilterTab==='salary'&&<label>Salario mensual desde <input type="number" min="0" value={filters.minimumMonthlySalary} onChange={event=>updateFilter('minimumMonthlySalary',event.target.value)} placeholder="30000"/></label>}
        {activeFilterTab==='roles'&&<div className="picker"><label>Roles <small>Hasta 10</small><input value={roleTerm} disabled={filters.roles.length>=10} onChange={event=>setRoleTerm(event.target.value)} placeholder="Escribe al menos dos letras"/></label>{roleSuggestions.length>0&&<div className="suggestions">{roleSuggestions.map(role=><button type="button" key={role.id} onClick={()=>addRole(role)}><span>{role.name}</span></button>)}</div>}<div className="read-chips">{filters.roles.map(role=><button type="button" key={role.id} onClick={()=>updateFilter('roles',filters.roles.filter(item=>item.id!==role.id))}>{role.name} ×</button>)}</div></div>}
      </div>
    </form>{saveDialogOpen&&<div className="modal-backdrop" onMouseDown={event=>{if(event.target===event.currentTarget)setSaveDialogOpen(false)}}><form className="save-search-dialog" role="dialog" aria-modal="true" aria-labelledby="save-search-title" onSubmit={event=>{event.preventDefault();void saveSearch()}}><div className="modal-head"><h2 id="save-search-title">Guardar búsqueda</h2><button type="button" className="quiet modal-close" aria-label="Cerrar" onClick={()=>setSaveDialogOpen(false)}>×</button></div><p>Guarda el texto y todos los filtros seleccionados para reutilizarlos después.</p><label>Nombre de la búsqueda<input autoFocus value={saveName} maxLength={120} onChange={event=>setSaveName(event.target.value)} placeholder="Ej. Backend remoto México" required/></label><div className="actions"><button type="button" className="quiet" onClick={()=>setSaveDialogOpen(false)}>Cancelar</button><button className="primary">Guardar búsqueda</button></div></form></div>}
    {loading?<Loading label="Buscando vacantes"/>:error?<Retry message={error} onRetry={()=>void search()}/>:!jobs.length?<Empty title="No encontramos vacantes" detail="Prueba con menos filtros o términos más amplios."/>:<div className="explore-workspace">
      <div className="job-stack"><div className="job-stack-head"><strong>{jobs.length} vacantes</strong><span>Página {pageIndex+1}</span></div>{jobs.map(job=>job.id&&<DiscoveryRow key={job.id} job={job} activity={activity[job.id]} selected={selectedId===job.id} onSelect={()=>setSelectedId(job.id!)}/>)}<div className="pagination"><button type="button" disabled={pageIndex===0} onClick={()=>{const previous=pages[pageIndex-1];if(previous){void populateActivity(previous.items);setSelectedId(previous.items[0]?.id);}setPageIndex(index=>index-1);}}>Anterior</button><span>{pageIndex+1}</span><button type="button" disabled={!currentPage?.nextCursor} onClick={()=>void search(filters,currentPage?.nextCursor,true)}>Siguiente</button></div></div>
      <ExploreDetail detail={detail} loading={detailLoading} activity={selectedActivity} onTransition={state=>{if(selectedId)void transition(selectedId,state);}} onAllowRecommendation={()=>{if(selectedId)void allowRecommendation(selectedId);}}/>
    </div>}</section>;
}

function FilterChoices({label,values,options,onToggle}:{label:string;values:string[];options:[string,string][];onToggle:(value:string)=>void}){
  return <fieldset className="choice-field"><legend>{label}</legend>{options.map(([value,name])=><label key={value}><input type="checkbox" checked={values.includes(value)} onChange={()=>onToggle(value)}/>{name}</label>)}</fieldset>;
}

function ExploreDetail({detail,loading,activity,onTransition,onAllowRecommendation}:{detail?:JobDetail;loading:boolean;activity?:Activity;onTransition:(state:TrackingState)=>void;onAllowRecommendation:()=>void}){
  const [match,setMatch]=useState<Match>();const [matchError,setMatchError]=useState('');const [matchAttempt,setMatchAttempt]=useState(0);
  useEffect(()=>{if(!detail?.id){setMatch(undefined);setMatchError('');return;}let cancelled=false;setMatch(undefined);setMatchError('');void api<Match>(`/jobs/${detail.id}/match`).then(result=>{if(!cancelled)setMatch(result);}).catch(failure=>{if(!cancelled)setMatchError(errorMessage(failure));});return()=>{cancelled=true;};},[detail?.id,matchAttempt]);
  if(loading)return <div className="job-detail"><Loading label="Cargando detalle"/></div>;
  if(!detail)return <div className="job-detail job-detail-empty"><Empty title="Selecciona una vacante" detail="Su descripción y requisitos aparecerán aquí."/></div>;
  const available:TrackingState[]=activity?.state==='DISCARDED'?[]:activity?.state==='SAVED'?['APPLIED','DISCARDED']:activity?.state==='APPLIED'?[]:['SAVED','APPLIED','DISCARDED'];
  const salary=salaryText(detail);const location=detail.locations.map(item=>[item.city,item.state,item.countryCode].filter(Boolean).join(', ')).join(' · ');
  return <article className="job-detail"><header><div><p className="eyebrow">{detail.employer}</p><h2>{detail.title}</h2><p className="job-facts">{[detail.roleFamily,detail.seniority,detail.remoteMode,detail.employmentType].filter(Boolean).join(' · ')}</p></div>{activity?.state&&<span className={`state state-${activity.state.toLowerCase()}`}>{stateLabel[activity.state]}</span>}</header><div className="detail-actions">{activity?.state==='DISCARDED'?<button className="primary" onClick={onAllowRecommendation}>Permitir recomendar</button>:available.map(state=><button key={state} onClick={()=>onTransition(state)}>{explorationActionLabel[state]}</button>)}</div><dl className="job-detail-facts"><div><dt>Ubicación</dt><dd>{location||'No especificada'}</dd></div><div><dt>Salario</dt><dd>{salary||'No especificado'}</dd></div><div><dt>Publicada</dt><dd>{publicationLabel(detail.publishedAt)||'No especificada'}</dd></div></dl>{match&&<Compatibility match={match}/>} {matchError&&<section className="match-error" role="status"><p>No pudimos calcular la compatibilidad de esta vacante.</p><small>{matchError}</small><button type="button" onClick={()=>setMatchAttempt(value=>value+1)}>Reintentar</button></section>}<section><h3>Descripción</h3><DescriptionText value={detail.description}/></section>{detail.requirements.length>0&&<section><h3>Requisitos</h3><ul className="detail-list">{detail.requirements.map(item=><li key={item.id}>{item.text}{item.mandatory&&<strong> Obligatorio</strong>}</li>)}</ul></section>}{detail.skillRequirements.length>0&&<section><h3>Habilidades</h3><div className="read-chips">{detail.skillRequirements.map(item=><span key={item.id}>{item.skill}</span>)}</div></section>}{detail.sourceLinks.length>0&&<section><h3>Postularse</h3><div className="source-links">{detail.sourceLinks.map(link=><a key={link.postingId} href={link.url} target="_blank" rel="noreferrer">{link.source}{link.preferred?' · Principal':''}</a>)}</div></section>}</article>;
}
function DescriptionText({value}:{value:string}){return <div className="long-copy formatted-description">{value.split(/\n+/).map(line=>line.trim()).filter(Boolean).map((line,index)=>line.startsWith('• ')?<div key={index} className="description-bullet">{line.slice(2)}</div>:<p key={index} className={isDescriptionHeading(line)?'description-heading':''}>{isDescriptionHeading(line)?<strong>{line}</strong>:line}</p>)}</div>}
function isDescriptionHeading(line:string){const text=line.replace(/:$/,'').trim();return text.length<=90&&(/^(about|responsibilities|requirements|qualifications|benefits|what you|the role|the team|sobre|responsabilidades|requisitos|calificaciones|beneficios|el puesto|el equipo)/i.test(text)||/^(what|why|who|cómo|que|por qué)\b/i.test(text));}

function Compatibility({match}:{match:Match}){
  const groups=[
    ['MATCH','Coincidencias','✓'],
    ['GAP','Brechas','!'],
    ['CONSIDERATION','Consideraciones','i'],
  ] as const;
  const counts=Object.fromEntries(groups.map(([type])=>[type,match.reasons.filter(reason=>reason.type===type).length])) as Record<string,number>;
  return <section className="compatibility"><div className="compatibility-head"><div><p className="eyebrow">Compatibilidad con tu perfil</p><h3>{classificationLabel[match.classification]||'Compatibilidad evaluada'}</h3></div><div className="compatibility-score"><strong>{Math.round(Number(match.score))}</strong><span>/100</span></div></div><div className="compatibility-counts">{groups.map(([type,label,icon])=>counts[type]>0&&<span key={type} className={type.toLowerCase()}><b>{icon}</b> {counts[type]} {label.toLowerCase()}</span>)}</div><div className="component-scores">{matchComponents.map(([key,label,maximum])=>{const score=Number(match.components?.[key]);if(!Number.isFinite(score))return null;const percent=Math.max(0,Math.min(100,(score/maximum)*100));return <div key={key}><div><span>{label}</span><strong>{Math.round(score * 10) / 10} / {maximum}</strong></div><span className="component-track" aria-label={`${label}: ${score} de ${maximum}`}><i style={{width:`${percent}%`}}/></span></div>;})}</div>{groups.map(([type,label,icon])=>{const reasons=match.reasons.filter(reason=>reason.type===type);return reasons.length>0&&<ReasonGroup key={type} type={type} label={label} icon={icon} reasons={reasons}/>})}</section>;
}

function ReasonGroup({type,label,icon,reasons}:{type:'MATCH'|'GAP'|'CONSIDERATION';label:string;icon:string;reasons:Match['reasons']}){const skillReasons=type==='MATCH'?reasons.filter(isSkillMatch):[];const otherReasons=type==='MATCH'?reasons.filter(reason=>!isSkillMatch(reason)):reasons;const entries=[...(skillReasons.length?[<SkillEvidenceGroup key="skills" reasons={skillReasons}/>] as React.ReactNode[]:[]),...otherReasons.map(reason=><Reason key={reason.id} reason={reason}/>)];const visible=entries.slice(0,3);const hidden=entries.slice(3);return <section className={`reason-group ${type.toLowerCase()}`}><h4><span>{icon}</span>{label}</h4><ul>{visible}</ul>{hidden.length>0&&<details><summary>Mostrar {hidden.length} {hidden.length===1?'razón más':'razones más'}</summary><ul>{hidden}</ul></details>}</section>;}
function isSkillMatch(reason:Match['reasons'][number]){return reason.component==='TECHNOLOGIES_KNOWLEDGE'&&reason.type==='MATCH'&&typeof reason.evidence?.skill==='string';}
function SkillEvidenceGroup({reasons}:{reasons:Match['reasons']}){return <li className="skill-evidence-group"><span>Tu perfil incluye la habilidad solicitada:</span><ul>{reasons.map(reason=>{const skill=reason.evidence?.skill as string;return <li key={reason.id}><small>Evidencia: <strong>{skill}</strong>{evidenceDetails(reason.evidence)}</small></li>;})}</ul></li>;}
function Reason({reason}:{reason:Match['reasons'][number]}){const evidence=reason.evidence;if(typeof evidence?.targetRole==='string')return <li className="role-evidence"><span>La vacante coincide con tu rol objetivo:</span><strong>{evidence.targetRole}</strong></li>;if(typeof evidence?.profileSeniority==='string')return <li className="seniority-evidence"><span>{headline(reason.explanation)}</span><small>seniority <strong>{evidence.profileSeniority}</strong></small></li>;const projects=projectEvidence(evidence);if(projects.length)return <li className="project-evidence"><span>{headline(reason.explanation)}</span><ul>{projects.map(project=><li key={`${project.name}-${project.skills.join('-')}`}><span>{project.name}:</span>{project.skills.map(skill=><strong key={skill}>{skill}</strong>)}</li>)}</ul></li>;const text=reasonEvidence(evidence);return <li><span>{reason.explanation}</span>{text&&<small>{text}</small>}</li>;}
function headline(value:string){return `${value.replace(/[.:]\s*$/,'')}:`;}
function projectEvidence(evidence?:Record<string,unknown>){if(!Array.isArray(evidence?.projects))return [] as {name:string;skills:string[]}[];return evidence.projects.flatMap(project=>{if(!project||typeof project!=='object')return [];const item=project as Record<string,unknown>;if(typeof item.name!=='string'||!Array.isArray(item.skills))return [];const skills=item.skills.filter((skill):skill is string=>typeof skill==='string'&&skill.length>0);return skills.length?[{name:item.name,skills}]:[];});}
function reasonEvidence(evidence?:Record<string,unknown>){if(!evidence)return '';if(typeof evidence.language==='string'&&typeof evidence.proficiency==='string')return `Evidencia: ${evidence.language}, nivel ${evidence.proficiency}.`;const details=evidenceDetails(evidence);return details?`Evidencia:${details.trim()}.`:'';}
function evidenceDetails(evidence?:Record<string,unknown>){if(!evidence)return '';const parts:string[]=[];if(typeof evidence.professionalMonths==='number'&&evidence.professionalMonths>0)parts.push(`${evidence.professionalMonths} meses profesionales`);if(typeof evidence.weightedPracticalMonths==='number'&&evidence.weightedPracticalMonths>0)parts.push(`${Number(evidence.weightedPracticalMonths)} meses en prácticas o proyectos`);return parts.length?` · ${parts.join(' · ')}`:'';}

function criteria(filters:SearchFilters){return {query:filters.query.trim()||null,roleFamilyIds:filters.roles.map(role=>role.id),remoteModes:filters.remoteModes,employmentTypes:filters.employmentTypes,minimumMonthlySalary:filters.minimumMonthlySalary?Number(filters.minimumMonthlySalary):null,publishedWithinDays:filters.publishedWithinDays?Number(filters.publishedWithinDays):null};}
function salaryText(job:Pick<Job,'salaryMinMonthly'|'salaryMaxMonthly'|'currency'>){if(job.salaryMinMonthly==null&&job.salaryMaxMonthly==null)return '';const currency=job.currency||'MXN';return `${new Intl.NumberFormat('es-MX',{style:'currency',currency,maximumFractionDigits:0}).format(job.salaryMinMonthly||0)} – ${new Intl.NumberFormat('es-MX',{style:'currency',currency,maximumFractionDigits:0}).format(job.salaryMaxMonthly||job.salaryMinMonthly||0)}`;}
function jobFacts(job:Job){return [job.city||job.state,[job.remoteMode,job.employmentType].filter(Boolean).join(' · '),salaryText(job)].filter(Boolean).join(' · ');}

function markViewed(values:Record<string,Activity>,ids:string[]){const next={...values};ids.forEach(jobId=>{if(next[jobId])next[jobId]={...next[jobId],new:false};});return next;}
function discoveryStatus(activity?:Activity){if(activity?.state==='DISCARDED')return 'Descartada';if(activity?.state&&activity.state!=='SAVED')return 'Postulada';return activity?.new===false?'Vista':'';}
function DiscoveryRow({job,activity,selected,onSelect}:{job:Job;activity?:Activity;selected:boolean;onSelect:()=>void}){const status=discoveryStatus(activity);return <button type="button" className={`job-row ${selected?'selected':''}`} onClick={onSelect}><span>{job.employer}</span><strong>{job.title}</strong><small>{jobFacts(job)}</small><div className="job-row-footer"><time>{publicationLabel(job.publishedAt)}</time>{status&&<span className={`job-row-status ${activity?.state ? `job-row-status-${activity.state.toLowerCase()}` : 'job-row-status-viewed'}`}>{status}</span>}</div></button>;}

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

function Loading({label}:{label:string}){return <div className="loading"><span></span><p>{label}…</p></div>}
function Retry({message,onRetry}:{message:string;onRetry:()=>void}){return <div className="empty"><h3>No pudimos cargar esta sección</h3><p>{message}</p><button onClick={onRetry}>Reintentar</button></div>}
function Empty({title,detail}:{title:string;detail:string}){return <div className="empty"><h3>{title}</h3><p>{detail}</p></div>}

createRoot(document.getElementById('root')!).render(<React.StrictMode><App /></React.StrictMode>);
