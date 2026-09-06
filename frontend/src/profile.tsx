import { FormEvent, ReactNode, useEffect, useState } from 'react';
import { api, ApiError, errorMessage } from './api';

type TargetRole={id:string;roleFamilyId:string;priority:number;name?:string};
type Preference={remoteMode?:string;employmentType?:string;minimumMonthlySalary?:number|null;currency?:string;willingToRelocate:boolean};
type Trajectory={id:string;type:string;title:string;organization?:string;description?:string;startYear:number;startMonth:number;endYear?:number|null;endMonth?:number|null;current:boolean};
type Education={id:string;institution:string;degree:string;fieldOfStudy?:string;startYear?:number|null;endYear?:number|null};
type Certification={id:string;name:string;issuer?:string;issuedYear?:number|null;credentialId?:string;credentialUrl?:string};
type Language={id:string;code:string;name:string;proficiency:string};
type Skill={id:string;catalogSkillId?:string|null;customName?:string|null;name?:string;proficiency?:string;matchEligible:boolean;evidenceTrajectoryIds:string[];professionalMonths?:number;weightedPracticalMonths?:number};
export type ProfileData={headline?:string;summary?:string;location?:string;seniority?:string;targetRoles:TargetRole[];preferences?:Preference|null;excludedEmployers:string[];trajectory:Trajectory[];education:Education[];certifications:Certification[];languages:Language[];skills:Skill[]};
type Profile={id?:string;version:number;catalogVersion:number;data:ProfileData};
type CatalogEntry={id:string;name:string};
type CvDocument={id:string;originalFilename:string;createdAt:string;latestImportStatus:string};

const seniorities=['INTERN','JUNIOR','MID','SENIOR','LEAD','MANAGER','DIRECTOR'];
const trajectoryTypes=['EMPLOYMENT','INTERNSHIP','TECHNICAL_SOCIAL_SERVICE','TECHNICAL_VOLUNTEERING','PERSONAL_PROJECT','OPEN_SOURCE','ACADEMIC_PROJECT','STUDY'];
const trajectoryLabels:Record<string,string>={EMPLOYMENT:'Empleo',INTERNSHIP:'Prácticas',TECHNICAL_SOCIAL_SERVICE:'Servicio social técnico',TECHNICAL_VOLUNTEERING:'Voluntariado técnico',PERSONAL_PROJECT:'Proyecto personal',OPEN_SOURCE:'Código abierto',ACADEMIC_PROJECT:'Proyecto académico',STUDY:'Estudio'};
const labels:Record<string,string>={INTERN:'Practicante',JUNIOR:'Junior',MID:'Intermedio',SENIOR:'Senior',LEAD:'Lead',MANAGER:'Gerencia',DIRECTOR:'Dirección',REMOTE:'Remoto',HYBRID:'Híbrido',ONSITE:'Presencial',ANY:'Cualquiera',FULL_TIME:'Tiempo completo',PART_TIME:'Medio tiempo',CONTRACT:'Contrato',INTERNSHIP:'Prácticas',BASIC:'Básico',CONVERSATIONAL:'Conversacional',PROFESSIONAL:'Profesional',FLUENT:'Fluido',NATIVE:'Nativo',BEGINNER:'Principiante',INTERMEDIATE:'Intermedio',ADVANCED:'Avanzado',EXPERT:'Experto'};
const text=(value?:string|null,fallback='Sin especificar')=>value?.trim()||fallback;
const label=(value?:string|null)=>value?labels[value]||trajectoryLabels[value]||value:'Sin especificar';
const uid=()=>crypto.randomUUID();
const optionalNumber=(value:string)=>value===''?null:Number(value);
const clone=<T,>(value:T):T=>structuredClone(value);

