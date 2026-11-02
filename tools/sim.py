#!/usr/bin/env python3
"""Referenz-Engine des Typ-Finders (spiegelt TypFinder.kt) und Simulation: Ein 'Persona'-Antworter je Typ/Untertyp."""
import json, os, random, sys
ROOT=os.path.join(os.path.dirname(os.path.abspath(__file__)),"..")
BANK=json.load(open(os.path.join(ROOT,"app/src/main/assets/typfinder.json"),encoding="utf-8"))["questions"]
BY={q["id"]:q for q in BANK}
CEN={"bauch":[8,9,1],"herz":[2,3,4],"kopf":[5,6,7]}
CONF={1:[6,8,2],2:[4,3,1],3:[8,7,2],4:[5,9,2],5:[4,9,6],6:[1,7,5],7:[6,3],8:[3,9,1],9:[5,4,8]}
INST=["sp","so","sx"]
class S:
    def __init__(s):
        s.t=[0.0]*10; s.i={k:0.0 for k in INST}; s.b={"bauch":0.0,"herz":0.0,"kopf":0.0}
        s.w=[0.0,0.0]; s.asked=set(); s.phase="A"; s.hist=[]; s.gegen=False; s.cn=0; s.rounds=0
def apply(s,q,idx):
    s.asked.add(q["id"])
    if idx<0: return
    for k,v in q["o"][idx]["w"].items():
        if k in INST: s.i[k]+=v
        elif k in s.b:
            s.b[k]+=v
            for ty in CEN[k]: s.t[ty]+=v
        elif k=="wl": s.w[0]+=v
        elif k=="wh": s.w[1]+=v
        else: s.t[int(k)]+=v
    s.hist.append((q["id"],idx))
def ranked(s): return sorted(range(1,10),key=lambda x:(-s.t[x],x))
def lead(vals):
    v=sorted(vals,reverse=True); return v[0]-v[1]
def pool(st): return [q for q in BANK if q["st"]==st]
def pick(s):
    """Nächste Runde (Liste von Fragen) oder None bei Ende. Spiegelt TypFinder.next()."""
    while True:
        ph=s.phase
        if ph=="A":
            n=sum(1 for q in pool("A") if q["id"] in s.asked)
            if n>=6 and (lead(list(s.i.values()))>=4.0 or n>=10):
                s.phase="B"; continue
            r=[q for q in pool("A") if q["id"] not in s.asked][:3]
            if not r: s.phase="B"; continue
            return "Instinkt",r
        if ph=="B":
            n=sum(1 for q in pool("B") if q["id"] in s.asked)
            if n>=3 and (lead(list(s.b.values()))>=3.0 or n>=9):
                s.phase="C"; continue
            r=[q for q in pool("B") if q["id"] not in s.asked][:3]
            if not r: s.phase="C"; continue
            return "Zentrum",r
        if ph=="C":
            rk=ranked(s); top3=rk[:3]; l=s.t[rk[0]]-s.t[rk[1]]
            if (s.cn>=6 and l>=4.0) or s.cn>=15:
                s.phase="G"; continue
            r=cover(s,top3,3)
            if not r: s.phase="G"; continue
            return "Typ",r
        if ph=="G":
            rk=ranked(s); top=rk[0]
            if s.gegen: s.phase="D"; continue
            s.gegen=True
            cands=[rk[1]]+[x for x in CONF[top] if x!=rk[1]]
            r=[]
            for c in cands:
                for q in BANK:
                    if q["st"]=="C" and q.get("pair") and set(q["ty"])=={top,c} and q["id"] not in s.asked and q not in r:
                        r.append(q); break
                if len(r)>=3: break
            if not r: continue
            return "Gegenprobe",r
        if ph=="D":
            top=ranked(s)[0]
            r=[q for q in pool("D") if q["ty"]==[top] and q["id"] not in s.asked][:3]
            s.phase="E"
            if r: return "Untertyp",r
            continue
        if ph=="E":
            top=ranked(s)[0]
            r=[q for q in pool("E") if q["ty"]==[top] and q["id"] not in s.asked]
            s.phase="X"
            if r: return "Flügel",r
            continue
        return None
def cover(s,top3,n):
    sc=[]
    for q in BANK:
        if q["st"]!="C" or q["id"] in s.asked: continue
        cov=[1 if t in q["ty"] else 0 for t in top3]
        if cov[0]+cov[1]==0: continue
        score=cov[0]*4+cov[1]*3+cov[2]*1.5 + (0.5 if not q.get("pair") else 0)
        if cov[0]+cov[1]<2 and not (len(cov)>1 and (cov[0] or cov[1]) and cov[2]): continue
        sc.append((-score,q["id"],q))
    sc.sort(key=lambda x:(x[0],x[1]))
    return [x[2] for x in sc[:n]]
def run(persona):
    s=S(); log=[]
    while True:
        r=pick(s)
        if r is None: break
        name,qs=r; s.rounds+=1
        for q in qs:
            idx=persona(q,s)
            apply(s,q,idx)
            if q["st"]=="C": s.cn+=1
            log.append(q["id"])
    return s,log
def result(s):
    rk=ranked(s); inst=sorted(INST,key=lambda k:-s.i[k])
    return rk, inst
if __name__=="__main__":
    random.seed(1)
    ok=0; tot=0; lens=[]
    for ty in range(1,10):
        for noise in (0.0,0.15,0.25):
            for it in range(40 if noise else 1):
                inst=random.choice(INST)
                def persona(q,s):
                    if random.random()<noise*0.5: return -1
                    if q["st"] in("A","D"):
                        key=inst
                        for j,o in enumerate(q["o"]):
                            w=o["w"]
                            if (key in w and w[key]>0) or (key in w and q.get("blind") and False): pass
                        # Instinkt-Persona: wählt Option mit eigenem Instinkt, bei blind den vernachlässigten (beliebig anderen)
                        if q.get("blind"):
                            other=[k for k in INST if k!=inst][0]
                            for j,o in enumerate(q["o"]):
                                if other in o["w"]: return j
                        for j,o in enumerate(q["o"]):
                            if key in o["w"] and o["w"][key]>0: return j
                    if q["st"]=="B":
                        c=[k for k,v in CEN.items() if ty in v][0]
                        for j,o in enumerate(q["o"]):
                            if c in o["w"]: return j
                    if q["st"]=="C":
                        if random.random()<noise: return random.randrange(len(q["o"]))
                        for j,o in enumerate(q["o"]):
                            if str(ty) in o["w"]: return j
                        return -1
                    if q["st"]=="E": return 0
                    return random.randrange(len(q["o"]))
                s,log=run(persona); rk,ii=result(s)
                tot+=1; lens.append(len(log))
                if rk[0]==ty and ii[0]==inst: ok+=1
    print("Treffer Typ+Instinkt:",ok,"von",tot, "| Fragen: min",min(lens),"mittel",sum(lens)/len(lens),"max",max(lens))
