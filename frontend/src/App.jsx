import {createContext,useContext,useState,useEffect} from 'react'
const Ctx=createContext()
const useAuth=()=>useContext(Ctx)
const call=async(p,o={},t)=>{
  const r=await fetch('/api'+p,{method:o.method||'GET',headers:{'Content-Type':'application/json',...(t&&{Authorization:'Bearer '+t})},body:o.body&&JSON.stringify(o.body)})
  const d=await r.json().catch(()=>({}))
  if(!r.ok)throw new Error(d.message||'Request failed ('+r.status+')')
  return d}
// custom hook (Exp 4): form state + validation
function useForm(init,validate){
  const [v,setV]=useState(init),[e,setE]=useState({})
  const set=k=>x=>setV({...v,[k]:x.target.value})
  const check=()=>{const er=validate(v);setE(er);return !Object.keys(er).length}
  return {v,e,set,check,setV}}
const today=()=>new Date().toISOString().slice(0,10)

function Login(){
  const {login}=useAuth(),[reg,setReg]=useState(false),[msg,setMsg]=useState('')
  const f=useForm({name:'',email:'',password:''},v=>{const e={}
    if(reg&&v.name.trim().length<3)e.name='Name needs 3+ letters'
    if(!/^\S+@\S+\.\S+$/.test(v.email))e.email='Enter a valid email'
    if(v.password.length<6)e.password='Password needs 6+ characters';return e})
  const go=async()=>{if(!f.check())return
    try{const d=await call(reg?'/auth/register':'/auth/login',{method:'POST',body:f.v});login(d)}catch(x){setMsg(x.message)}}
  return <div className="login"><div className="card"><h1>🦷 Smile-Pro</h1><p>Dental Appointment System</p>
    {reg&&<><input placeholder="Full name" value={f.v.name} onChange={f.set('name')}/><small>{f.e.name}</small></>}
    <input placeholder="Email" value={f.v.email} onChange={f.set('email')}/><small>{f.e.email}</small>
    <input type="password" placeholder="Password" value={f.v.password} onChange={f.set('password')}/><small>{f.e.password}</small>
    {msg&&<div className="err">{msg}</div>}
    <button onClick={go}>{reg?'Create account':'Login'}</button>
    <a onClick={()=>{setReg(!reg);setMsg('')}}>{reg?'Have an account? Login':'New patient? Register'}</a>
    <div className="demo"><a onClick={()=>f.setV({...f.v,email:'admin@smilepro.com',password:'admin123'})}>Admin demo</a> · <a onClick={()=>f.setV({...f.v,email:'rahul@patient.com',password:'patient123'})}>Patient demo</a></div>
  </div></div>}

function Book({go}){
  const {token}=useAuth(),[ds,setDs]=useState([]),[b,setB]=useState({dentistId:'',date:today(),time:'',reason:''}),[slots,setSlots]=useState([]),[msg,setMsg]=useState(''),[ok,setOk]=useState(null)
  useEffect(()=>{call('/dentists').then(setDs)},[])
  useEffect(()=>{setB(x=>({...x,time:''}));if(b.dentistId&&b.date)call(`/slots?dentistId=${b.dentistId}&date=${b.date}`,{},token).then(setSlots).catch(()=>setSlots([]))},[b.dentistId,b.date])
  const submit=async()=>{setMsg('');if(!b.dentistId||!b.time||!b.reason.trim())return setMsg('Choose a dentist, a time slot and enter the reason')
    try{setOk(await call('/appointments',{method:'POST',body:{...b,dentistId:+b.dentistId}},token))}catch(x){setMsg(x.message)}}
  if(ok)return <div className="card ok"><h2>✅ Appointment requested</h2><p>ID #{ok.id} · {ok.dentistName} · {ok.date} at {ok.time} · {ok.status}</p><button onClick={()=>go('my')}>View my appointments</button></div>
  return <div className="card"><h2>Book an appointment</h2>
    <select value={b.dentistId} onChange={e=>setB({...b,dentistId:e.target.value})}><option value="">Select dentist</option>{ds.map(d=><option key={d.id} value={d.id}>{d.name} — {d.specialization}</option>)}</select>
    <input type="date" min={today()} value={b.date} onChange={e=>setB({...b,date:e.target.value})}/>
    <div className="slots">{b.dentistId?slots.length?slots.map(s=><button key={s} className={b.time===s?'on':''} onClick={()=>setB({...b,time:s})}>{s}</button>):<i>No free slots</i>:<i>Pick a dentist to see free slots</i>}</div>
    <input placeholder="Reason (e.g. Tooth pain, Cleaning)" value={b.reason} onChange={e=>setB({...b,reason:e.target.value})}/>
    {msg&&<div className="err">{msg}</div>}<button onClick={submit}>Confirm booking</button></div>}