export function ProfileView({notify}:{notify:(value:string)=>void}){
  const [saved,setSaved]=useState<Profile>();
  const [draft,setDraft]=useState<Profile>();
  const [documents,setDocuments]=useState<CvDocument[]>([]);
  const [editing,setEditing]=useState(false);
  const [loading,setLoading]=useState(true);
  const [saving,setSaving]=useState(false);
  const [errors,setErrors]=useState<Record<string,string>>({});

  async function load(){
    setLoading(true);
    try{
      const [profile,cvs]=await Promise.all([api<Profile>('/me/profile'),api<CvDocument[]>('/me/cv-documents')]);
      const normalized={...profile,data:normalize(profile.data)};
      setSaved(normalized);setDraft(clone(normalized));setDocuments(cvs);
    }catch(failure){notify(errorMessage(failure));}
    finally{setLoading(false);}
  }
  useEffect(()=>{void load()},[]);
  if(loading||!saved||!draft)return <Loading label="Cargando perfil"/>;
  const currentSaved=saved,currentDraft=draft;

  function beginEdit(){setDraft(clone(currentSaved));setErrors({});setEditing(true);}
  function cancel(){setDraft(clone(currentSaved));setErrors({});setEditing(false);}
  async function save(event:FormEvent){
    event.preventDefault();const found=validateProfile(currentDraft.data);setErrors(found);if(Object.keys(found).length)return;
    setSaving(true);
    try{
      const result=await api<Profile>('/me/profile',{method:'PUT',headers:{'If-Match':String(currentSaved.version)},body:JSON.stringify(currentDraft.data)});
      const normalized={...result,data:normalize(result.data)};setSaved(normalized);setDraft(clone(normalized));setEditing(false);
      notify('Perfil guardado; las recomendaciones se actualizarán automáticamente.');
    }catch(failure){
      if(failure instanceof ApiError&&failure.problem.fieldErrors)setErrors(failure.problem.fieldErrors);
      notify(errorMessage(failure));if(failure instanceof ApiError&&failure.problem.code==='VERSION_CONFLICT')await load();
    }finally{setSaving(false);}
  }
  return editing
    ? <ProfileEditor profile={currentDraft} setProfile={setDraft} errors={errors} saving={saving} onSave={save} onCancel={cancel}/>
    : <ProfileRead profile={currentSaved} documents={documents} onEdit={beginEdit}/>;
}

function normalize(data:ProfileData):ProfileData{return {...data,targetRoles:data.targetRoles||[],excludedEmployers:data.excludedEmployers||[],trajectory:data.trajectory||[],education:data.education||[],certifications:data.certifications||[],languages:data.languages||[],skills:data.skills||[]};}

