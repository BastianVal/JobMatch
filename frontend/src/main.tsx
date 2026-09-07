import React, { FormEvent, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { api, ApiError, errorMessage, resetCsrf } from './api';
import { ProfileView } from './profile';
import './styles.css';

type Account = { id: string; email: string; status: string };
type TrackingState = 'SAVED'|'DISCARDED'|'APPLIED'|'INTERVIEW'|'OFFER'|'ACCEPTED'|'REJECTED'|'WITHDRAWN';
type Activity = { jobId: string; new: boolean; trackingId?: string; state?: TrackingState; note?: string; version?: number };
type Job = { id?: string; jobId?: string; title: string; employer: string; seniority?: string; remoteMode?: string; employmentType?: string; score?: number; classification?: string };
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
  return <div className="app-shell"><aside><div><p className="eyebrow">JobMatch</p><h2>Hola</h2><small>{account.email}</small></div><nav>{([['recommendations','Para ti'],['search','Explorar'],['tracking','Seguimiento'],['profile','Perfil'],['cv','Importar CV']] as [View,string][]).map(([id,label])=><button key={id} className={view===id?'active':''} onClick={()=>setView(id)}>{label}</button>)}</nav><button className="quiet" onClick={logout}>Cerrar sesión</button></aside><div className="workspace"><header><div><p className="eyebrow">Panel personal</p><h1>{titles[view]}</h1></div></header>{notice && <div className="toast" role="status">{notice}<button onClick={()=>setNotice('')}>×</button></div>}{view==='recommendations' && <JobsView mode="recommendations" notify={setNotice} />}{view==='search' && <JobsView mode="search" notify={setNotice} />}{view==='tracking' && <TrackingView notify={setNotice} />}{view==='profile' && <ProfileView notify={setNotice} />}{view==='cv' && <CvView notify={setNotice} />}</div></div>;
}

function JobsView({ mode, notify }: { mode:'recommendations'|'search'; notify:(value:string)=>void }) {
  const [jobs, setJobs] = useState<Job[]>([]); const [activity, setActivity] = useState<Record<string,Activity>>({});
  const [loading, setLoading] = useState(true); const [query, setQuery] = useState(''); const [error, setError] = useState('');
  const [recommendationStatus,setRecommendationStatus]=useState<'UPDATING'|'READY'>('READY');
  async function load(search=query,background=false) {
    if(!background)setLoading(true); setError('');
    try {
      let result:Job[];
      if(mode==='recommendations'){
        const feed=await api<RecommendationFeed>('/me/recommendations?limit=50');setRecommendationStatus(feed.status);result=feed.items;
      }else result=(await api<{items:Job[]}>(`/jobs/search?q=${encodeURIComponent(search)}&limit=25`)).items;
      const ids = result.map(job=>job.jobId || job.id!).filter(Boolean);
      if (ids.length) { const params = ids.map(id=>`jobId=${encodeURIComponent(id)}`).join('&'); const before = await api<Activity[]>(`/me/job-activity?${params}`); setActivity(Object.fromEntries(before.map(item=>[item.jobId,item]))); }
      setJobs(result);
    } catch (failure) { setError(errorMessage(failure)); } finally { if(!background)setLoading(false); }
  }
  useEffect(()=>{ void load(''); },[mode]);
  useEffect(()=>{if(mode!=='recommendations'||recommendationStatus!=='UPDATING')return;const timer=setInterval(()=>void load('',true),2000);return()=>clearInterval(timer);},[mode,recommendationStatus]);
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
  return <section>{mode==='search' && <form className="searchbar" onSubmit={event=>{event.preventDefault();void load(query)}}><input value={query} onChange={event=>setQuery(event.target.value)} placeholder="Rol, tecnología o empresa"/><button className="primary">Buscar</button></form>}{loading?<Loading label="Buscando oportunidades"/>:error?<Retry message={error} onRetry={()=>void load()}/>:mode==='recommendations'&&recommendationStatus==='UPDATING'?<div className="recommendation-updating"><span></span><div><h3>Actualizando tus recomendaciones</h3><p>Aplicamos los cambios de tu perfil. Esta lista se reemplazará automáticamente cuando termine.</p></div></div>:jobs.length===0?<Empty title={mode==='search'?'No encontramos vacantes':'Aún no hay recomendaciones'} detail={mode==='search'?'Prueba con términos más amplios.':'Agrega al menos un rol objetivo en tu perfil o espera a que existan vacantes compatibles.'}/>:<div className="job-grid">{jobs.map(job=>{const id=job.jobId||job.id!;return <JobCard key={id} job={job} activity={activity[id]} onTransition={state=>void transition(id,state)} notify={notify}/>})}</div>}</section>;
}

