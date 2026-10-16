// 仅面向交接中已存在的 8091 隔离实例，创建可清理的原生 UI 点验记录。
const fs=require('node:fs');const assert=require('node:assert/strict');
const mini=JSON.parse(fs.readFileSync('/tmp/courtside-mini-session.json'));
const admin=JSON.parse(fs.readFileSync('/tmp/courtside-venue1-session.json'));
const api=async (url,method='GET',data,staff=false)=>{
 const r=await fetch('http://127.0.0.1:8091/api'+url,{method,headers:{[staff?'X-Admin-Token':'X-Mini-Token']:staff?admin.token:mini.token,'Content-Type':'application/json'},body:data&&JSON.stringify(data)});
 const body=await r.json();assert.equal(body.code,0,url+': '+body.msg);return body.data;
};
(async()=>{
 const day=new Date(Date.now()+8*3600000+86400000).toISOString().slice(0,10);
 const slots=await api(`/public/venues/1/slot-comparison?date=${day}`);
 const open=slots.filter(s=>s.status==='OPEN');assert.ok(open.length>=2);
 const facts={day,bookings:[],roleId:null,employeeId:null};
 fs.writeFileSync('/tmp/courtside-native-fixtures.json',JSON.stringify(facts));
 for(const slot of open.slice(0,2)){
  const b=await api('/mini/bookings/create','POST',{sessionSlotId:slot.id,contactName:'UI 点验',contactPhone:'13800000000',expectedPriceFen:slot.priceFen,idempotencyKey:'courtside-'+Date.now()+'-'+slot.id});facts.bookings.push(b.id);fs.writeFileSync('/tmp/courtside-native-fixtures.json',JSON.stringify(facts));
 }
 await api(`/mini/bookings/${facts.bookings[0]}/mock-pay`,'POST');
 const role=await api('/venue-admin/roles','POST',{name:'场边原生点验角色',status:'ACTIVE',permissions:['DASHBOARD_VIEW','BOOKING_VERIFY','CUSTOMER_VIEW','MEMBER_RECHARGE']},true);facts.roleId=role.id;fs.writeFileSync('/tmp/courtside-native-fixtures.json',JSON.stringify(facts));
 const employee=await api('/venue-admin/employees/create','POST',{name:'场边原生点验员工'},true);
 const employees=await api('/venue-admin/employees/list','GET',null,true);facts.employeeId=employees.find(e=>e.name==='场边原生点验员工').id;
 fs.writeFileSync('/tmp/courtside-native-fixtures.json',JSON.stringify(facts));
 await api(`/venue-admin/employees/${facts.employeeId}/roles`,'PUT',{roleIds:[facts.roleId]},true);
 await api('/mini/staff/bind','POST',{bindingCode:employee.bindingCode});
 const customers=await api('/venue-admin/customers/list?keyword=','GET',null,true);
 facts.customerId=customers.find(c=>c.userId===mini.profile.id)?.customerId;
 facts.pendingSlot=open[2]||open[1];
 fs.writeFileSync('/tmp/courtside-native-fixtures.json',JSON.stringify(facts));
 console.log(JSON.stringify({day,bookings:facts.bookings,employeeId:facts.employeeId,customerId:facts.customerId}));
})().catch(e=>{console.error(e);process.exitCode=1});