function ProfileRead({profile,documents,onEdit}:{profile:Profile;documents:CvDocument[];onEdit:()=>void}){
  const d=profile.data,p=d.preferences;
  return <div className="profile-page">
    <div className="profile-toolbar"><div><p className="profile-kicker">Perfil versión {profile.version}</p><p>Esta información alimenta tus recomendaciones.</p></div><button className="primary" onClick={onEdit}>Editar perfil</button></div>
    <section className="profile-hero"><div><span className="profile-avatar" aria-hidden="true">{(d.headline||'P').charAt(0).toUpperCase()}</span><div><h2>{text(d.headline,'Perfil profesional')}</h2><p>{[text(d.location,''),label(d.seniority)].filter(Boolean).join(' · ')||'Agrega tu ubicación y seniority'}</p></div></div><p className="profile-summary">{text(d.summary,'Agrega un resumen que explique tu experiencia, fortalezas y objetivos.')}</p></section>
    <div className="profile-columns">
      <div className="profile-main">
        <ReadSection title="Roles objetivo" empty="Aún no seleccionas roles.">{d.targetRoles.map(role=><div className="ranked-item" key={role.id}><span>{role.priority}</span><strong>{role.name||role.roleFamilyId}</strong></div>)}</ReadSection>
        <ReadSection title="Trayectoria" empty="Aún no agregas experiencia o proyectos.">{d.trajectory.map(item=><article className="timeline-item" key={item.id}><p className="meta">{trajectoryLabels[item.type]||item.type} · {period(item)}</p><h3>{item.title}</h3><p>{text(item.organization,'Sin organización')}</p>{item.description&&<p className="long-copy">{item.description}</p>}</article>)}</ReadSection>
        <ReadSection title="Educación" empty="Aún no agregas estudios.">{d.education.map(item=><article className="profile-entry" key={item.id}><h3>{item.degree}{item.fieldOfStudy?` · ${item.fieldOfStudy}`:''}</h3><p>{item.institution} · {yearRange(item.startYear,item.endYear)}</p></article>)}</ReadSection>
        <ReadSection title="Certificaciones y cursos" empty="Aún no agregas certificaciones.">{d.certifications.map(item=><article className="profile-entry" key={item.id}><h3>{item.name}</h3><p>{[item.issuer,item.issuedYear].filter(Boolean).join(' · ')||'Sin detalles'}</p>{item.credentialUrl&&<a href={item.credentialUrl} target="_blank" rel="noreferrer">Ver credencial</a>}</article>)}</ReadSection>
      </div>
      <div className="profile-side">
        <ReadSection title="Preferencias"><dl className="facts"><div><dt>Modalidad</dt><dd>{label(p?.remoteMode)}</dd></div><div><dt>Tipo de empleo</dt><dd>{label(p?.employmentType)}</dd></div><div><dt>Salario mínimo</dt><dd>{p?.minimumMonthlySalary?money(p.minimumMonthlySalary,p.currency):'Sin especificar'}</dd></div><div><dt>Reubicación</dt><dd>{p?.willingToRelocate?'Disponible':'No disponible'}</dd></div></dl></ReadSection>
        <ReadSection title="Habilidades" empty="Aún no agregas habilidades."><div className="read-chips">{d.skills.map(skill=><span key={skill.id}>{skill.name||skill.customName} · {label(skill.proficiency)}{skill.matchEligible?'':' · personalizada'}</span>)}</div></ReadSection>
        <ReadSection title="Idiomas" empty="Aún no agregas idiomas.">{d.languages.map(item=><div className="split-line" key={item.id}><strong>{item.name}</strong><span>{label(item.proficiency)}</span></div>)}</ReadSection>
        <ReadSection title="Empresas excluidas" empty="No tienes empresas excluidas."><div className="read-chips muted">{d.excludedEmployers.map(name=><span key={name}>{name}</span>)}</div></ReadSection>
        <ReadSection title="CV registrados" empty="Todavía no has importado un CV.">{documents.map(cv=><div className="document-row" key={cv.id}><div><strong>{cv.originalFilename}</strong><small>{new Date(cv.createdAt).toLocaleDateString('es-MX')}</small></div><span>{cv.latestImportStatus}</span></div>)}</ReadSection>
      </div>
    </div>
  </div>;
}

function ReadSection({title,empty,children}:{title:string;empty?:string;children?:ReactNode}){const present=Array.isArray(children)?children.length>0:Boolean(children);return <section className="profile-section"><h2>{title}</h2>{present?children:<p className="section-empty">{empty}</p>}</section>;}

