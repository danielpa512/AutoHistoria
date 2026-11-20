import { test } from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { createApi } from './server.mjs';
import { mkdtempSync, mkdirSync, unlinkSync, rmdirSync } from 'node:fs';
import { join } from 'node:path';

async function fixture(t,options={}) {
  const server=createApi({authLimit:100,...options});server.listen(0,'127.0.0.1');await once(server,'listening');
  t.after(()=>new Promise(resolve=>server.close(resolve)));
  return async (method,path,body,token)=>{
    const r=await fetch(`http://127.0.0.1:${server.address().port}${path}`,{method,
      headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:body===undefined?undefined:JSON.stringify(body)});
    return {status:r.status,...await r.json()};
  };
}
async function account(api,email='ana@example.com') {
  assert.equal((await api('POST','/v1/auth/register',{name:'Ana',email,password:'Password Seguro 123!'})).status,201);
  const login=await api('POST','/v1/auth/login',{email,password:'Password Seguro 123!'});assert.equal(login.status,200);return login.token;
}
const visit = (id='first')=>({id,place:'Taller',address:'Calle 10',notes:'Revisión',kilometers:1000,latitude:4.6,longitude:-74.1,accuracy:5,altitude:2600,capturedAt:Date.now(),photos:[],checklist:{tires:'GOOD',brakes:'REVIEW',lights:'GOOD',body:'GOOD'}});
test('demo local acepta código arbitrario una vez y revoca sesiones',async t=>{
  const api=await fixture(t,{demoRecovery:true}),token=await account(api);
  assert.equal((await api('POST','/v1/auth/forgot-password',{email:'ana@example.com'})).status,200);
  const body={token:'cualquier-cosa',password:'Nueva clave demo 123!'};
  assert.equal((await api('POST','/v1/auth/reset-password',body)).status,200);
  assert.equal((await api('POST','/v1/auth/reset-password',body)).status,422);
  assert.equal((await api('GET','/v1/me',undefined,token)).status,401);
  assert.equal((await api('POST','/v1/auth/login',{email:'ana@example.com',password:body.password})).status,200);
});