function JobCard({ job, activity, onTransition, notify }: { job:Job; activity?:Activity; onTransition:(state:TrackingState)=>void; notify:(value:string)=>void }) {
  const [match,setMatch]=useState<Match>(); const [busy,setBusy]=useState(false); const id=job.jobId||job.id!;
  const available: TrackingState[]=activity?.state ? transitions[activity.state] : ['SAVED','DISCARDED','APPLIED'];
  async function inspect(){setBusy(true);try{setMatch(await api<Match>(`/jobs/${id}/match`));}catch(e){notify(errorMessage(e));}finally{setBusy(false)}}
  return <article className="job-card"><div className="job-top"><div>{activity?.new&&<span className="new-badge">Nueva</span>}<p className="meta">{job.employer}</p><h3>{job.title}</h3></div>{job.score!==undefined&&<div className="score"><strong>{Math.round(Number(job.score))}</strong><span>/100</span></div>}</div><p className="job-facts">{[job.seniority,job.remoteMode,job.employmentType].filter(Boolean).join(' · ')}</p>{activity?.state&&<span className={`state state-${activity.state.toLowerCase()}`}>{stateLabel[activity.state]}</span>}<div className="actions">{available.map(state=><button key={state} onClick={()=>onTransition(state)}>{stateLabel[state]}</button>)}<button className="quiet" disabled={busy} onClick={()=>void inspect()}>{busy?'Calculando…':'Ver evidencia'}</button></div>{match&&<div className="match-detail"><strong>{Math.round(Number(match.score))}/100 · {match.classification}</strong><ul>{match.reasons.slice(0,5).map(reason=><li key={reason.id} className={reason.type.toLowerCase()}>{reason.explanation}</li>)}</ul></div>}</article>;
}

function TrackingView({ notify }: { notify:(value:string)=>void }) {
  const [items,setItems]=useState<TrackedJob[]>([]);const [loading,setLoading]=useState(true);const [error,setError]=useState('');
  async function load(){setLoading(true);try{setItems(await api<TrackedJob[]>('/me/tracking?limit=100'));setError('');}catch(e){setError(errorMessage(e));}finally{setLoading(false)}}
  useEffect(()=>{void load()},[]);
  async function move(item:TrackedJob,target:TrackingState){try{await api(`/me/jobs/${item.jobId}/tracking`,{method:'PUT',headers:{'If-Match':String(item.version),'Idempotency-Key':crypto.randomUUID()},body:JSON.stringify({state:target,note:item.note})});notify(`Seguimiento actualizado a ${stateLabel[target]}.`);await load();}catch(e){notify(errorMessage(e));await load();}}
  if(loading)return <Loading label="Cargando seguimiento"/>; if(error)return <Retry message={error} onRetry={()=>void load()}/>; if(!items.length)return <Empty title="No hay vacantes en seguimiento" detail="Guarda o registra una postulación desde recomendaciones o búsqueda."/>;
  return <div className="tracking-list">{items.map(item=><article className="tracking-card" key={item.id}><div><span className={`state state-${item.state.toLowerCase()}`}>{stateLabel[item.state]}</span><h3>{item.title}</h3><p>{item.employer}</p></div><div className="actions">{transitions[item.state].map(state=><button key={state} onClick={()=>void move(item,state)}>{stateLabel[state]}</button>)}</div><details><summary>Historial · {item.history.length} eventos</summary><ol>{item.history.map(event=><li key={event.id}><time>{new Date(event.occurredAt).toLocaleDateString('es-MX')}</time> {event.fromState?`${stateLabel[event.fromState]} → `:''}{stateLabel[event.toState]}</li>)}</ol></details></article>)}</div>;
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