function ProfileEditor({profile,setProfile,errors,saving,onSave,onCancel}:{profile:Profile;setProfile:(value:Profile)=>void;errors:Record<string,string>;saving:boolean;onSave:(event:FormEvent)=>void;onCancel:()=>void}){
  const d=profile.data;
  const change=(patch:Partial<ProfileData>)=>setProfile({...profile,data:{...d,...patch}});
  const list=<K extends keyof ProfileData>(key:K,value:ProfileData[K])=>change({[key]:value} as Pick<ProfileData,K>);
  return <form className="profile-editor" onSubmit={onSave} noValidate>
    <div className="profile-toolbar sticky"><div><h2>Editar perfil</h2><p>Los cambios sólo se aplican al guardar.</p></div><div className="actions"><button type="button" onClick={onCancel}>Cancelar</button><button className="primary" disabled={saving}>{saving?'Guardando…':'Guardar cambios'}</button></div></div>
    {errors.form&&<p className="field-error" role="alert">{errors.form}</p>}
    <EditSection title="Información profesional"><div className="form-grid"><Field label="Titular" error={errors.headline}><input value={d.headline||''} maxLength={160} onChange={e=>change({headline:e.target.value})}/></Field><Field label="Ubicación"><input value={d.location||''} maxLength={160} onChange={e=>change({location:e.target.value})}/></Field><Field label="Seniority"><select value={d.seniority||''} onChange={e=>change({seniority:e.target.value})}><option value="">Sin especificar</option>{seniorities.map(v=><option key={v} value={v}>{label(v)}</option>)}</select></Field></div><Field label="Resumen" hint={`${(d.summary||'').length}/3000`}><textarea value={d.summary||''} maxLength={3000} rows={8} onChange={e=>change({summary:e.target.value})}/></Field></EditSection>
    <EditSection title="Roles objetivo" description="Busca por nombre o tecnología. El orden define la prioridad."><RolePicker roles={d.targetRoles} onChange={roles=>list('targetRoles',roles)}/>{errors.targetRoles&&<p className="field-error">{errors.targetRoles}</p>}</EditSection>
    <EditSection title="Preferencias"><Preferences value={d.preferences||{willingToRelocate:false,currency:'MXN'}} onChange={value=>change({preferences:value})}/></EditSection>
    <EditSection title="Empresas excluidas" description="Estas empresas no aparecerán en Para ti."><TagEditor values={d.excludedEmployers} placeholder="Escribe una empresa y presiona Agregar" limit={50} onChange={values=>list('excludedEmployers',values)}/></EditSection>
    <EditSection title="Trayectoria"><div className="repeat-list">{d.trajectory.map((item,index)=><TrajectoryEditor key={item.id} value={item} error={errors[`trajectory.${index}`]} onChange={value=>list('trajectory',replace(d.trajectory,index,value))} onRemove={()=>list('trajectory',remove(d.trajectory,index))}/>)}</div><button type="button" onClick={()=>list('trajectory',[...d.trajectory,newTrajectory()])}>+ Agregar trayectoria</button></EditSection>
    <EditSection title="Educación"><div className="repeat-list">{d.education.map((item,index)=><EducationEditor key={item.id} value={item} error={errors[`education.${index}`]} onChange={value=>list('education',replace(d.education,index,value))} onRemove={()=>list('education',remove(d.education,index))}/>)}</div><button type="button" onClick={()=>list('education',[...d.education,{id:uid(),institution:'',degree:''}])}>+ Agregar educación</button></EditSection>
    <EditSection title="Certificaciones y cursos"><div className="repeat-list">{d.certifications.map((item,index)=><CertificationEditor key={item.id} value={item} error={errors[`certifications.${index}`]} onChange={value=>list('certifications',replace(d.certifications,index,value))} onRemove={()=>list('certifications',remove(d.certifications,index))}/>)}</div><button type="button" onClick={()=>list('certifications',[...d.certifications,{id:uid(),name:''}])}>+ Agregar certificación</button></EditSection>
    <EditSection title="Idiomas"><div className="repeat-list">{d.languages.map((item,index)=><LanguageEditor key={item.id} value={item} error={errors[`languages.${index}`]} onChange={value=>list('languages',replace(d.languages,index,value))} onRemove={()=>list('languages',remove(d.languages,index))}/>)}</div><button type="button" onClick={()=>list('languages',[...d.languages,{id:uid(),code:'',name:'',proficiency:'PROFESSIONAL'}])}>+ Agregar idioma</button></EditSection>
    <EditSection title="Habilidades y evidencias" description="Las habilidades del catálogo participan en el matching; también puedes registrar habilidades personalizadas."><SkillPicker skills={d.skills} trajectory={d.trajectory} onChange={skills=>list('skills',skills)}/>{errors.skills&&<p className="field-error">{errors.skills}</p>}</EditSection>
    <div className="editor-footer"><button type="button" onClick={onCancel}>Cancelar</button><button className="primary" disabled={saving}>{saving?'Guardando…':'Guardar cambios'}</button></div>
  </form>;
}