function My(){
  const {token}=useAuth(),[l,setL]=useState([]),load=()=>call('/appointments/my',{},token).then(setL)
  useEffect(()=>{load()},[])
  return <div className="card"><h2>My appointments</h2><Table rows={l} actions={a=>a.status!=='CANCELLED'&&a.status!=='COMPLETED'&&<button className="sm red" onClick={()=>confirm('Cancel?')&&call(`/appointments/${a.id}/cancel`,{method:'PUT'},token).then(load)}>Cancel</button>}/></div>}

const Table=({rows,actions,admin})=><div className="scroll"><table><thead><tr><th>ID</th>{admin&&<th>Patient</th>}<th>Dentist</th><th>Date</th><th>Time</th><th>Reason</th><th>Status</th><th></th></tr></thead>
  <tbody>{rows.map(a=><tr key={a.id}><td>{a.id}</td>{admin&&<td>{a.patientName}</td>}<td>{a.dentistName}</td><td>{a.date}</td><td>{a.time}</td><td>{a.reason}</td><td><span className={'b '+a.status}>{a.status}</span></td><td>{actions(a)}</td></tr>)}
  {!rows.length&&<tr><td colSpan="8">Nothing here yet</td></tr>}</tbody></table></div>

function Home(){
  const {user,token}=useAuth(),[s,setS]=useState(null),[m,setM]=useState([])
  useEffect(()=>{user.role==='ADMIN'?call('/admin/stats',{},token).then(setS):call('/appointments/my',{},token).then(setM)},[])
  const Bar=({o})=>Object.entries(o||{}).map(([k,v])=><div key={k} className="bar"><span>{k}</span><i style={{width:v*30+10}}>{v}</i></div>)
  if(user.role==='ADMIN')return s?<><div className="stats"><div>📅<b>{s.total}</b>Appointments</div><div>🧑<b>{s.patients}</b>Users</div><div>🦷<b>{s.dentists}</b>Dentists</div></div>
    <div className="card"><h3>By status</h3><Bar o={s.byStatus}/><h3>By dentist</h3><Bar o={s.byDentist}/></div></>:null
  const c=st=>m.filter(a=>a.status===st).length
  return <><h2>Welcome, {user.name}</h2><div className="stats"><div><b>{m.length}</b>Total</div><div><b>{c('PENDING')}</b>Pending</div><div><b>{c('CONFIRMED')}</b>Confirmed</div><div><b>{c('COMPLETED')}</b>Completed</div></div></>}

function AdminAppts(){
  const {token}=useAuth(),[l,setL]=useState([]),load=()=>call('/admin/appointments',{},token).then(setL),set=(id,status)=>call(`/admin/appointments/${id}/status`,{method:'PUT',body:{status}},token).then(load)
  useEffect(()=>{load()},[])
  return <div className="card"><h2>All appointments</h2><Table admin rows={l} actions={a=><select value="" onChange={e=>e.target.value&&set(a.id,e.target.value)}><option value="">Set status</option>{['CONFIRMED','COMPLETED','CANCELLED'].map(s=><option key={s}>{s}</option>)}</select>}/></div>}

function Dentists(){
  const {token}=useAuth(),[l,setL]=useState([]),[n,setN]=useState(''),[sp,setSp]=useState(''),[m,setM]=useState(''),load=()=>call('/dentists').then(setL)
  useEffect(()=>{load()},[])
  const add=async()=>{try{await call('/dentists',{method:'POST',body:{name:n,specialization:sp}},token);setN('');setSp('');setM('');load()}catch(x){setM(x.message)}}
  return <div className="card"><h2>Dentists</h2><input placeholder="Name" value={n} onChange={e=>setN(e.target.value)}/><input placeholder="Specialization" value={sp} onChange={e=>setSp(e.target.value)}/>
    {m&&<div className="err">{m}</div>}<button onClick={add}>＋ Add dentist</button>
    <table><tbody>{l.map(d=><tr key={d.id}><td>{d.name}</td><td>{d.specialization}</td><td><button className="sm red" onClick={()=>confirm('Delete?')&&call('/dentists/'+d.id,{method:'DELETE'},token).then(load)}>Delete</button></td></tr>)}</tbody></table></div>}

export default function App(){
  const [a,setA]=useState(null),[p,setP]=useState('home')
  if(!a)return <Ctx.Provider value={{login:d=>{setA(d);setP('home')}}}><Login/></Ctx.Provider>
  const adm=a.user.role==='ADMIN',menu=adm?[['home','📊 Dashboard'],['appts','📅 Appointments'],['dentists','🦷 Dentists']]:[['home','📊 Dashboard'],['book','➕ Book'],['my','📋 My appointments']]
  const page={home:<Home/>,book:<Book go={setP}/>,my:<My/>,appts:<AdminAppts/>,dentists:<Dentists/>}[p]
  return <Ctx.Provider value={{user:a.user,token:a.token}}><div className="app"><aside><h2>🦷 Smile-Pro</h2><small>{a.user.name} ({a.user.role})</small>
    {menu.map(([k,t])=><a key={k} className={p===k?'on':''} onClick={()=>setP(k)}>{t}</a>)}<a onClick={()=>setA(null)}>⎋ Logout</a></aside><main key={p}>{page}</main></div></Ctx.Provider>}
