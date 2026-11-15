/* Olympia 2050 Research Suite v3.0: forward-only experiments, never broker execution. */
(function(){
'use strict';
const $=id=>document.getElementById(id),put=(id,s)=>{const e=$(id);if(e)e.textContent=String(s)};
const KEY='olympia_research_suite_v3';
let records=[];try{const x=JSON.parse(localStorage.getItem(KEY)||'[]');if(Array.isArray(x))records=x.filter(z=>z&&typeof z.id==='string').slice(-6000)}catch(e){}
let lastDiagnostic='',lastPts=null;
const day=ts=>new Date(ts).toLocaleDateString('en-CA');
const slot=ts=>{const d=new Date(ts);return String(d.getHours()).padStart(2,'0')+':'+(d.getMinutes()<30?'00':'30')};
function save(){try{localStorage.setItem(KEY,JSON.stringify(records))}catch(e){put('rsExplain','Storage full: export records and free space')}}
function stats(arr){const done=arr.filter(x=>x.status==='WIN'||x.status==='LOSS'),w=done.filter(x=>x.status==='WIN').length;return {n:done.length,w,l:done.length-w,p:done.length?w/done.length:0}}
function wilson(s){if(!s.n)return 0;const z=1.96,p=s.p,n=s.n;return (p+z*z/(2*n)-z*Math.sqrt(p*(1-p)/n+z*z/(4*n*n)))/(1+z*z/n)}
function check(pts){if(!Array.isArray(pts)||pts.length<42)return {ok:false,reason:'Need 42 consecutive 5-minute candles'};
 const a=pts.slice(-42);for(let i=0;i<a.length;i++){const x=a[i];if(!Number.isFinite(x.ts)||!Number.isFinite(x.price)||x.price<=0)return {ok:false,reason:'Invalid candle price or timestamp'};if(i&&x.ts-a[i-1].ts!==300000)return {ok:false,reason:'Gap or duplicate candle timestamps'}}
 const age=Date.now()-a[a.length-1].ts;if(age<0||age>660000)return {ok:false,reason:'Candle stale or future timestamp'};return {ok:true,last:a[a.length-1],age}}
function classify(pts){
 const a=pts.slice(-22),moves=a.slice(1).map((x,i)=>x.price-a[i].price),abs=moves.map(Math.abs),total=abs.reduce((x,y)=>x+y,0),net=moves.reduce((x,y)=>x+y,0);
 const eff=total?Math.abs(net)/total:0,flips=moves.slice(1).filter((v,i)=>v*moves[i]<0).length,avg=total/moves.length;
 const near=abs.slice(-5).reduce((x,y)=>x+y,0)/5,far=abs.slice(0,16).reduce((x,y)=>x+y,0)/16,ratio=far?near/far:1;
 const regime=ratio>1.8?'VOLATILE':eff>.55&&flips<=8?'TRENDING':eff<.25&&flips>=9?'CHOPPY':'RANGING';
 return {regime,eff,flips,ratio,avg,net,moves,direction:net>0?'UP':net<0?'DOWN':'FLAT'};
}
function candidates(pts,x){
 const a=pts.slice(-42),p=a.map(z=>z.price),n=p.length-1,last=p[n],prev=p[n-1],before=p[n-2],m=x.moves,avg=x.avg||1e-12;
 const hi=(i,j)=>Math.max(...p.slice(i,j)),lo=(i,j)=>Math.min(...p.slice(i,j)),h=hi(n-20,n),l=lo(n-20,n),range=h-l;
 const recent=m.slice(-3),old=m.slice(-6,-3),ma=k=>p.slice(-k).reduce((u,v)=>u+v,0)/k;
 const r=[],add=(name,dir,condition)=>{if(condition&&dir!=='FLAT')r.push({name,dir,eligible:true})};
 const dir=x.direction,opp=dir==='UP'?'DOWN':'UP',delta=last-prev,prior=prev-before,rise=delta>0?'UP':delta<0?'DOWN':'FLAT';
 const nearHigh=last>=h-avg,nearLow=last<=l+avg,signs=m.slice(-6).map(z=>Math.sign(z));
 add('Strong Trend',dir,x.eff>.6&&x.flips<=7);
 add('Trend Pullback',dir,x.eff>.35&&rise!==dir&&rise!=='FLAT'&&Math.abs(delta)<avg*1.6);
 add('Range Reversal',nearHigh?'DOWN':'UP',x.regime==='RANGING'&&(nearHigh||nearLow)&&Math.abs(delta)<avg*1.4);
 add('False Breakout',prev>hi(n-21,n-1)?'DOWN':'UP',(prev>hi(n-21,n-1)&&last<h)||(prev<lo(n-21,n-1)&&last>l));
 add('Breakout Retest',last>h?'UP':'DOWN',(last>h&&prev<=h)||(last<l&&prev>=l));
 add('Three-Candle Momentum',rise,recent.every(v=>v>0)||recent.every(v=>v<0));
 add('Exhaustion',prior>0?'DOWN':'UP',Math.abs(prior)>avg*2&&Math.abs(delta)<Math.abs(prior)*.35);
 // Wick Rejection, Inside Candle and Engulfing require OHLC, unavailable in price-only source.
 add('Volatility Expansion',rise,x.ratio>1.7&&Math.abs(delta)>avg*1.5);
 add('Volatility Compression',dir,x.ratio<.65&&x.eff>.3);
 add('Trend Acceleration',dir,x.eff>.4&&recent.reduce((u,v)=>u+Math.abs(v),0)>old.reduce((u,v)=>u+Math.abs(v),0)*1.6);
 add('Trend Deceleration',opp,x.eff>.4&&recent.reduce((u,v)=>u+Math.abs(v),0)<old.reduce((u,v)=>u+Math.abs(v),0)*.55);
 add('Alternating Candles',rise==='UP'?'DOWN':'UP',signs.every((v,i)=>i===0||v*signs[i-1]<0));
 add('Support Resistance',nearHigh?'DOWN':'UP',range>avg*3&&(nearHigh||nearLow)&&x.eff<.45);
 // Time DNA is descriptive only until independently validated.
 add('Market Regime Switch',dir,x.regime==='TRENDING'&&x.eff>.55);
 add('Multi-Timeframe Agreement',dir,(ma(5)>ma(15)&&ma(15)>ma(30)&&dir==='UP')||(ma(5)<ma(15)&&ma(15)<ma(30)&&dir==='DOWN'));
 // Skipped Signal Replay is observational, not an entry rule.
 add('Micro Trend',rise,recent.every(v=>v>0)||recent.every(v=>v<0));
 add('Volatility Breakout',rise,(last>h||last<l)&&Math.abs(delta)>avg*1.5);
 return r;
}
function settle(pts){
 const map=new Map(pts.map(x=>[x.ts,x.price]));let changed=false;
 for(const x of records){if(x.status!=='PENDING'||!map.has(x.expiry))continue;
  const exit=map.get(x.expiry);x.exit=exit;x.status=exit===x.entry?'TIE':((exit>x.entry)===(x.dir==='UP')?'WIN':'LOSS');changed=true}
 if(changed)save();
}
function explanation(x,c,gate,selected){
 const a=['मार्केट '+x.regime,'दिशा '+x.direction,'Trend efficiency '+Math.round(x.eff*100)+'%','Volatility '+x.ratio.toFixed(2),'Direction flips '+x.flips+'/20'];
 a.push('प्रयोग '+(selected?selected.name:'कोई पैटर्न नहीं'));a.push(gate?'Risk gate passed':'Risk gate blocked');
 return a.join(' · ')+' · ये भविष्यवाणी नहीं है';
}
function render(pts,integrity,x,gate,selected){
 const done=records.filter(z=>(z.status==='WIN'||z.status==='LOSS')&&z.kind==='CANDIDATE'),st=stats(records.filter(z=>z.kind==='CANDIDATE'));
 put('rsForward','FORWARD: '+records.filter(z=>z.kind==='CANDIDATE').length);put('rsWin','WIN: '+st.w);put('rsLoss','LOSS: '+st.l);put('rsSkip','SKIPPED: '+records.filter(z=>z.kind==='SKIPPED').length);
 if(!integrity.ok){put('rsData','SOURCE: BLOCKED');put('rsExplain','Data Integrity Guardian: '+integrity.reason);return}
 put('rsData','SOURCE: VERIFIED');put('rsRegime','REGIME: '+x.regime);
 const votes={direction:x.eff>.4,consistency:x.flips<=8,volatility:x.ratio>=.65&&x.ratio<=1.6,data:true,risk:gate,history:st.n>=100,regime:x.regime==='TRENDING'};
 put('rsCouncil','7 AGENTS: '+Object.values(votes).filter(Boolean).length+'/7');
 put('rsExplain',explanation(x,null,gate,selected));
 const cap=Number($('olCapital')?.value)||0,base=Number($('olStake')?.value)||0,riskPct=Number($('olRiskPct')?.value)||0,payout=Number($('olPayout')?.value)||85;
 const budget=cap*riskPct/100,lossesToday=records.filter(z=>z.kind==='CANDIDATE'&&z.status==='LOSS'&&day(z.ts)===day(Date.now())).length;
 const winsToday=records.filter(z=>z.kind==='CANDIDATE'&&z.status==='WIN'&&day(z.ts)===day(Date.now())).length;
 const limited=Math.min(base,Math.max(0,budget-lossesToday*base)),profitLock=winsToday>lossesToday?base*.5:base,cooldown=lossesToday>=2?0:base;
 put('rsMoney','Fixed $'+base.toFixed(2)+' | 1% Fractional $'+(cap*.01).toFixed(2)+' | Daily Cap $'+limited.toFixed(2)+' | Profit Lock $'+profitLock.toFixed(2)+' | 2-Loss Cooldown $'+cooldown.toFixed(2)+' | illustrative, not stake advice | payout break-even '+(100/(1+payout/100)).toFixed(1)+'%');
 put('rsPatternCoverage','20 named patterns: 15 price-based rules + 3 OHLC-only unavailable + 2 observational; no fabricated OHLC');
 put('rsResearchAlert',selected?'SHADOW '+selected.dir+' · '+selected.name+' · research only':'SHADOW WAIT · no current pattern');

 const names=[...new Set(records.filter(z=>z.kind==='PATTERN_TEST').map(z=>z.strategy))];
 const ranks=names.map(name=>({name,s:stats(records.filter(z=>z.strategy===name&&z.kind==='PATTERN_TEST'))})).sort((a,b)=>b.s.n-a.s.n);
 put('rsStrategy','Strategy Tournament (observation only): '+(ranks.slice(0,5).map(z=>z.name+' '+z.s.w+'W/'+z.s.l+'L ('+z.s.n+' settled)').join(' · ')||'awaiting records')+' · No auto-switch until independent validation');
 const time=slot(integrity.last.ts),same=records.filter(z=>z.slot===time&&z.kind==='CANDIDATE'),ts=stats(same);
 put('rsTime','Time DNA '+time+' · '+ts.w+'W/'+ts.l+'L · sample '+ts.n+' · '+(ts.n<30?'insufficient evidence':'historical only'));
 const lastLoss=records.slice().reverse().find(z=>z.status==='LOSS'&&z.kind==='CANDIDATE');
 put('rsAutopsy',lastLoss?'Last LOSS autopsy: '+lastLoss.strategy+' · '+lastLoss.regime+' · '+lastLoss.dir+' · '+new Date(lastLoss.ts).toLocaleString()+' · direction failed after 5m; cause not established':'No settled LOSS to analyze');
 put('rsForecast','Scenario simulator (not predictions): next candle could rise, fall or remain flat; observed mean move '+x.avg.toFixed(6)+' · illustrative ±'+(x.avg*1.5).toFixed(6));
 put('rsConfidence',st.n<100?'Observed sample '+st.n+'/100; no calibrated forecast':('Observed '+(st.p*100).toFixed(1)+'% WIN · 95% Wilson lower bound '+(100*wilson(st)).toFixed(1)+'% · not independent validation'));
}
window.olResearchSuite=function(pts){
 try{
 lastPts=pts;const v=check(pts);if(!v.ok){render(pts,v,null,false,null);return}
 const x=classify(pts),set=settle(pts),all=candidates(pts,x);
 const gate=typeof window.ol2050Gate==='function'&&!!window.ol2050Gate(pts,v.last.ts)?.allow;
 const chosen=all[0]||null;
 // Forward only: one snapshot per verified newest candle, even for rejected decisions.
 if(v.age<=60000&&!records.some(z=>z.id==='RS|'+v.last.ts)){
  const kind=chosen?'CANDIDATE':'SKIPPED';
  records.push({id:'RS|'+v.last.ts,ts:v.last.ts,expiry:v.last.ts+300000,entry:v.last.price,dir:chosen?.dir||x.direction,strategy:chosen?.name||'No pattern',regime:x.regime,slot:slot(v.last.ts),kind,status:chosen&&chosen.dir?'PENDING':'NO_ENTRY',riskPassed:gate,eff:x.eff});
  for(const candidate of all){records.push({id:'PAT|'+v.last.ts+'|'+candidate.name,ts:v.last.ts,expiry:v.last.ts+300000,entry:v.last.price,dir:candidate.dir,strategy:candidate.name,regime:x.regime,slot:slot(v.last.ts),kind:'PATTERN_TEST',status:'PENDING',riskPassed:gate,eff:x.eff})}records=records.slice(-6000);save();
 }
 render(pts,v,x,gate,chosen);
 }catch(e){put('rsData','SOURCE: ERROR');put('rsExplain','Research Suite error: '+e.message)}
};
window.rsStress=function(){const cap=Number($('olCapital')?.value)||0,stake=Number($('olStake')?.value)||0,risk=Number($('olRiskPct')?.value)||0;
 if(!(cap>0&&stake>0&&risk>0)){put('rsStressOut','Enter valid capital, stake and daily risk first');return}
 const budget=cap*risk/100,maxLosses=Math.floor(budget/stake),loss=Math.min(15,maxLosses)*stake;
 put('rsStressOut','Simulation only: 15 losses would cost $'+(15*stake).toFixed(2)+'; daily risk budget $'+budget.toFixed(2)+' permits at most '+maxLosses+' base-stake losses. No trades placed.')};
window.rsExport=function(format){
 try{
 const blob=format==='csv'?new Blob([['id','time','strategy','regime','direction','status','kind','riskPassed'].join(',')+'\n'+records.map(x=>[x.id,new Date(x.ts).toISOString(),x.strategy,x.regime,x.dir,x.status,x.kind,x.riskPassed].map(z=>'"'+String(z??'').replace(/"/g,'""')+'"').join(',')).join('\n')],{type:'text/csv'}):new Blob([JSON.stringify({version:3,exportedAt:new Date().toISOString(),records},null,2)],{type:'application/json'});
 const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download='olympia-research-'+day(Date.now())+'.'+(format==='csv'?'csv':'json');document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),5000)
 }catch(e){put('rsExplain','Export failed: '+e.message)}
};
})();