function RolePicker({roles,onChange}:{roles:TargetRole[];onChange:(roles:TargetRole[])=>void}){
  const [query,setQuery]=useState('');const [results,setResults]=useState<CatalogEntry[]>([]);const [busy,setBusy]=useState(false);
  useEffect(()=>{if(query.trim().length<2){setResults([]);return;}const timer=setTimeout(()=>{setBusy(true);api<CatalogEntry[]>(`/catalog/roles?q=${encodeURIComponent(query.trim())}&limit=10`).then(items=>setResults(items.filter(item=>!roles.some(role=>role.roleFamilyId===item.id)))).finally(()=>setBusy(false));},250);return()=>clearTimeout(timer);},[query,roles]);
  function add(item:CatalogEntry){if(roles.length>=10)return;onChange([...roles,{id:uid(),roleFamilyId:item.id,name:item.name,priority:roles.length+1}]);setQuery('');setResults([]);}
  function mutate(index:number,direction:-1|1){const target=index+direction;if(target<0||target>=roles.length)return;const next=[...roles];[next[index],next[target]]=[next[target],next[index]];onChange(next.map((role,i)=>({...role,priority:i+1})));}
  return <div className="picker"><div className="sortable-list">{roles.map((role,index)=><div className="sortable-item" key={role.id}><span className="rank">{index+1}</span><strong>{role.name||role.roleFamilyId}</strong><div><button type="button" aria-label="Subir prioridad" disabled={index===0} onClick={()=>mutate(index,-1)}>↑</button><button type="button" aria-label="Bajar prioridad" disabled={index===roles.length-1} onClick={()=>mutate(index,1)}>↓</button><button type="button" aria-label={`Eliminar ${role.name}`} onClick={()=>onChange(roles.filter((_,i)=>i!==index).map((r,i)=>({...r,priority:i+1})))}>×</button></div></div>)}</div>
    <div className="autocomplete"><input value={query} disabled={roles.length>=10} onChange={e=>setQuery(e.target.value)} placeholder={roles.length>=10?'Alcanzaste el máximo de 10 roles':'Escribe al menos 2 caracteres'} aria-label="Buscar rol objetivo" autoComplete="off"/>{busy&&<small>Buscando…</small>}{results.length>0&&<div className="suggestions">{results.map(item=><button type="button" key={item.id} onClick={()=>add(item)}>{item.name}</button>)}</div>}</div>
    <small>{roles.length}/10 roles seleccionados. Sólo puedes elegir opciones del catálogo.</small>
  </div>;
}

