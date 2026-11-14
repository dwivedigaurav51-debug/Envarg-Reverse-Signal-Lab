/* Olympia 2050 research modules. All amounts are hypothetical. Never sends broker orders. */
(function(){
'use strict';
const $=id=>document.getElementById(id);
const KEY='olympia2050_blackbox_v2';
let audit=[];
try{const v=JSON.parse(localStorage.getItem(KEY)||'[]');if(Array.isArray(v))audit=v.slice(-2500)}catch(e){}
let lastKey='',lastPts=null;
const n=id=>Number($(id)?.value);
const money=x=>'$'+(Number.isFinite(x)?x:0).toFixed(2);
const set=(id,v)=>{const e=$(id);if(e)e.textContent=String(v)};
function today(ts){const d=new Date(ts);return [d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-')}
function currentPaper(){
 const a=(Array.isArray(savedTrades)?savedTrades:[]).filter(x=>x&&Number.isFinite(Number(x.ts))&&today(Number(x.ts))===today(Date.now())&&typeof x.win==='boolean'&&Number.isFinite(x.stake)&&x.stake>0&&Number.isFinite(x.pnl));
 return {count:a.length,wins:a.filter(x=>x.win).length,losses:a.filter(x=>!x.win).length,pnl:a.reduce((v,x)=>v+x.pnl,0),assumption:'Recorded virtual stake and entry payout only; legacy unpriced trades excluded'};
}
function model(pts){
 const cap=n('olCapital'),stake=n('olStake'),risk=n('olRiskPct'),goal=n('olTarget');
 const fresh=Array.isArray(pts)&&pts.length>=42&&pts.slice(-42).every((p,i,a)=>i===0||p.ts-a[i-1].ts===300000)&&Date.now()-pts[pts.length-1].ts>=0&&Date.now()-pts[pts.length-1].ts<=660000;
 const health=marketHealth?.level||'UNAVAILABLE',metric=v11Current||{};
 const m=fresh?pts.slice(-21).map((p,i,a)=>i?p.price-a[i-1].price:0).slice(1):[];
 const abs=m.reduce((v,x)=>v+Math.abs(x),0),net=m.reduce((v,x)=>v+x,0),eff=abs?Math.abs(net)/abs:0;
 const paper=currentPaper(),budget=cap*risk/100,remaining=budget+Math.min(0,paper.pnl);
 const valid=cap>0&&stake>0&&risk>0&&risk<=20&&goal>0&&n('olPayout')>0&&n('olPayout')<=100;
 const riskOk=valid&&stake<=cap*.02&&remaining>=stake;
 const slot=oneWaySlot(Date.now()),sample=(Array.isArray(v11DailyHealth)?v11DailyHealth:[]).filter(x=>x.slot===slot);
 const favorable=sample.filter(x=>['GOOD','EXCELLENT'].includes(x.level)).length;
 const score=Number(metric.score)||0;
 const votes={trend:score>=65&&!metric.falseSignal,health:['GOOD','EXCELLENT'].includes(health),time:sample.length>=5&&favorable/sample.length>=.6,efficiency:fresh&&eff>=.45,risk:riskOk};
 const count=Object.values(votes).filter(Boolean).length;
 let decision='WAIT',reason='Insufficient fresh data';
 if(!valid){decision='STOP';reason='Invalid risk settings'}
 else if(paper.pnl>=goal){decision='STOP';reason='Illustrative paper target reached'}
 else if(remaining<stake||stake>cap*.02){decision='STOP';reason='Capital / daily risk limit'}
 else if(!fresh){reason='Missing, stale or discontinuous candles'}
 else if(count<5){decision='SKIP';reason='All five risk and evidence checks required'}
 else{decision='RESEARCH';reason='Research candidate only; no real-money order'}
 return {cap,stake,risk,goal,fresh,health,score,net,eff,slot,sample,favorable,paper,budget,remaining,votes,count,decision,reason,ts:fresh?pts[pts.length-1].ts:null};
}
function render(x){
 set('ol2050Pulse',x.fresh?(x.net>0?'UP ':x.net<0?'DOWN ':'FLAT ')+Math.round(x.eff*100)+'%':'NO DATA');
 set('ol2050Council',x.count+'/5');
 set('ol2050Safety',x.decision==='STOP'?'STOP':'GUARDED');
 set('ol2050Decision',x.decision);
 set('ol2050Evidence',x.reason+' · '+x.health+' · score '+x.score+'/100 · not a win probability');
 set('olTwin',x.fresh?'20-move observed efficiency '+Math.round(x.eff*100)+'%; scenario: direction can reverse or remain flat':'Digital twin unavailable: 42 continuous fresh candles required');
 set('olRuin','Daily risk budget '+money(x.budget)+' · remaining estimated '+money(x.remaining)+' · stake cap '+money(x.cap*.02)+' · ruin risk not zero');
 set('olTimeDNA',x.slot+' · favorable '+x.favorable+'/'+x.sample.length+' recorded time samples; not a forecast');
 set('olParallel','Research: trend / time / health / volatility filters; out-of-sample tournament not validated');
 set('olTrap2050',!x.fresh?'UNKNOWN':x.eff<.35?'CHOP / LOW EFFICIENCY':v11Current.falseSignal?'FALSE-SIGNAL FLAG':'No current rule flag');
 set('olCouncilDetail',Object.entries(x.votes).map(([k,v])=>k+':'+(v?'YES':'NO')).join(' · '));
 const settled=(Array.isArray(savedTrades)?savedTrades:[]).filter(z=>typeof z.win==='boolean'),w=settled.filter(z=>z.win).length;
 set('olProbability',settled.length>=30?'Observed '+(100*w/settled.length).toFixed(1)+'% WIN from '+settled.length+' paper trades; not calibrated prediction':'Insufficient settled paper sample '+settled.length+'/30');
 set('olHeartbeat',x.fresh?'20-move net '+x.net.toFixed(6)+' · efficiency '+Math.round(x.eff*100)+'%':'No verified fresh data');
 set('olVault','Today estimated paper P/L '+money(x.paper.pnl)+' · target '+money(x.goal)+' · '+x.paper.assumption);
 set('olDisaster','15 consecutive base-stake losses: '+money(15*x.stake)+' ('+(x.cap?1500*x.stake/x.cap:0).toFixed(1)+'% capital), before stop');
 set('olCommander',x.decision+' · '+x.reason+' · no broker execution');
 const key=String(x.ts)+'|'+x.decision+'|'+x.health+'|'+x.paper.count;
 if(lastKey!==key){lastKey=key;audit.push({recordedAt:Date.now(),candleTime:x.ts,decision:x.decision,reason:x.reason,health:x.health,score:x.score,efficiency:x.eff,votes:x.votes,virtualPaperCount:x.paper.count});audit=audit.slice(-2500);try{localStorage.setItem(KEY,JSON.stringify(audit))}catch(e){}}
 set('olBlackBox',audit.length+' local decision records · JSON export available; storage is not permanent backup');
}
// Fail-closed gate: checked synchronously before EVERY new WebView virtual entry.
window.ol2050Gate=function(pts,candleTs){
 try{
 const x=model(pts),base=n('olStake'),cap=n('olCapital'),pay=n('olPayout')/100;
 if(x.decision!=='RESEARCH')return {allow:false,reason:'2050 '+x.decision+' · '+x.reason};
 if(!x.fresh||x.ts!==candleTs)return {allow:false,reason:'Candle freshness mismatch'};
 if(!Number.isFinite(base)||base<=0||!Number.isFinite(pay)||pay<=0||pay>1)return {allow:false,reason:'Invalid stake/payout'};
 // Reserve pending exposure so repeated signals cannot exceed daily loss budget.
 const pending=(Array.isArray(liveOpen)?liveOpen:[]).filter(t=>t.selected&&!t.settled&&today(t.ts)===today(Date.now())).reduce((a,t)=>a+(Number(t.stake)||0),0);
 const remaining=x.remaining-pending;
 const multiplier=x.health==='EXCELLENT'&&x.score>=85?3:x.health==='EXCELLENT'&&x.score>=75?2:1;
 const proposed=Math.min(base*multiplier,cap*.02,remaining);
 if(proposed<base)return {allow:false,reason:'Insufficient risk budget including pending exposure'};
 if(x.paper.pnl>=x.goal)return {allow:false,reason:'Daily virtual profit target reached'};
 return {allow:true,stake:Number(proposed.toFixed(2)),payout:pay,reason:'Research-only virtual entry'};
 }catch(e){return {allow:false,reason:'Risk gate error: '+e.message}}
};
window.ol2050Update=function(pts){if(pts)lastPts=pts;try{render(model(lastPts))}catch(e){set('ol2050Evidence','Module unavailable: '+e.message)}};
function exportJson(name,data){const url=URL.createObjectURL(new Blob([JSON.stringify(data,null,2)],{type:'application/json'})),a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),3000)}
$('olExportBlackBox').onclick=()=>exportJson('Olympia2050_BlackBox.json',{exportedAt:new Date().toISOString(),audit,oneWay:typeof oneWayDays!=='undefined'?oneWayDays:[],healthHistory:typeof v11DailyHealth!=='undefined'?v11DailyHealth:[],paperTrades:savedTrades});
$('olRunDisaster').onclick=()=>{const x=model(lastPts),loss=15*x.stake;set('olDisasterResult','15 straight losses: -'+money(loss)+' ('+(x.cap?100*loss/x.cap:0).toFixed(1)+'% capital). Daily stop budget '+money(x.budget)+' must prevent continuing. No orders placed.')};
$('olAsk').onclick=()=>{
 const q=$('olQuestion').value.toLowerCase();let a='Ask about best time, one-way, wins or target. Local records only.';
 if(/time|समय|टाइम|best/.test(q)){const map={};for(const z of v11DailyHealth){const t=map[z.slot]||(map[z.slot]={n:0,good:0});t.n++;if(['GOOD','EXCELLENT'].includes(z.level))t.good++}const best=Object.entries(map).filter(([k,v])=>v.n>=5).sort((a,b)=>b[1].good/b[1].n-a[1].good/a[1].n)[0];a=best?'Observed favorable slot '+best[0]+' · '+best[1].good+'/'+best[1].n+' samples; no future guarantee':'Insufficient repeated observations'}
 else if(/one|way|up|down|दिशा/.test(q))a='Recorded one-way intervals: '+oneWayDays.length;
 else if(/win|loss|जीत|हार/.test(q)){const p=currentPaper();a='Today saved virtual WIN '+p.wins+' · LOSS '+p.losses+' · illustrative P/L '+money(p.pnl)}
 else if(/risk|target|टारगेट|पैसा/.test(q))a=$('olTargetDifficulty').textContent+' · '+$('olTargetReason').textContent;
 set('olAnswer',a);
};
for(const id of ['olCapital','olStake','olRiskPct','olTarget','olPayout'])$(id).addEventListener('change',()=>window.ol2050Update(null));
window.ol2050Update(null);
})();