test('registro, duplicados normalizados, login, perfil y revocación',async t=>{
  const api=await fixture(t),token=await account(api);
  assert.equal((await api('POST','/v1/auth/register',{name:'Otra',email:' ANA@EXAMPLE.COM ',password:'Password Seguro 123!'})).status,409);
  assert.equal((await api('POST','/v1/auth/login',{email:'ana@example.com',password:'incorrecta'})).status,401);
  const me=await api('GET','/v1/me',undefined,token);assert.equal(me.user.email,'ana@example.com');assert.equal(me.user.password_hash,undefined);
  assert.equal((await api('PATCH','/v1/me',{email:'otra@example.com'},token)).status,401);
  const profile=await api('PATCH','/v1/me',{name:'Ana María',lastName:'Parra',city:'Bogotá'},token);assert.equal(profile.user.lastName,'Parra');
  assert.equal((await api('POST','/v1/auth/logout',{},token)).status,200);
  assert.equal((await api('GET','/v1/me',undefined,token)).status,401);
});
test('cada cuenta sólo puede consultar y modificar sus vehículos',async t=>{
  const api=await fixture(t),a=await account(api),b=await account(api,'b@example.com');
  const v=await api('POST','/v1/vehicles',{plate:'abc-123',vin:''},a);assert.equal(v.status,201);assert.equal(v.vehicle.plate,'ABC123');
  assert.equal((await api('POST','/v1/vehicles',{plate:'ABC123'},a)).status,409);
  assert.equal((await api('GET','/v1/vehicles',undefined,b)).total,0);
  assert.equal((await api('GET',`/v1/vehicles/${v.vehicle.id}`,undefined,b)).status,404);
  assert.equal((await api('POST',`/v1/vehicles/${v.vehicle.id}/inspections`,visit(),b)).status,404);
  assert.equal((await api('POST','/v1/vehicles',{plate:'ABC123'},b)).status,201);
});
test('inspecciones idempotentes, GPS, checklist y kilometraje validados por servidor',async t=>{
  const api=await fixture(t),token=await account(api),v=await api('POST','/v1/vehicles',{plate:'ABC123'},token);
  const path=`/v1/vehicles/${v.vehicle.id}/inspections`;
  assert.equal((await api('POST',path,{...visit(),latitude:91},token)).status,422);
  assert.equal((await api('POST',path,{...visit(),checklist:{}},token)).status,422);
  assert.equal((await api('POST',path,visit(),token)).status,201);
  assert.equal((await api('POST',path,visit(),token)).status,200);
  assert.equal((await api('POST',path,{...visit('second'),kilometers:900},token)).status,422);
  const garage=await api('GET','/v1/vehicles?q=abc',undefined,token);assert.equal(garage.items[0].inspections.length,1);assert.equal(garage.items[0].inspections[0].longitude,-74.1);
});
test('cambio de contraseña revoca todas las sesiones y permite nuevo login',async t=>{
  const api=await fixture(t),token=await account(api);
  const second=await api('POST','/v1/auth/login',{email:'ana@example.com',password:'Password Seguro 123!'});
  assert.equal((await api('POST','/v1/me/password',{currentPassword:'Password Seguro 123!',newPassword:'Otra clave segura 456!'},token)).status,200);
  assert.equal((await api('GET','/v1/me',undefined,second.token)).status,401);
  assert.equal((await api('POST','/v1/auth/login',{email:'ana@example.com',password:'Otra clave segura 456!'})).status,200);
});
test('se rechazan avatares activos, JSON malformado, sin sesión y paginación inválida',async t=>{
  const api=await fixture(t),token=await account(api);
  assert.equal((await api('PATCH','/v1/me',{avatar:'data:image/svg+xml;base64,PHN2Zz4='},token)).status,422);
  assert.equal((await api('GET','/v1/vehicles')).status,401);
  assert.equal((await api('GET','/v1/vehicles?limit=999',undefined,token)).status,422);
  assert.equal((await api('POST','/v1/vehicles',{plate:'X',report:{json:'oops',time:1}},token)).status,422);
});
test('borrado de cuenta elimina acceso a los datos y sesiones',async t=>{
  const api=await fixture(t),token=await account(api);
  assert.equal((await api('DELETE','/v1/me',{currentPassword:'Password Seguro 123!'},token)).status,200);
  assert.equal((await api('GET','/v1/me',undefined,token)).status,401);
  assert.equal((await api('POST','/v1/auth/login',{email:'ana@example.com',password:'Password Seguro 123!'})).status,401);
});
test('limitación de intentos de autenticación',async t=>{
  const api=await fixture(t,{authLimit:1});
  assert.equal((await api('POST','/v1/auth/login',{email:'x@example.com',password:'incorrecta'})).status,401);
  assert.equal((await api('POST','/v1/auth/login',{email:'x@example.com',password:'incorrecta'})).status,429);
});
test('recuperación: código de un uso y revocación de sesiones',async t=>{
  let sent;
  const api=await fixture(t,{sendReset:async(email,token)=>{sent={email,token};}}),token=await account(api);
  const requested=await api('POST','/v1/auth/forgot-password',{email:'ana@example.com'});
  assert.equal(requested.status,200);assert.equal(sent.email,'ana@example.com');
  const missing=await api('POST','/v1/auth/forgot-password',{email:'unknown@example.com'});assert.equal(requested.message,missing.message);
  const body={token:sent.token,password:'Clave recuperada 2026!'};
  assert.equal((await api('POST','/v1/auth/reset-password',body)).status,200);
  assert.equal((await api('POST','/v1/auth/reset-password',body)).status,422);
  assert.equal((await api('GET','/v1/me',undefined,token)).status,401);
  assert.equal((await api('POST','/v1/auth/login',{email:'ana@example.com',password:body.password})).status,200);
});
test('cambiar correo invalida códigos enviados a la dirección anterior',async t=>{
  let code;
  const api=await fixture(t,{sendReset:async(email,token)=>{code=token;}}),token=await account(api);
  await api('POST','/v1/auth/forgot-password',{email:'ana@example.com'});
  assert.equal((await api('PATCH','/v1/me',{email:'nueva@example.com',currentPassword:'Password Seguro 123!'},token)).status,200);
  assert.equal((await api('POST','/v1/auth/reset-password',{token:code,password:'Otra clave segura 123!'})).status,422);
});
test('persistencia tras reinicio del servidor, fotos y geocodificación sin clave',async t=>{
  const parent=join(process.cwd(),'data');mkdirSync(parent,{recursive:true});
  const directory=mkdtempSync(join(parent,'test-')),database=join(directory,'test.sqlite');
  let server;
  const start=async()=>{server=createApi({database,authLimit:100});server.listen(0,'127.0.0.1');await once(server,'listening');};
  const stop=()=>new Promise(resolve=>server.close(resolve));
  const api=async(method,path,body,token)=>{
    const r=await fetch(`http://127.0.0.1:${server.address().port}${path}`,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:body===undefined?undefined:JSON.stringify(body)});
    return {status:r.status,...await r.json()};
  };
  await start();
  try {
    const token=await account(api);
    const image='data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aHuoAAAAASUVORK5CYII=';
    assert.equal((await api('PATCH','/v1/me',{avatar:image},token)).status,200);
    const vehicle=await api('POST','/v1/vehicles',{plate:'TEST42'},token);
    assert.equal((await api('POST',`/v1/vehicles/${vehicle.vehicle.id}/inspections`,{...visit(),photos:[image]},token)).status,201);
    await stop();await start();
    const garage=await api('GET','/v1/vehicles',undefined,token);
    assert.equal(garage.items[0].inspections[0].photos[0],image);
    assert.equal((await api('GET','/v1/me',undefined,token)).user.avatar,image);
    assert.equal((await api('POST','/v1/geocode',{lat:4.6,lon:-74.1},token)).status,503);
  } finally {
    await stop();
    for(const suffix of ['','-wal','-shm']) { try { unlinkSync(database+suffix); } catch(e) { if(e.code!=='ENOENT') throw e; } }
    rmdirSync(directory);
  }
});