function SkillPicker({skills,trajectory,onChange}:{skills:Skill[];trajectory:Trajectory[];onChange:(skills:Skill[])=>void}){
  const [query,setQuery]=useState('');const [results,setResults]=useState<CatalogEntry[]>([]);
  useEffect(()=>{if(query.trim().length<2){setResults([]);return;}const timer=setTimeout(()=>api<CatalogEntry[]>(`/catalog/skills?q=${encodeURIComponent(query.trim())}&limit=10`).then(items=>setResults(items.filter(item=>!skills.some(skill=>skill.catalogSkillId===item.id)))),250);return()=>clearTimeout(timer);},[query,skills]);
  function addCatalog(item:CatalogEntry){onChange([...skills,{id:uid(),catalogSkillId:item.id,name:item.name,matchEligible:true,proficiency:'INTERMEDIATE',evidenceTrajectoryIds:[]}]);setQuery('');setResults([]);}
  function addCustom(){const name=query.trim();if(!name||skills.some(s=>(s.customName||s.name||'').toLocaleLowerCase()===name.toLocaleLowerCase()))return;onChange([...skills,{id:uid(),customName:name,name,matchEligible:false,proficiency:'INTERMEDIATE',evidenceTrajectoryIds:[]}]);setQuery('');setResults([]);}
  return <div className="picker"><div className="repeat-list">{skills.map((skill,index)=><article className="repeat-card" key={skill.id}><div className="repeat-head"><div><strong>{skill.name||skill.customName}</strong><small>{skill.matchEligible?'Catálogo · participa en matching':'Personalizada · no participa en matching'}</small></div><button type="button" onClick={()=>onChange(remove(skills,index))}>Eliminar</button></div><div className="form-grid compact"><Field label="Nivel"><select value={skill.proficiency||''} onChange={e=>onChange(replace(skills,index,{...skill,proficiency:e.target.value}))}><option value="">Sin especificar</option>{['BEGINNER','INTERMEDIATE','ADVANCED','EXPERT'].map(v=><option key={v} value={v}>{label(v)}</option>)}</select></Field><fieldset className="evidence"><legend>Evidencias de trayectoria</legend>{trajectory.length===0?<small>Agrega trayectoria para vincular evidencia.</small>:trajectory.map(item=><label key={item.id}><input type="checkbox" checked={skill.evidenceTrajectoryIds.includes(item.id)} onChange={e=>{const ids=e.target.checked?[...skill.evidenceTrajectoryIds,item.id]:skill.evidenceTrajectoryIds.filter(id=>id!==item.id);onChange(replace(skills,index,{...skill,evidenceTrajectoryIds:ids}))}}/>{item.title||'Trayectoria sin título'}</label>)}</fieldset></div></article>)}</div><div className="autocomplete skill-search"><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Buscar habilidad o escribir una personalizada" aria-label="Buscar habilidad" autoComplete="off"/>{results.length>0&&<div className="suggestions">{results.map(item=><button type="button" key={item.id} onClick={()=>addCatalog(item)}>{item.name}<small>Catálogo</small></button>)}</div>} {query.trim()&&<button type="button" onClick={addCustom}>Agregar “{query.trim()}” como personalizada</button>}</div></div>;
}

function Preferences({value,onChange}:{value:Preference;onChange:(value:Preference)=>void}){return <div className="form-grid"><Field label="Modalidad"><select value={value.remoteMode||''} onChange={e=>onChange({...value,remoteMode:e.target.value})}><option value="">Sin especificar</option>{['REMOTE','HYBRID','ONSITE','ANY'].map(v=><option key={v} value={v}>{label(v)}</option>)}</select></Field><Field label="Tipo de empleo"><select value={value.employmentType||''} onChange={e=>onChange({...value,employmentType:e.target.value})}><option value="">Sin especificar</option>{['FULL_TIME','PART_TIME','CONTRACT','INTERNSHIP','ANY'].map(v=><option key={v} value={v}>{label(v)}</option>)}</select></Field><Field label="Salario mínimo mensual"><input type="number" min="0" step="1000" value={value.minimumMonthlySalary??''} onChange={e=>onChange({...value,minimumMonthlySalary:optionalNumber(e.target.value)})}/></Field><Field label="Moneda"><input value={value.currency||'MXN'} maxLength={3} onChange={e=>onChange({...value,currency:e.target.value.toUpperCase()})}/></Field><label className="check-field"><input type="checkbox" checked={value.willingToRelocate} onChange={e=>onChange({...value,willingToRelocate:e.target.checked})}/>Tengo disponibilidad para reubicarme</label></div>}

