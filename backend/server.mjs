import http from 'node:http';
import { DatabaseSync } from 'node:sqlite';
import { randomBytes, randomUUID, createHash, scrypt, timingSafeEqual } from 'node:crypto';
import { promisify } from 'node:util';
import { mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const derive = promisify(scrypt);
const hash = v => createHash('sha256').update(v).digest('hex');
const fail = (status, message) => { throw Object.assign(new Error(message), { status }); };
const need = (ok, message) => { if (!ok) fail(422, message); };
const text = (v, max = 120) => typeof v === 'string' && v.length <= max;
const emailValue = v => { need(text(v, 254) && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.trim()), 'Correo inválido.'); return v.trim().toLowerCase(); };
const passwordValue = v => { need(text(v, 128) && v.length >= 12, 'La contraseña debe tener entre 12 y 128 caracteres.'); return v; };
const normalize = v => { need(text(v, 30), 'Identificador inválido.'); return v.toUpperCase().replace(/[^A-Z0-9]/g, ''); };
async function passwordHash(password, salt) { return Buffer.from(await derive(password, salt, 64, { N: 32768, r: 8, p: 1, maxmem: 64 * 1024 * 1024 })).toString('hex'); }
async function passwordMatches(user, password) {
  if (!text(password, 128)) return false;
  const actual = await passwordHash(password, user?.salt || 'unregistered-account');
  return timingSafeEqual(Buffer.from(actual, 'hex'), Buffer.from(user?.password_hash || '00'.repeat(64), 'hex')) && !!user;
}
function picture(v, max = 400000) {
  need(typeof v === 'string' && v.length <= Math.ceil(max * 4 / 3) + 100, 'Imagen demasiado grande.');
  const match = /^data:image\/(jpeg|png);base64,([A-Za-z0-9+/]+={0,2})$/.exec(v);
  need(!!match, 'Usa una imagen PNG o JPEG.');
  const bytes = Buffer.from(match[2], 'base64');
  need(bytes.length <= max && (match[1] === 'jpeg' ? bytes.subarray(0,3).equals(Buffer.from([255,216,255])) : bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10]))), 'Imagen inválida.');
  return v;
}
const cleanReport = r => {
  if (r == null) return null;
  need(text(r.json, 100000) && Number.isSafeInteger(r.time), 'Ficha inválida.');
  let raw; try { raw = JSON.parse(r.json); } catch { fail(422, 'Ficha JSON inválida.'); }
  need(raw && typeof raw.vehicle === 'object' && raw.vehicle !== null && !Array.isArray(raw.vehicle), 'Faltan datos del vehículo.');
  // Store only the technical vehicle object, never the upstream token or quotation.
  return { json: JSON.stringify({ vehicle: raw.vehicle }), time: r.time, manual: r.manual === true };
};
export function createApi({ database = ':memory:', googleKey = '', rateLimit = 300, authLimit = 15, sendReset = null, demoRecovery = false } = {}) {
  if (demoRecovery && process.env.NODE_ENV === 'production') throw new Error('Demo recovery is disabled in production');
  const demoRequests = new Map();
  if (database !== ':memory:') mkdirSync(dirname(resolve(database)), { recursive: true });
  const db = new DatabaseSync(database);
  db.exec(`PRAGMA foreign_keys=ON; PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000;
    CREATE TABLE IF NOT EXISTS users(id TEXT PRIMARY KEY,email TEXT UNIQUE NOT NULL,name TEXT NOT NULL,last_name TEXT NOT NULL DEFAULT '',phone TEXT NOT NULL DEFAULT '',city TEXT NOT NULL DEFAULT '',bio TEXT NOT NULL DEFAULT '',avatar TEXT NOT NULL DEFAULT '',salt TEXT NOT NULL,password_hash TEXT NOT NULL,created_at INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS sessions(token_hash TEXT PRIMARY KEY,user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,expires_at INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS password_resets(token_hash TEXT PRIMARY KEY,user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,expires_at INTEGER NOT NULL);
    CREATE INDEX IF NOT EXISTS sessions_user ON sessions(user_id);
    CREATE TABLE IF NOT EXISTS vehicles(id TEXT PRIMARY KEY,user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,plate TEXT NOT NULL,vin TEXT NOT NULL,report TEXT,created_at INTEGER NOT NULL);
    CREATE UNIQUE INDEX IF NOT EXISTS vehicle_plate ON vehicles(user_id,plate) WHERE plate<>'';
    CREATE UNIQUE INDEX IF NOT EXISTS vehicle_vin ON vehicles(user_id,vin) WHERE vin<>'';
    CREATE TABLE IF NOT EXISTS inspections(id TEXT NOT NULL,vehicle_id TEXT NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,kilometers INTEGER NOT NULL,time INTEGER NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(vehicle_id,id));
    CREATE INDEX IF NOT EXISTS inspection_history ON inspections(vehicle_id,time);
    CREATE TABLE IF NOT EXISTS audit(id INTEGER PRIMARY KEY,event TEXT NOT NULL,user_id TEXT,time INTEGER NOT NULL);
    PRAGMA user_version=1;`);
  const one = (sql, ...args) => db.prepare(sql).get(...args);
  const all = (sql, ...args) => db.prepare(sql).all(...args);
  const run = (sql, ...args) => db.prepare(sql).run(...args);
  const publicUser = u => ({ id: u.id, name: u.name, lastName: u.last_name, email: u.email, phone: u.phone, city: u.city, bio: u.bio, avatar: u.avatar, createdAt: u.created_at });
  const audit = (event, uid) => run('INSERT INTO audit(event,user_id,time) VALUES(?,?,?)', event, uid, Date.now());
  const transaction = fn => { db.exec('BEGIN IMMEDIATE'); try { const r = fn(); db.exec('COMMIT'); return r; } catch (e) { db.exec('ROLLBACK'); throw e; } };
  const session = uid => {
    run('DELETE FROM sessions WHERE expires_at<?', Date.now());
    const token = randomBytes(32).toString('base64url'), expiresAt = Date.now() + 7 * 86400000;
    run('INSERT INTO sessions VALUES(?,?,?)', hash(token), uid, expiresAt);
    return { token, expiresAt };
  };
  const owned = (uid, id) => one('SELECT * FROM vehicles WHERE user_id=? AND id=?', uid, id) || fail(404, 'Vehículo no encontrado.');
  const vehicleJson = v => ({ id: v.id, plate: v.plate, vin: v.vin, report: v.report ? JSON.parse(v.report) : null,
    inspections: all('SELECT payload FROM inspections WHERE vehicle_id=? ORDER BY time DESC', v.id).map(i => JSON.parse(i.payload)) });
  const buckets = new Map();
  const limit = (key, max) => {
    const now = Date.now();
    if (buckets.size > 10000) { for (const [k, v] of buckets) if (v.until < now) buckets.delete(k); }
    if (buckets.size > 20000) fail(503, 'Servidor ocupado.');
    const b = buckets.get(key); if (!b || b.until < now) buckets.set(key, { count: 1, until: now + 60000 });
    else if (++b.count > max) fail(429, 'Demasiados intentos. Espera un minuto.');
  };
  const server = http.createServer(async (req, res) => {
    const requestId = randomUUID();
    res.setHeader('X-Request-Id', requestId); res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('Cache-Control', 'no-store'); res.setHeader('Content-Type', 'application/json; charset=utf-8');
    const send = (status, body) => { res.writeHead(status); res.end(JSON.stringify(body)); };
    try {
      const url = new URL(req.url, 'http://localhost'); const path = url.pathname; const method = req.method;
      limit(req.socket.remoteAddress || 'unknown', rateLimit);
      if (path.startsWith('/v1/auth/')) limit('auth:' + req.socket.remoteAddress, authLimit);
      if (method === 'GET' && path === '/health') { one('SELECT 1'); return send(200, { status: 'ok' }); }
      let body = {};
      if (['POST','PATCH','PUT','DELETE'].includes(method)) {
        if (!(req.headers['content-type'] || '').startsWith('application/json')) fail(415, 'Envía application/json.');
        const chunks = []; let size = 0;
        for await (const chunk of req) { size += chunk.length; if (size > 4500000) fail(413, 'Solicitud demasiado grande.'); chunks.push(chunk); }
        try { body = JSON.parse(Buffer.concat(chunks).toString() || '{}'); } catch { fail(400, 'JSON inválido.'); }
        need(body && typeof body === 'object' && !Array.isArray(body), 'Objeto JSON requerido.');
      }
      if (method === 'POST' && path === '/v1/auth/register') {
        const email = emailValue(body.email); const password = passwordValue(body.password);
        need(text(body.name) && body.name.trim().length > 0, 'Escribe tu nombre.');
        const salt = randomBytes(16).toString('hex'), passwordDigest = await passwordHash(password, salt), id = randomUUID();
        try { run('INSERT INTO users(id,email,name,salt,password_hash,created_at) VALUES(?,?,?,?,?,?)', id, email, body.name.trim(), salt, passwordDigest, Date.now()); }
        catch (e) { if (String(e.message).includes('UNIQUE')) fail(409, 'El correo ya está registrado.'); throw e; }
        audit('register', id); return send(201, { user: publicUser(one('SELECT * FROM users WHERE id=?', id)) });
      }
      if (method === 'POST' && path === '/v1/auth/login') {
        const email = emailValue(body.email), user = one('SELECT * FROM users WHERE email=?', email);
        limit('email:' + hash(email), authLimit);
        if (!await passwordMatches(user, body.password)) fail(401, 'Correo o contraseña incorrectos.');
        audit('login', user.id); return send(200, { ...session(user.id), user: publicUser(user) });
      }
      if (method === 'POST' && path === '/v1/auth/forgot-password') {
        const email = emailValue(body.email);
        if (demoRecovery) {
          if (!['127.0.0.1','::1','::ffff:127.0.0.1'].includes(req.socket.remoteAddress)) fail(403,'La simulación sólo permite conexiones locales.');
          const found = one('SELECT id FROM users WHERE email=?',email);
          demoRequests.set(req.socket.remoteAddress,{uid:found?.id,expires:Date.now()+15*60000});
          return send(200,{message:'MODO DEMO: escribe cualquier código para continuar. No se envió ningún correo.'});
        }
        if (!sendReset) fail(503,'La recuperación por correo aún no está configurada.');
        const found = one('SELECT id FROM users WHERE email=?',email);
        if (found) {
          const token = randomBytes(32).toString('base64url');
          transaction(() => {
            run('DELETE FROM password_resets WHERE user_id=? OR expires_at<?',found.id,Date.now());
            run('INSERT INTO password_resets VALUES(?,?,?)',hash(token),found.id,Date.now()+15*60000);
          });
          try { await sendReset(email,token); } catch { console.error(JSON.stringify({requestId,error:'reset_delivery_failed'})); }
        }
        return send(200,{message:'Si el correo está registrado, recibirás un código válido durante 15 minutos.'});
      }
      if (method === 'POST' && path === '/v1/auth/reset-password') {
        if (demoRecovery) {
          const pending=demoRequests.get(req.socket.remoteAddress);
          if(!pending?.uid || pending.expires<Date.now()) fail(422,'Solicita primero la recuperación de una cuenta de prueba existente.');
          need(text(body.token,100) && body.token.trim().length>0,'Escribe cualquier código de demostración.');
          passwordValue(body.password);
          const salt=randomBytes(16).toString('hex'),digest=await passwordHash(body.password,salt);
          need(demoRequests.get(req.socket.remoteAddress) === pending,'Solicitud reemplazada. Vuelve a intentarlo.');
          demoRequests.delete(req.socket.remoteAddress);
          transaction(()=>{
            run('UPDATE users SET salt=?,password_hash=? WHERE id=?',salt,digest,pending.uid);
            run('DELETE FROM sessions WHERE user_id=?',pending.uid);
            run('DELETE FROM password_resets WHERE user_id=?',pending.uid);
            audit('password.demo_reset',pending.uid);
          });
          return send(200,{ok:true,demo:true});
        }
        need(text(body.token,100) && body.token.length === 43,'Código inválido.'); passwordValue(body.password);
        const reset = one('SELECT * FROM password_resets WHERE token_hash=? AND expires_at>?',hash(body.token),Date.now());
        if(!reset) fail(422,'Código inválido o vencido.');
        const salt=randomBytes(16).toString('hex'),digest=await passwordHash(body.password,salt);
        transaction(() => {
          const consumed=run('DELETE FROM password_resets WHERE token_hash=? AND expires_at>?',hash(body.token),Date.now());
          need(consumed.changes === 1,'Código ya usado o vencido.');
          run('UPDATE users SET salt=?,password_hash=? WHERE id=?',salt,digest,reset.user_id);
          run('DELETE FROM sessions WHERE user_id=?',reset.user_id); audit('password.reset',reset.user_id);
        }); return send(200,{ok:true});
      }
      const bearer = /^Bearer ([A-Za-z0-9_-]{43})$/.exec(req.headers.authorization || '');
      const current = bearer && one('SELECT user_id FROM sessions WHERE token_hash=? AND expires_at>?', hash(bearer[1]), Date.now());
      if (!current) fail(401, 'Inicia sesión nuevamente.');
      const uid = current.user_id, user = one('SELECT * FROM users WHERE id=?', uid);
      if (method === 'POST' && path === '/v1/auth/logout') { run('DELETE FROM sessions WHERE token_hash=?', hash(bearer[1])); return send(200, { ok: true }); }
      if (method === 'POST' && path === '/v1/auth/logout-all') { run('DELETE FROM sessions WHERE user_id=?', uid); return send(200, { ok: true }); }
      if (method === 'GET' && path === '/v1/me') return send(200, { user: publicUser(user) });
      if (method === 'PATCH' && path === '/v1/me') {
        const name = body.name ?? user.name, lastName = body.lastName ?? user.last_name, phone = body.phone ?? user.phone, city = body.city ?? user.city, bio = body.bio ?? user.bio;
        need(text(name) && name.trim() && text(lastName) && text(phone,30) && text(city) && text(bio,500), 'Datos de perfil inválidos.');
        const email = body.email === undefined ? user.email : emailValue(body.email);
        if (email !== user.email && !await passwordMatches(user, body.currentPassword)) fail(401, 'Confirma tu contraseña para cambiar el correo.');
        const avatar = body.avatar === undefined ? user.avatar : body.avatar === '' ? '' : picture(body.avatar);
        try { run('UPDATE users SET name=?,last_name=?,phone=?,city=?,bio=?,email=?,avatar=? WHERE id=?', name.trim(), lastName.trim(), phone.trim(), city.trim(), bio.trim(), email, avatar, uid); }
        catch (e) { if (String(e.message).includes('UNIQUE')) fail(409, 'El correo ya está registrado.'); throw e; }
        if (email !== user.email) run('DELETE FROM password_resets WHERE user_id=?',uid);
        audit('profile.updated', uid); return send(200, { user: publicUser(one('SELECT * FROM users WHERE id=?', uid)) });
      }
      if (method === 'POST' && path === '/v1/me/password') {
        passwordValue(body.newPassword);
        if (!await passwordMatches(user, body.currentPassword)) fail(401, 'Contraseña actual incorrecta.');
        const salt = randomBytes(16).toString('hex'), digest = await passwordHash(body.newPassword, salt);
        transaction(() => { run('UPDATE users SET salt=?,password_hash=? WHERE id=?', salt,digest,uid); run('DELETE FROM sessions WHERE user_id=?', uid); run('DELETE FROM password_resets WHERE user_id=?',uid); audit('password.changed',uid); });
        return send(200, { ok: true });
      }
      if (method === 'DELETE' && path === '/v1/me') {
        if (!await passwordMatches(user, body.currentPassword)) fail(401, 'Contraseña actual incorrecta.');
        transaction(() => { run('DELETE FROM audit WHERE user_id=?', uid); run('DELETE FROM users WHERE id=?', uid); }); return send(200,{ok:true});
      }

      if (method === 'GET' && path === '/v1/vehicles/recent') {
        const rows = all(`
          SELECT id, plate, created_at
          FROM vehicles
          ORDER BY created_at DESC, id DESC
          LIMIT 10
        `);

        return send(200, {
          items: rows.map(v => ({
            id: v.id,
            plate: v.plate,
            createdAt: v.created_at
          }))
        });
      }

      if (method === 'GET' && path === '/v1/vehicles') {
        const q = (url.searchParams.get('q') || '').trim().toLowerCase(); need(q.length <= 120, 'Búsqueda demasiado larga.');
        const offset = Number(url.searchParams.get('offset') || 0), count = Number(url.searchParams.get('limit') || 50);
        need(Number.isInteger(offset) && offset >= 0 && Number.isInteger(count) && count > 0 && count <= 100, 'Paginación inválida.');
        const rows = all('SELECT * FROM vehicles WHERE user_id=? ORDER BY created_at DESC', uid).filter(v => `${v.plate} ${v.vin} ${v.report || ''}`.toLowerCase().includes(q));
        return send(200, { items: rows.slice(offset,offset+count).map(vehicleJson), total: rows.length });
      }
      if (method === 'POST' && path === '/v1/vehicles') {
        const plate = normalize(body.plate || ''), vin = normalize(body.vin || '');
        need((plate || vin) && plate.length <= 12 && (!vin || /^[A-HJ-NPR-Z0-9]{17}$/.test(vin)), 'Placa o VIN inválido.');
        const id = randomUUID(), report = cleanReport(body.report);
        try { run('INSERT INTO vehicles VALUES(?,?,?,?,?,?)', id,uid,plate,vin,report ? JSON.stringify(report) : null,Date.now()); }
        catch (e) { if (String(e.message).includes('UNIQUE')) fail(409, 'Ya registraste esa placa o VIN.'); throw e; }
        audit('vehicle.created',uid); return send(201, { vehicle: vehicleJson(owned(uid,id)) });
      }
      const route = /^\/v1\/vehicles\/([a-f0-9-]+)(?:\/(report|inspections))?$/.exec(path);
      if (route) {
        const v = owned(uid,route[1]);
        if (method === 'GET' && !route[2]) return send(200, { vehicle: vehicleJson(v) });
        if (method === 'PUT' && route[2] === 'report') { const r = cleanReport(body); run('UPDATE vehicles SET report=? WHERE id=?', JSON.stringify(r),v.id); return send(200,{ok:true}); }
        if (method === 'POST' && route[2] === 'inspections') {
          need(text(body.id,80) && /^[a-zA-Z0-9-]+$/.test(body.id), 'ID de inspección inválido.');
          const exists = one('SELECT payload FROM inspections WHERE vehicle_id=? AND id=?',v.id,body.id);
          if (exists) return send(200, { inspection: JSON.parse(exists.payload) });
          need(text(body.place,200) && body.place.trim() && text(body.address,500) && text(body.notes,5000), 'Lugar u observaciones inválidos.');
          need(Number.isSafeInteger(body.kilometers) && body.kilometers >= 0 && body.kilometers <= 10000000, 'Kilometraje inválido.');
          for (const [field,min,max] of [['latitude',-90,90],['longitude',-180,180],['accuracy',0,100000]]) need(typeof body[field] === 'number' && Number.isFinite(body[field]) && body[field] >= min && body[field] <= max, 'Coordenadas o precisión inválidas.');
          need(Number.isSafeInteger(body.capturedAt) && body.capturedAt > 0 && body.capturedAt <= Date.now()+300000, 'Hora GPS inválida.');
          need(body.altitude == null || (typeof body.altitude === 'number' && Number.isFinite(body.altitude)), 'Altitud inválida.');
          need(body.checklist && ['tires','brakes','lights','body'].every(k => ['GOOD','REVIEW','REPLACE'].includes(body.checklist[k])), 'Completa la checklist.');
          need(Array.isArray(body.photos) && body.photos.length <= 6, 'Máximo 6 fotos.'); body.photos.forEach(p => picture(p));
          const record = { id:body.id,time:Date.now(),place:body.place.trim(),address:body.address.trim(),notes:body.notes,kilometers:body.kilometers,latitude:body.latitude,longitude:body.longitude,accuracy:body.accuracy,altitude:body.altitude ?? null,capturedAt:body.capturedAt,googleAddress:body.googleAddress === true,checklist:body.checklist,photos:body.photos };
          transaction(() => {
            const previous = one('SELECT MAX(kilometers) AS km FROM inspections WHERE vehicle_id=?',v.id);
            need(previous.km === null || body.kilometers >= previous.km,'El kilometraje no puede disminuir.');
            run('INSERT INTO inspections VALUES(?,?,?,?,?)',body.id,v.id,body.kilometers,record.time,JSON.stringify(record)); audit('inspection.created',uid);
          }); return send(201,{inspection:record});
        }
      }
      if (method === 'POST' && path === '/v1/geocode') {
        need(typeof body.lat === 'number' && Number.isFinite(body.lat) && Math.abs(body.lat) <= 90 && typeof body.lon === 'number' && Number.isFinite(body.lon) && Math.abs(body.lon) <= 180, 'Coordenadas inválidas.');
        limit('geocode:'+uid, 20);
        if (!googleKey) fail(503,'Google Geocoding no está configurado. Escribe la dirección manualmente.');
        const u = new URL('https://maps.googleapis.com/maps/api/geocode/json');
        u.search = new URLSearchParams({latlng:`${body.lat},${body.lon}`,language:'es',key:googleKey});
        let data; try { const response = await fetch(u,{signal:AbortSignal.timeout(12000)}); data = await response.json(); } catch { fail(502,'Google no respondió. Escribe la dirección manualmente.'); }
        if(data.status !== 'OK') fail(502,'No se pudo resolver la dirección. Revisa Geocoding API o escríbela manualmente.');
        return send(200,{address:data.results[0].formatted_address});
      }
      fail(404,'Ruta no encontrada.');
    } catch (e) {
      const status = e.status || 500;
      if (status === 429) res.setHeader('Retry-After','60');
      // Never log bodies, authorization, coordinates, email or upstream URLs.
      if (status === 500) console.error(JSON.stringify({requestId, error:'internal_error'}));
      if (!res.headersSent) send(status,{error:{code:status,message:status === 500 ? 'Error interno.' : e.message,requestId}});
    }
  });
  server.requestTimeout = 30000; server.headersTimeout = 10000;
  server.on('close', () => db.close());
  return server;
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const sendReset = process.env.RESEND_API_KEY && process.env.MAIL_FROM ? async(email,token) => {
    const response=await fetch('https://api.resend.com/emails',{method:'POST',signal:AbortSignal.timeout(10000),headers:{Authorization:`Bearer ${process.env.RESEND_API_KEY}`,'Content-Type':'application/json'},
      body:JSON.stringify({from:process.env.MAIL_FROM,to:[email],subject:'Recupera tu cuenta AutoHistoria',text:`Tu código de recuperación es:\n${token}\n\nCaduca en 15 minutos y sólo puede utilizarse una vez. Si no lo solicitaste, ignora este correo.`})});
    if(!response.ok) throw new Error('Email delivery failed');
  } : null;
  const demoRecovery=process.env.DEMO_PASSWORD_RESET === 'true';
  if(demoRecovery && !['127.0.0.1','localhost','::1'].includes(process.env.HOST || '127.0.0.1')) throw new Error('Demo recovery requires a loopback HOST');
  const server = createApi({database:process.env.DATABASE_PATH || './data/autohistoria.sqlite',googleKey:process.env.GOOGLE_GEOCODING_API_KEY || '',sendReset,demoRecovery});
  server.listen(Number(process.env.PORT || 3000),process.env.HOST || '127.0.0.1',() => console.log('AutoHistoria API ready'));
  for(const signal of ['SIGINT','SIGTERM']) process.on(signal,() => { server.close(() => process.exit(0)); setTimeout(() => process.exit(1),9000).unref(); });
}
