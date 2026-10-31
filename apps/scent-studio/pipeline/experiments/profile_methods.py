"""노트 프로필 계산 방식 × "강" 강도 비교 실험 (2026-10-30). 앱 폴더에서: py pipeline/experiments/profile_methods.py
결과는 HANDOFF "다음 세션에서 할 일" 1번 표. 2026-10-31 재현을 evaluate.py 방식(같은 향수 다른 농도도 맞힘)으로 바꿨다. 실제 카탈로그(pipeline/data/out/catalog.json.gz)가 있어야 한다."""
import sys,gzip,json,math,copy; sys.path.insert(0,'pipeline'); sys.stdout.reconfigure(encoding='utf-8')
from collections import defaultdict, Counter
import engine_ref as E, evaluate as V
c0=json.load(gzip.open('pipeline/data/out/catalog.json.gz'))
A=[a['key'] for a in c0['accords']]; afam={a['key']:a['family'] for a in c0['accords']}
P=c0['perfumes']; N=len(P)
def vec(acc):
    w=[5,4,3,2,1][:len(acc)]; t=sum(w); v=[0.0]*len(A)
    for a,x in zip(acc,w): v[A.index(a)]=100*x/t
    return v
pv={p['key']:vec(p['accords']) for p in P}
g=[sum(pv[p['key']][i] for p in P)/N for i in range(len(A))]
by=defaultdict(list)
for p in P:
    for k in {k for L in ('top','middle','base') for k in p[L]}: by[k].append(p)
fam={n['key']:n['family'] for n in c0['notes']}; df={k:len(v) for k,v in by.items()}
mean={k:[sum(pv[p['key']][i] for p in ps)/len(ps) for i in range(len(A))] for k,ps in by.items()}
def build(method):
    ex={}
    for k,m in mean.items():
        if method=='diff': e=[max(0,m[i]-g[i]) for i in range(len(A))]
        elif method=='ratio': e=[max(0,m[i]/g[i]-1) if g[i]>0 else 0 for i in range(len(A))]
        elif method=='ppmi': e=[m[i]*max(0,math.log(m[i]/g[i])) if m[i]>0 and g[i]>0 else 0 for i in range(len(A))]
        elif method=='diff_sq': e=[max(0,m[i]-g[i])**2 for i in range(len(A))]
        ex[k]=e
    fs=defaultdict(lambda:[0.0]*len(A)); fw=Counter()
    for k,e in ex.items():
        fw[fam[k]]+=df[k]
        for i in range(len(A)): fs[fam[k]][i]+=df[k]*e[i]
    prior={f:[x/fw[f] for x in v] for f,v in fs.items()}
    c=copy.deepcopy(c0)
    for n in c['notes']:
        e=ex[n['key']]; f=prior[fam[n['key']]]
        s=[(df[n['key']]*e[i]+20*f[i])/(df[n['key']]+20) for i in range(len(A))]
        t=sum(s) or 1; n['profile']=[x/t for x in s]
    return c
by_key={p['key']:p for p in P}
tier={n['key']:n['tier'] for n in c0['notes']}
picks,brands=[],set()
for p in sorted(P,key=lambda p:(-p['ratingCount'],p['key'])):
    notes=[(k,l) for l in ('top','middle','base') for k in p[l][:3] if tier[k]!='hidden']  # evaluate.py와 같은 고르기
    if p['brand'] in brands or not notes: continue
    picks.append((p,notes)); brands.add(p['brand'])
    if len(picks)==20: break
def evaluate(label, e):
    bs=V.blend(V.BLENDS[0][1])
    ratios=e.display_ratios(bs)[:3]
    recs=e.recommend(bs,k=5)
    first_citrus=sum(1 for r in recs if by_key[r['perfumeKey']]['accords'][0]=='citrus')
    cf=sum(1 for r in recs if {'citrus','floral'}<= {afam[a] for a in by_key[r['perfumeKey']]['accords']})
    # 재현은 evaluate.py와 같게: Top 10 안에서 같은 향수(group, 다른 농도 포함)가 3위 안이면 맞힘 (2026-10-31)
    def rank(p,n):
        gs=[by_key[r['perfumeKey']]['group'] for r in e.recommend([E.BlendNote(k,l,1.0) for k,l in n],k=10)]
        return gs.index(p['group'])+1 if p['group'] in gs else 99
    rep=sum(1 for p,n in picks if rank(p,n)<=3)
    br=set()
    for _,s in V.BLENDS: br|={by_key[r['perfumeKey']]['brand'] for r in e.recommend(V.blend(s),k=10)}
    print(f"{label:14} 조합{ratios} 시트러스1위 {first_citrus}/5 시트러스+플로럴 {cf}/5 재현 {rep}/20 브랜드 {len(br)}")
for method in ['diff','ppmi','diff_sq']:
    base=build(method)
    for strong in (1.5,2.0):
        e=E.Engine(base)
        orig=e.blend_vector
        e.blend_vector=(lambda orig,strong: lambda blend: orig([E.BlendNote(x.note_key,x.layer,strong if x.intensity==1.5 else x.intensity) for x in blend]))(orig,strong)
        evaluate(f"{method}/강{strong}", e)