function TrajectoryEditor({value,error,onChange,onRemove}:{value:Trajectory;error?:string;onChange:(v:Trajectory)=>void;onRemove:()=>void}){return <article className="repeat-card"><RepeatHead title={value.title||'Nueva trayectoria'} onRemove={onRemove}/>{error&&<p className="field-error">{error}</p>}<div className="form-grid"><Field label="Tipo"><select value={value.type} onChange={e=>onChange({...value,type:e.target.value})}>{trajectoryTypes.map(v=><option key={v} value={v}>{trajectoryLabels[v]}</option>)}</select></Field><Field label="Título"><input value={value.title} maxLength={180} onChange={e=>onChange({...value,title:e.target.value})}/></Field><Field label="Organización"><input value={value.organization||''} maxLength={180} onChange={e=>onChange({...value,organization:e.target.value})}/></Field><Field label="Año inicial"><input type="number" min="1950" max="2200" value={value.startYear} onChange={e=>onChange({...value,startYear:Number(e.target.value)})}/></Field><Field label="Mes inicial"><input type="number" min="1" max="12" value={value.startMonth} onChange={e=>onChange({...value,startMonth:Number(e.target.value)})}/></Field>{!value.current&&<><Field label="Año final"><input type="number" min="1950" max="2200" value={value.endYear??''} onChange={e=>onChange({...value,endYear:optionalNumber(e.target.value)})}/></Field><Field label="Mes final"><input type="number" min="1" max="12" value={value.endMonth??''} onChange={e=>onChange({...value,endMonth:optionalNumber(e.target.value)})}/></Field></>}<label className="check-field"><input type="checkbox" checked={value.current} onChange={e=>onChange({...value,current:e.target.checked,endYear:e.target.checked?null:value.endYear,endMonth:e.target.checked?null:value.endMonth})}/>Actualmente</label></div><Field label="Descripción"><textarea rows={4} maxLength={3000} value={value.description||''} onChange={e=>onChange({...value,description:e.target.value})}/></Field></article>}

function EducationEditor({value,error,onChange,onRemove}:{value:Education;error?:string;onChange:(v:Education)=>void;onRemove:()=>void}){return <article className="repeat-card"><RepeatHead title={value.degree||'Nueva educación'} onRemove={onRemove}/>{error&&<p className="field-error">{error}</p>}<div className="form-grid"><Field label="Institución"><input value={value.institution} onChange={e=>onChange({...value,institution:e.target.value})}/></Field><Field label="Grado"><input value={value.degree} onChange={e=>onChange({...value,degree:e.target.value})}/></Field><Field label="Área de estudio"><input value={value.fieldOfStudy||''} onChange={e=>onChange({...value,fieldOfStudy:e.target.value})}/></Field><Field label="Año inicial"><input type="number" value={value.startYear??''} onChange={e=>onChange({...value,startYear:optionalNumber(e.target.value)})}/></Field><Field label="Año final"><input type="number" value={value.endYear??''} onChange={e=>onChange({...value,endYear:optionalNumber(e.target.value)})}/></Field></div></article>}

function CertificationEditor({value,error,onChange,onRemove}:{value:Certification;error?:string;onChange:(v:Certification)=>void;onRemove:()=>void}){return <article className="repeat-card"><RepeatHead title={value.name||'Nueva certificación'} onRemove={onRemove}/>{error&&<p className="field-error">{error}</p>}<div className="form-grid"><Field label="Nombre"><input value={value.name} onChange={e=>onChange({...value,name:e.target.value})}/></Field><Field label="Emisor"><input value={value.issuer||''} onChange={e=>onChange({...value,issuer:e.target.value})}/></Field><Field label="Año"><input type="number" value={value.issuedYear??''} onChange={e=>onChange({...value,issuedYear:optionalNumber(e.target.value)})}/></Field><Field label="ID de credencial"><input value={value.credentialId||''} onChange={e=>onChange({...value,credentialId:e.target.value})}/></Field><Field label="URL de credencial"><input type="url" value={value.credentialUrl||''} onChange={e=>onChange({...value,credentialUrl:e.target.value})}/></Field></div></article>}

function LanguageEditor({value,error,onChange,onRemove}:{value:Language;error?:string;onChange:(v:Language)=>void;onRemove:()=>void}){return <article className="repeat-card"><RepeatHead title={value.name||'Nuevo idioma'} onRemove={onRemove}/>{error&&<p className="field-error">{error}</p>}<div className="form-grid"><Field label="Idioma"><input value={value.name} onChange={e=>onChange({...value,name:e.target.value})}/></Field><Field label="Código (ej. es, en)"><input value={value.code} maxLength={10} onChange={e=>onChange({...value,code:e.target.value.toLowerCase()})}/></Field><Field label="Nivel"><select value={value.proficiency} onChange={e=>onChange({...value,proficiency:e.target.value})}>{['BASIC','CONVERSATIONAL','PROFESSIONAL','FLUENT','NATIVE'].map(v=><option key={v} value={v}>{label(v)}</option>)}</select></Field></div></article>}

