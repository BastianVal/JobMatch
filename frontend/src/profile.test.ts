import { describe, expect, it } from 'vitest';
import { ProfileData, validateProfile } from './profile';

const emptyProfile=():ProfileData=>({targetRoles:[],excludedEmployers:[],trajectory:[],education:[],certifications:[],languages:[],skills:[]});

describe('profile editor validation',()=>{
  it('accepts an empty optional profile',()=>{
    expect(validateProfile(emptyProfile())).toEqual({});
  });

  it('enforces the target role limit',()=>{
    const profile=emptyProfile();
    profile.targetRoles=Array.from({length:11},(_,index)=>({id:`target-${index}`,roleFamilyId:`role-${index}`,priority:index+1}));
    expect(validateProfile(profile)).toMatchObject({targetRoles:'Puedes seleccionar como máximo 10 roles.'});
  });

  it('marks incomplete repeatable sections next to their item',()=>{
    const profile=emptyProfile();
    profile.education=[{id:'education',institution:'',degree:''}];
    profile.languages=[{id:'one',code:'es',name:'Español',proficiency:'NATIVE'},{id:'two',code:'ES',name:'Español',proficiency:'NATIVE'}];
    expect(validateProfile(profile)).toMatchObject({'education.0':expect.any(String),'languages.1':expect.any(String),form:expect.any(String)});
  });
});