function TagEditor({values,placeholder,limit,onChange}:{values:string[];placeholder:string;limit:number;onChange:(values:string[])=>void}){const [value,setValue]=useState('');function add(){const clean=value.trim();if(clean&&values.length<limit&&!values.some(v=>v.toLocaleLowerCase()===clean.toLocaleLowerCase()))onChange([...values,clean]);setValue('');}return <div><div className="tag-input"><input value={value} placeholder={placeholder} onChange={e=>setValue(e.target.value)} onKeyDown={e=>{if(e.key==='Enter'){e.preventDefault();add();}}}/><button type="button" onClick={add}>Agregar</button></div><div className="editable-chips">{values.map((item,index)=><button type="button" key={item} onClick={()=>onChange(remove(values,index))}>{item} ×</button>)}</div></div>}

function EditSection({title,description,children}:{title:string;description?:string;children:ReactNode}){return <section className="edit-section"><div><h2>{title}</h2>{description&&<p>{description}</p>}</div>{children}</section>}
function Field({label:caption,hint,error,children}:{label:string;hint?:string;error?:string;children:ReactNode}){return <label>{caption}{hint&&<small>{hint}</small>}{children}{error&&<span className="field-error">{error}</span>}</label>}
function RepeatHead({title,onRemove}:{title:string;onRemove:()=>void}){return <div className="repeat-head"><strong>{title}</strong><button type="button" onClick={onRemove}>Eliminar</button></div>}
function Loading({label:caption}:{label:string}){return <div className="loading"><span></span><p>{caption}…</p></div>}
function replace<T>(items:T[],index:number,value:T){return items.map((item,i)=>i===index?value:item)}
function remove<T>(items:T[],index:number){return items.filter((_,i)=>i!==index)}
function newTrajectory():Trajectory{const now=new Date();return{id:uid(),type:'EMPLOYMENT',title:'',startYear:now.getFullYear(),startMonth:now.getMonth()+1,current:true}}
function period(item:Trajectory){const start=`${String(item.startMonth).padStart(2,'0')}/${item.startYear}`;return `${start} – ${item.current?'Actual':`${String(item.endMonth||'').padStart(2,'0')}/${item.endYear||''}`}`}
function yearRange(start?:number|null,end?:number|null){return start||end?`${start||'?'} – ${end||'Actual'}`:'Fechas no especificadas'}
function money(value:number,currency='MXN'){return new Intl.NumberFormat('es-MX',{style:'currency',currency:currency||'MXN',maximumFractionDigits:0}).format(value)}
export function validateProfile(data:ProfileData){const e:Record<string,string>={};if(data.targetRoles.length>10)e.targetRoles='Puedes seleccionar como máximo 10 roles.';data.trajectory.forEach((v,i)=>{if(!v.title.trim()||!v.startYear||!v.startMonth||(!v.current&&(!v.endYear||!v.endMonth)))e[`trajectory.${i}`]='Completa título y fechas válidas.'});data.education.forEach((v,i)=>{if(!v.institution.trim()||!v.degree.trim())e[`education.${i}`]='Institución y grado son obligatorios.'});data.certifications.forEach((v,i)=>{if(!v.name.trim())e[`certifications.${i}`]='El nombre es obligatorio.'});const codes=new Set<string>();data.languages.forEach((v,i)=>{const code=v.code.trim().toLowerCase();if(!v.name.trim()||!code||codes.has(code))e[`languages.${i}`]='El idioma requiere nombre y código único.';codes.add(code)});if(data.skills.some(v=>!v.catalogSkillId&&!v.customName?.trim()))e.skills='Hay una habilidad sin nombre.';if(Object.keys(e).length)e.form='Revisa los campos marcados antes de guardar.';return e;}
