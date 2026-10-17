#!/usr/bin/env python3
"""Add source-bounded reference data; never generates executable gameplay adapters.
Run after build_srd51_reference.py, with the same exact source PDF. PyMuPDF is maintainer-only.
"""
import json,re,hashlib,sys,copy
from pathlib import Path
import fitz
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources/data/tacticalcombat/reference'
SHA='d3f94417d2532f42a5abaec07e71a59007bf6cc46992c6458be6667f7a9f1e34'
def clean(s):return re.sub(r'-{2,}','-',re.sub(r'\s+',' ',s.replace('\xad','').replace('‐','-').replace('‑','-'))).strip()
def slug(s):return re.sub('[^a-z0-9]+','-',s.lower()).strip('-')
def digest(s):return hashlib.sha256(s.encode()).hexdigest()
def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
class Source:
 def __init__(self,path):
  assert hashlib.sha256(Path(path).read_bytes()).hexdigest()==SHA,'Unreviewed source PDF'
  self.pdf=fitz.open(path);self.lines=[]
  for pi,page in enumerate(self.pdf):
   for col in [0,1]:
    group=[]
    for block in page.get_text('dict')['blocks']:
     for line in block.get('lines',[]):
      x,y,xx,yy=line['bbox']
      if y<60 or yy>735 or (x>=306)!=bool(col):continue
      text=clean(''.join(s['text'] for s in line['spans']))
      if text:group.append(dict(text=text,page=pi+1,x=x,y=y,size=max(s['size'] for s in line['spans']),font=line['spans'][0]['font'],heading=any(s['font']=='GillSans-SemiBold' for s in line['spans'])))
    ordered=sorted(group,key=lambda l:l['y'])
    for row in ordered:
     if self.lines and row['heading'] and self.lines[-1]['heading'] and row['page']==self.lines[-1]['page'] and abs(row['x']-self.lines[-1]['x'])<1 and abs(row['size']-self.lines[-1]['size'])<.2 and 0<row['y']-self.lines[-1]['y']<20:
      self.lines[-1]['text']+=' '+row['text'];self.lines[-1]['y']=row['y']
     else:self.lines.append(row)
 def body(self,start,end):return ' '.join(l['text'] for l in self.lines[start:end])
 def pages(self,start,end):return sorted({l['page'] for l in self.lines[start:end]})

def expand(path):
 source=Source(path);ls=source.lines;doc=json.loads((RES/'catalog-v1.json').read_text());base=copy.deepcopy(doc);assert len(base['entries'])==623,'Run the baseline importer first, then this additive expansion';index={e['id']:e for e in doc['entries']};inventory=[];ambiguities=[]
 def entry(category,name,start,end,data,related=None,update=False):
  identity='srd51:'+category+':'+slug(name);text=source.body(start+1,end);assert text,(name,start,end)
  evidence=dict(id=identity,name=name,category=category,pages=source.pages(start,end),startLine=start,endLine=end,sourceTextSha256=digest(source.body(start,end)))
  assert identity not in {i['id'] for i in inventory},('duplicate source candidate',identity)
  inventory.append(evidence)
  if identity in index:
   e=index[identity]
   # Preserve existing reviewed bodies and all executable-source fields. Enrichment is additive.
   e['data']['reference']=data
   if update:e['description']=text;e['pages']=evidence['pages']
  else:
   e=dict(id=identity,category=category,name=name,source='srd51-2014',pages=evidence['pages'],verification='PDF_TEXT_VERIFIED',description=text,data=data,related=related or [])
   doc['entries'].append(e);index[identity]=e
  return e
 # Independent stat-block inventory: every Armor Class label in the monster chapters/appendices.
 acs=[i for i,l in enumerate(ls) if (261<=l['page']<=357 or 366<=l['page']<=403) and l['text'].startswith('Armor Class ')]
 blocks=[]
 for i in acs:
  start=next((j-1 for j in range(i-1,max(-1,i-6),-1) if re.match(r'^(Tiny|Small|Medium|Large|Huge|Gargantuan) ',ls[j]['text'])),None)
  assert start is not None,('monster classification',i)
  # Multi-line titles are uncommon; name is the closest stat-block title, not chapter heading.
  assert ls[start]['font']=='Calibri-Bold' and abs(ls[start]['size']-12)<.2,('monster name',ls[start])
  blocks.append((start,i))
 for n,(start,ac_index) in enumerate(blocks):
  # Stat blocks end at the next block or chapter boundary; non-stat lore remains explicitly source text.
  end=blocks[n+1][0] if n+1<len(blocks) else len(ls)
  if ls[start]['page']<358:end=min(end,next(i for i in range(start+1,len(ls)) if ls[i]['page']>=358))
  end=min([end]+[i for i in range(start+1,end) if ls[i]['heading'] and ls[i]['size']>=13.5])
  body=source.body(start+1,end);name=ls[start]['text'];rows=ls[start+1:end];text='\n'.join(l['text'] for l in rows)
  try:
   kind=clean(text[:text.index('Armor Class')]);ac=int(re.search(r'Armor Class (\d+)',text)[1]);hp=int(re.search(r'Hit Points (\d+)',text)[1]);hd=re.search(r'Hit Points \d+ \(([^)]+)\)',text)
   stats=re.search(r'STR\s+DEX\s+CON\s+INT\s+WIS\s+CHA\s+(.*?)(?=Saving Throws|Skills|Damage|Condition|Senses|Languages|Challenge)',text,re.S)
   scores=[int(v) for v in re.findall(r'(\d+) \([+−-]\d+\)',stats[1])];assert len(scores)==6
   challenge=re.search(r'Challenge ([\d/]+) \(([^)]+)\)',text);assert challenge
   labels=['Armor Class','Hit Points','Speed','Saving Throws','Skills','Damage Vulnerabilities','Damage Resistances','Damage Immunities','Condition Immunities','Senses','Languages','Challenge','Actions','Reactions','Legendary Actions','Lair Actions','Regional Effects']
   def field(label):
    m=re.search(r'(?:^|\n)'+re.escape(label)+r' (.*?)(?=\n(?:'+ '|'.join(map(re.escape,labels))+r')(?: |\n)|\nSTR|$)',text,re.S);return clean(m[1]) if m else ''
   after=text.index(challenge[0])+len(challenge[0]);markers=list(re.finditer(r'\n(Actions|Reactions|Legendary Actions|Lair Actions|Regional Effects)\n',text[after:]));sections={}
   for m,mark in enumerate(markers):sections[mark[1]]=clean(text[after+mark.end():after+(markers[m+1].start() if m+1<len(markers) else len(text)-after)])
   traits=clean(text[after:after+markers[0].start()]) if markers else clean(text[after:])
   data=dict(size=kind.split()[0],type=kind.split(',',1)[0].split(' ',1)[1],alignment=kind.split(',',1)[1].strip(),ac=ac,armor=field('Armor Class'),hp=hp,hitDice=hd[1] if hd else '',speed=field('Speed'),abilities=dict(zip(['str','dex','con','int','wis','cha'],scores)),saves=field('Saving Throws'),skills=field('Skills'),vulnerabilities=field('Damage Vulnerabilities'),resistances=field('Damage Resistances'),immunities=field('Damage Immunities'),conditionImmunities=field('Condition Immunities'),senses=field('Senses'),languages=field('Languages'),challenge=challenge[1],xp=challenge[2],traits=traits,actions=sections.get('Actions',''),reactions=sections.get('Reactions',''),legendaryActions=sections.get('Legendary Actions',''),lairActions=sections.get('Lair Actions',''),regionalEffects=sections.get('Regional Effects',''),spellcasting=traits if 'Spellcasting.' in traits else 'None in this stat block',referenceOnly=True,automated=False)
   data['speedModes']={('walk' if not m[1] else m[1]):int(m[2]) for m in re.finditer(r'(?:^|, )(?:(burrow|climb|fly|swim) )?(\d+) ft\.',data['speed'])}
   data['savingThrowBonuses']={m[1].lower():int(m[2]) for m in re.finditer(r'(Str|Dex|Con|Int|Wis|Cha) ([+−-]\d+)',data['saves'].replace('−','-'))}
   data['spellReferenceStatus']='TEXT_MENTIONS_REVIEW_REQUIRED'
   data['multiattackText']=data['actions'] if 'Multiattack.' in data['actions'] else ''
   data['spellReferences']=[] # Conservative exact-name references, never inferred spell mechanics.
   for spell in base['entries']:
    if spell['category']=='spells' and re.search(r'(?<!\w)'+re.escape(spell['name'].lower())+r'(?!\w)',traits.lower()):data['spellReferences'].append(spell['id'])
   entry('monsters',name,start,end,data,data['spellReferences'])
  except (AssertionError,AttributeError,IndexError,ValueError) as error:
   ambiguities.append(dict(category='monsters',name=name,pages=source.pages(start,end),reason=str(error) or 'Unparsed stat metadata',sourceText=body))
 # Magic item inventory uses italic type/rarity metadata, independently of title sizes/tables.
 itemstarts=[]
 for i,l in enumerate(ls):
  if 207<=l['page']<=253 and l['font']=='Cambria-Italic' and re.match(r'^(Wondrous item|Potion|Armor|Weapon|Ring|Rod|Staff|Wand|Scroll)(?:[, (])',l['text']):
   j=i-1
   while j>0 and ls[j-1]['heading'] and abs(ls[j-1]['size']-12)<.2 and ls[j-1]['page']==ls[j]['page'] and ls[j-1]['y']<ls[j]['y'] and ls[j]['y']-ls[j-1]['y']<18:j-=1
   assert ls[j]['heading'] and abs(ls[j]['size']-12)<.2,('item title',ls[j])
   itemstarts.append((j,i))
 for n,(start,meta) in enumerate(itemstarts):
  end=itemstarts[n+1][0] if n+1<len(itemstarts) else next(i for i,l in enumerate(ls) if l['page']==254)
  # General sentience rules between the alphabetical list and artifact are not part of the last wand.
  end=min([end]+[i for i in range(start+1,end) if ls[i]['heading'] and ls[i]['size']>=18])
  name=source.body(start,meta);text=source.body(meta,end);metadata=[]
  k=meta
  while k<end and ls[k]['font']=='Cambria-Italic':metadata.append(ls[k]['text']);k+=1
  metadata=clean(' '.join(metadata));rarities=[r for r in ['common','uncommon','rare','very rare','legendary','artifact'] if re.search(r'(?<![a-z])'+r'(?=$|[, (])',metadata)]
  # More specific rarity consumes the shorter substring (uncommon != common, very rare != rare).
  if 'very rare' in rarities and 'rare' in rarities:rarities.remove('rare')
  attunement='requires attunement' in metadata.lower()
  charge_paragraphs=' '.join(ls[i]['text'] for i in range(k,end) if re.search(r'charge|regain|dawn|dusk',ls[i]['text'],re.I))
  entry('magic-items',name,start,end,dict(itemType=metadata,rarities=rarities,rarityVaries='varies' in metadata,attunementRequired=attunement,attunementText=metadata if attunement else '',chargesAndRechargeText=charge_paragraphs,restrictionsText=source.body(k,end),chargeIndexStatus='TEXT_INDEX_ONLY_READ_FULL_DESCRIPTION',chargesMaximum=int(re.search(r'has (\d+) charges',text)[1]) if re.search(r'has (\d+) charges',text) else None,automated=False,referenceOnly=True))
 # All source class chapter headings, complete class text, and geometric progression-table rows.
 classnames=['Barbarian','Bard','Cleric','Druid','Fighter','Monk','Paladin','Ranger','Rogue','Sorcerer','Warlock','Wizard']
 classstarts=[next(i for i,l in enumerate(ls) if l['text']==name and l['heading'] and l['size']>24 and 8<=l['page']<=54) for name in classnames]
 classend=next(i for i,l in enumerate(ls) if l['text']=='Beyond 1st Level' and l['page']==56)
 for ci,start in enumerate(classstarts):
  end=classstarts[ci+1] if ci+1<len(classstarts) else classend;name=classnames[ci];parent='srd51:classes:'+name.lower();chapter=ls[start:end]
  # Class tables consist of Calibri ordinal row anchors at the left margin of each table half.
  anchors=[l for l in chapter if l['font']=='Calibri' and re.fullmatch(r'(?:[1-9]|1[0-9]|20)(?:st|nd|rd|th)',l['text']) and (l['x']<85 or 325<l['x']<345)]
  levels=[]
  layouts={
   'Barbarian':([55,87,137,208,247],['level','proficiencyBonus','features','rages','rageDamage']),
   'Fighter':([55,94,151],['level','proficiencyBonus','features']),
   'Rogue':([55,90,151,192],['level','proficiencyBonus','sneakAttack','features']),
   'Monk':([55,87,160,217,260,357],['level','proficiencyBonus','martialArts','kiPoints','unarmoredMovement','features']),
   'Warlock':([55,97,157,289,349,404,447,496],['level','proficiencyBonus','features','cantripsKnown','spellsKnown','pactSlots','pactSlotLevel','invocationsKnown']),
   'Paladin':([55,87,136,294,317,343,368,392],['level','proficiencyBonus','features']+['slot'+str(i) for i in range(1,6)]),
   'Ranger':([55,87,136,336,398,421,447,472,496],['level','proficiencyBonus','features','spellsKnown']+['slot'+str(i) for i in range(1,6)]),
   'Bard':([55,87,136,275,313,350,373,398,421,445,468,491,514,538],['level','proficiencyBonus','features','cantripsKnown','spellsKnown']+['slot'+str(i) for i in range(1,10)]),
   'Sorcerer':([55,87,136,175,275,313,350,373,398,421,445,468,491,514,538],['level','proficiencyBonus','sorceryPoints','features','cantripsKnown','spellsKnown']+['slot'+str(i) for i in range(1,10)]),
   'Cleric':([55,87,136,311,350,373,398,421,445,468,491,514,538],['level','proficiencyBonus','features','cantripsKnown']+['slot'+str(i) for i in range(1,10)]),
   'Druid':([55,87,136,311,350,373,398,421,445,468,491,514,538],['level','proficiencyBonus','features','cantripsKnown']+['slot'+str(i) for i in range(1,10)]),
   'Wizard':([55,87,136,255,297,319,344,368,391,415,438,461,485],['level','proficiencyBonus','features','cantripsKnown']+['slot'+str(i) for i in range(1,10)])}
  for anchor in anchors:
   level=int(re.match(r'\d+',anchor['text'])[0]);same=[l for l in chapter if l['page']==anchor['page'] and (l['font'].startswith('Calibri') or l['font']=='HiraKakuPro-W3') and l['y']>=anchor['y']-.5]
   right=anchor['x']>300
   # Width is full-page when the primary table has spell/resource headers beyond x306.
   full=name not in ['Barbarian','Fighter','Rogue']
   lo=325 if right else 55;hi=558 if right or full else 305
   nextrow=min([l['y'] for l in anchors if l['page']==anchor['page'] and (l['x']>300)==right and l['y']>anchor['y']+1]+[735])
   # Final row terminates before subsequent prose/heading; only table Calibri text retained.
   cells=[l for l in same if lo<=l['x']<hi and l['y']<nextrow-.5 and not l['font'].startswith('Calibri-Bold')]
   # PB anchors separate row levels from ordinal slot-level values.
   pb=next((l for l in cells if re.fullmatch(r'\+[2-6]',l['text']) and abs(l['y']-anchor['y'])<1 and (l['x']-lo)<85),None)
   if pb is None:continue
   # Capture columns by their printed headers (x-aligned, including continuation lines).
   rowtext=' | '.join(l['text'] for l in sorted(cells,key=lambda l:(round(l['x']/3),l['y'])))
   bounds,labels=layouts[name];offset=271 if right else 0;columns={k:[] for k in labels}
   for cell in cells:
    x=cell['x']-offset
    col=max([i for i,b in enumerate(bounds) if x>=b] or [0]);columns[labels[col]].append(cell)
   printed={k:' '.join(c['text'].replace('̶','—') for c in sorted(v,key=lambda r:r['y'])) for k,v in columns.items()}
   def amount(value):
    if not value or value=='—':return 0
    if value=='Unlimited':return 'unlimited'
    assert re.fullmatch(r'[+]?\d+(?:st|nd|rd|th)?',value),(name,level,'bad numeric cell',value,printed)
    return int(re.match(r'[+]?\d+',value)[0])
   typed={k:amount(v) for k,v in printed.items() if k not in ['features','level','proficiencyBonus','martialArts','sneakAttack','unarmoredMovement'] and not k.startswith('slot')}
   if name=='Barbarian' and 'Unlimited' in printed['features']:
    typed['rages']='unlimited';printed['features']=printed['features'].replace('Unlimited ','').replace(' Unlimited','')
   if 'martialArts' in printed:typed['martialArts']=printed['martialArts']
   if 'sneakAttack' in printed:typed['sneakAttack']=printed['sneakAttack']
   if 'unarmoredMovement' in printed:typed['unarmoredMovementFeet']=int(re.search(r'\d+',printed['unarmoredMovement'])[0]) if re.search(r'\d+',printed['unarmoredMovement']) else 0
   slots=[amount(printed[k]) for k in labels if k.startswith('slot')]
   levels.append(dict(level=level,proficiencyBonus=int(pb['text']),sourcePage=anchor['page'],featuresText=printed['features'],resources=typed,spellSlots=slots,tableRowText=rowtext))
  # Some class half tables have x330 row anchors; each level must occur exactly once.
  levels.sort(key=lambda row:row['level'])
  rightanchors=[l for l in anchors if l['x']>300]
  if rightanchors:
   first=min(rightanchors,key=lambda l:(l['page'],l['y']));prevlevel=int(re.match(r'\d+',first['text'])[0])-1
   if prevlevel>0:
    continuation=[l for l in chapter if l['font']=='Calibri' and l['page']==first['page'] and l['y']<first['y']-.5 and l['x']>=layouts[name][0][layouts[name][1].index('features')]+271]
    if continuation:levels[prevlevel-1]['featuresText']+=' '+' '.join(l['text'] for l in continuation)
  assert [r['level'] for r in levels]==list(range(1,21)),(name,'progression anchors',[r['level'] for r in levels])
  heads=[i for i in range(start+1,end) if ls[i]['heading'] and ls[i]['size']>=11.5]
  sections=[];features=[]
  for h,idx in enumerate(heads):
   stop=heads[h+1] if h+1<len(heads) else end;title=ls[idx]['text'];body=source.body(idx+1,stop)
   if not body or title in ['Class Features','Hit Points','Proficiencies','Equipment'] or title.startswith('The '+name):continue
   # Tables/headers with no prose remain in class source text, not duplicate feature records.
   if len(body)<25:continue
   old_id='srd51:features:'+slug(name+': '+title)
   if old_id in {e['id'] for e in inventory}:continue
   old=index.get(old_id);numbers=re.findall(r'(?:at|At|Starting at|Beginning at|When you reach|when you reach|Starting when you reach) (\d+)(?:st|nd|rd|th) level',body)
   minimum=old['data']['minimumLevel'] if old else int(numbers[0]) if numbers else None
   prerequisites=' '.join(l['text'] for l in ls[idx+1:stop] if l['text'].startswith('Prerequisite'))
   if minimum is None and re.search(r'Prerequisite[^:]*: (\d+)(?:st|nd|rd|th) level',body):minimum=int(re.search(r'Prerequisite[^:]*: (\d+)(?:st|nd|rd|th) level',body)[1])
   d=dict(characterClass=parent,minimumLevel=minimum,levelStatus='LEGACY_RETAINED' if old else 'SOURCE_EXPLICIT' if minimum is not None else 'UNRESOLVED',featureKind='conditional-or-choice' if re.search(r'choose|choice|if |while ',body,re.I) else 'ordinary',prerequisitesText=prerequisites,automated=False,referenceOnly=True)
   f=entry('features',name+': '+title,idx,stop,d,[parent]);features.append(f['id']);sections.append(dict(heading=title,feature=f['id'],pages=source.pages(idx,stop)))
  # Keep original class ID/hitdie, replacing only the previously incomplete reference summary with source text.
  hit=index[parent]['data']['hitDie'];fulltext=source.body(start+1,end)
  prof=re.search(r'Proficiencies (.*?) Equipment ',fulltext,re.S);equip=re.search(r'Equipment (.*?)(?:The '+name+r'|Rage |Spellcasting |Fighting Style |Divine Sense |Unarmored Defense |Favored Enemy |Expertise |Sorcerous Origin |Otherworldly Patron )',fulltext,re.S)
  def labeled(label):
   found=next((i for i in range(start,end) if ls[i]['text'].startswith(label+':')),None)
   if found is None:return ''
   out=[ls[found]['text'].split(':',1)[1].strip()]
   for line in ls[found+1:end]:
    if line['heading'] or line['font'].startswith('Calibri') or line['font']=='HiraKakuPro-W3' or re.match(r'^(Armor|Weapons|Tools|Saving Throws|Skills|Hit Dice|Hit Points at [^:]+):',line['text']):break
    out.append(line['text'])
   return ' '.join(out)
  proficiencies={key:labeled(label) for key,label in [('armor','Armor'),('weapons','Weapons'),('tools','Tools'),('saves','Saving Throws'),('skills','Skills')]}
  eq=next(i for i in range(start,end) if ls[i]['text']=='Equipment' and ls[i]['heading']);eqend=next((i for i in range(eq+1,end) if ls[i]['heading'] or ls[i]['font'].startswith('Calibri')),end)
  equipment=source.body(eq+1,eqend)
  c=entry('classes',name,start,end,dict(hitDie=hit,progressionComplete=True,levels=levels,proficiencies=proficiencies,proficienciesText='; '.join(k+': '+v for k,v in proficiencies.items()),startingEquipmentText=equipment,featureReferences=features,sections=sections,automated=False,referenceOnly=True),features,update=True)
  c['data']['progressionComplete']=True
  c['data']['reference']['subclassSelectionLevel']={'barbarian':3,'bard':3,'cleric':1,'druid':2,'fighter':3,'monk':3,'paladin':3,'ranger':3,'rogue':3,'sorcerer':1,'warlock':1,'wizard':2}[name.lower()]
  c['data']['reference']['abilityScoreImprovementLevels']=[row['level'] for row in levels if 'Ability Score Improvement' in row['featuresText']]
  for row in levels:
   row['featureUnlocks']=[]
   for label in row['featuresText'].split(', '):
    label=label.strip()
    if label in ['', '—']:continue
    stem=re.sub(r' \(.*','',label)
    matches=[f for f in features if index[f]['name'].split(': ',1)[1]==stem]
    row['featureUnlocks'].append(dict(label=label,references=matches,linkStatus='MATCHED_HEADING' if matches else 'TABLE_LABEL_OR_CONDITIONAL'))
 # General rules/gear: source headings determine boundaries, including chapter/table context.
 ranges=[(55,104),(194,206),(251,260),(360,365)]
 starts=[i for i,l in enumerate(ls) if any(a<=l['page']<=b for a,b in ranges) and l['heading'] and l['size']>=11.5]
 seen=set()
 for n,start in enumerate(starts):
  end=starts[n+1] if n+1<len(starts) else next(i for i,l in enumerate(ls) if l['page']==366)
  # Never bridge gaps (spell descriptions / magic items / monster blocks / appendices).
  rangeend=next(b for a,b in ranges if a<=ls[start]['page']<=b)
  end=min(end,next((i for i in range(start+1,len(ls)) if ls[i]['page']>rangeend),len(ls)))
  title=ls[start]['text'];body=source.body(start+1,end)
  if len(body)<25:continue
  existing=next((e for e in base['entries'] if e['name']==title and e['category'] in ['movement','rests','combat','advancement','feats']),None)
  category=existing['category'] if existing else 'rules'
  # Repeated headings receive their printed chapter context, not duplicate identical entries.
  if (category,slug(title)) in seen:title=title+' (page '+str(ls[start]['page'])+')'
  seen.add((category,slug(title)))
  entry(category,title,start,end,dict(automated=False,referenceOnly=True,sectionText=body),update=False)
 # Detailed gear descriptions are bold paragraph leads, kept alongside stable table IDs.
 for e in list(doc['entries']):
  if e['category'] not in ['equipment','weapons','armor','races','subraces','subclasses']:continue
  if e['category'] in ['races','subraces']:
   text=e['description'];traits=[]
   for m in re.finditer(r'([A-Z][A-Za-z ’-]+)\. ',text):
    if m[1] in ['Ability Score Increase','Age','Alignment','Size','Speed','Languages','Darkvision'] or any(k in m[1] for k in ['Training','Resistance','Resilience','Ancestry','Versatility','Cunning','Legacy','Lucky','Nimbleness','Brave']):traits.append(m[1])
   basebody=re.split(r'Hill Dwarf|High Elf|Lightfoot|Rock Gnome',text)[0] if e['category']=='races' else text
   reviewed={'Dwarf':{'con':2},'Elf':{'dex':2},'Halfling':{'dex':2},'Human':dict.fromkeys(['str','dex','con','int','wis','cha'],1),'Dragonborn':{'str':2,'cha':1},'Gnome':{'int':2},'Half-Elf':{'cha':2},'Half-Orc':{'str':2,'con':1},'Tiefling':{'int':1,'cha':2},'Hill Dwarf':{'wis':1},'High Elf':{'int':1},'Lightfoot':{'cha':1},'Rock Gnome':{'con':1}}
   abilities={'str':'Strength','dex':'Dexterity','con':'Constitution','int':'Intelligence','wis':'Wisdom','cha':'Charisma'}
   for ability,amount_ in reviewed[e['name']].items():
    assert e['name']=='Human' and 'each increase by 1' in basebody or re.search(abilities[ability]+r' score increases by '+str(amount_),basebody),(e['name'],'ability source mismatch')
   e['data']['abilityAdjustments']=reviewed[e['name']]
   e['data']['abilityChoice']=dict(count=2,amount=1,excludes=['cha']) if e['name']=='Half-Elf' else None
   e['data']['baseTraitsText']=basebody
   e['data']['traitHeadings']=list(dict.fromkeys(traits));e['data']['abilityAdjustmentsText']=' '.join(m[0] for m in re.finditer(r'Your [^.]*score[^.]*\.',text,re.I));e['data']['languagesText']=' '.join(m[0] for m in re.finditer(r'(?:You can speak|You can read)[^.]*\.',text));e['data']['automated']=False
  if e['category']=='subclasses':
   # Existing opening descriptions remain; complete feature descriptions live under their class.
   parent=e['data']['parent'];c=index[parent];features=c['data'].get('reference',{}).get('featureReferences',[])
   start=next(i for i,l in enumerate(ls) if l['text']==e['name'] and l['heading'] and 8<=l['page']<=55)
   chapterend=classstarts[classnames.index(parent.split(':')[-1].title())+1] if parent.split(':')[-1]!='wizard' else classend
   # Most subclasses end at chapter end; invocations/pact choices remain explicitly source text.
   e['data']['fullDescription']=source.body(start+1,chapterend);e['data']['featureReferences']=[f for f in features if any(v['id']==f and v['startLine']>start for v in inventory)];e['data']['featureLinkStatus']='SOURCE_SECTION_BOUNDED';e['data']['featureLevels']=[dict(feature=f,minimumLevel=index[f]['data'].get('minimumLevel'),status=index[f]['data'].get('levelStatus','LEGACY_VERIFIED')) for f in e['data']['featureReferences']]
  if e['category'] in ['equipment','weapons','armor']:
   shortname=re.sub(r' \([^)]*\)','',e['name']).replace('’',"'").lower()
   leads=[i for i,l in enumerate(ls) if 62<=l['page']<=74 and l['text'].replace('’',"'").lower().startswith(shortname+'.')]
   if leads:
    i=leads[0];end=next((j for j in range(i+1,len(ls)) if ls[j]['heading'] or ls[j]['font']=='Cambria-Bold' and re.match(r'^[A-Z][^.]+\. ',ls[j]['text'])),i+1)
    e['data']['descriptionText']=source.body(i,end);e['data']['descriptionPages']=source.pages(i,end)
   e['data']['automated']=False
 # Link only exact prerequisite mentions, retaining uncertainty rather than inferring grants.
 for feature in doc['entries']:
  if feature['category']!='features':continue
  data=feature['data'].get('reference',feature['data']);prerequisite=data.get('prerequisitesText','').lower()
  data['prerequisiteReferences']=[e['id'] for e in doc['entries'] if e['id']!=feature['id'] and (e['category']=='spells' or e['category']=='features' and e['data'].get('characterClass')==data.get('characterClass')) and prerequisite and (e['name'].split(': ',1)[-1].lower() in prerequisite)]
  data['dependencyStatus']='EXACT_TEXT_MENTIONS_ONLY' if prerequisite else 'NO_EXPLICIT_PREREQUISITE_LINE'
 # Additional printed mount/vehicle/service rows retain contextual labels and exact values.
 for page in range(71,75):
  rows={}
  for line in ls:
   if line['page']==page and line['font'].startswith('Calibri') and abs(line['size']-8.9)<.3:rows.setdefault((line['x']>=306,round(line['y'],1)),[]).append(line)
  context=''
  for (_,y),row in sorted(rows.items()):
   row.sort(key=lambda q:q['x']);texts=[r['text'] for r in row];title=texts[0]
   if len(texts)<2:
    if title in ['Saddle','Ale','Inn stay (per day)','Meals (per day)','Wine','Coach cab','Hireling']:context=title
    continue
   price=next((t for t in texts[1:] if re.search(r'\d[\d,]* (?:cp|sp|gp|pp)',t)),None)
   if price is None:continue
   if title in ['Item','Lifestyle','Service','Cost']:continue
   kind='mount' if any('ft.' in t for t in texts) else 'vehicle' if any('mph' in t for t in texts) else 'service-or-gear'
   prefix='Service: ' if page==74 and row[0]['x']>=306 else 'Lifestyle: ' if (page==72 and row[0]['x']>=306 and y>660) or page==73 and row[0]['x']<306 and y<125 else ''
   name=prefix+(context+': ' if context and title in ['Exotic','Military','Pack','Riding','Gallon','Mug','Squalid','Poor','Modest','Comfortable','Wealthy','Aristocratic','Common (pitcher)','Fine (bottle)','Between towns','Within a city','Skilled','Untrained'] else '')+title
   if 'srd51:equipment:'+slug(name) in index:continue
   start=ls.index(row[0]);e=entry('equipment',name,start,start+len(row),dict(cost=price,printedRow=' | '.join(texts),kind=kind,weight=next((t for t in texts[1:] if 'lb.' in t),''),speed=next((t for t in texts[1:] if 'ft.' in t or 'mph' in t),''),automated=False,referenceOnly=True));e['description']=' | '.join(texts)
 # Boxed equipment-pack paragraphs are not rows in the gear price table.
 for i,line in enumerate(ls):
  if 67<=line['page']<=70 and re.match(r"^[A-Z][A-Za-z’ -]+Pack \(\d+ gp\)\.",line['text']):
   m=re.match(r'(.+? Pack) \((\d+ gp)\)\.',line['text']);name=m[1]
   end=next((j for j in range(i+1,len(ls)) if ls[j]['heading'] or ls[j]['font'].endswith('Bold') and re.match(r'^[A-Z][^.]+\.',ls[j]['text'])),i+1)
   if 'srd51:equipment:'+slug(name) not in index:entry('equipment',name,i,end,dict(cost=m[2],contentsText=source.body(i,end),kind='equipment-pack',automated=False,referenceOnly=True))
 # Preserve existing source provenance and document blocked CC comparison accurately.
 doc['sources'][0]['changes']+=' J2.5C2 adds source-bounded descriptions, stat blocks and progression rows; structured annotations are reference only.'
 doc['sources'][0]['licenseVerification']='Official CC PDF returned HTTP 403 again in J2.5C2 (including escalated retry). Exact uploaded 403-page OGL-layout SRD 5.1 verified by SHA-256; PDF not relabeled. CC-edition text comparison remains pending.'
 # Evidence binds the static inventory to exact imported text/data; original entries get retained fingerprints too.
 coverage=dict(unresolvedRelationships=[dict(id=e['id'],reason='Feature level not unambiguously extracted; source text retained') for e in doc['entries'] if e['category']=='features' and e['data'].get('minimumLevel') is None],schema=1,sourceSha256=SHA,sourceEdition='2014 SRD 5.1 OGL-layout reference; CC edition comparison pending',baselineEntries=len(base['entries']),baselineRecords=[dict(id=e['id'],descriptionSha256=digest(e['description']),dataSha256=digest(json.dumps(e['data'],ensure_ascii=False,sort_keys=True,separators=(',',':')))) for e in base['entries'] if e['category'] in ['spells','monsters','features','conditions','rests','movement','combat','advancement','feats','spell-lists']],retainedIds=[e['id'] for e in base['entries']],candidates=inventory,ambiguities=ambiguities,categories={},records=[])
 for e in doc['entries']:
  coverage['records'].append(dict(id=e['id'],category=e['category'],name=e['name'],descriptionSha256=digest(e['description']),dataSha256=digest(json.dumps(e['data'],ensure_ascii=False,sort_keys=True,separators=(',',':')))))
 for cat in sorted({e['category'] for e in doc['entries']}):
  candidates=[v for v in inventory if v['category']==cat];imported=[e for e in doc['entries'] if e['category']==cat]
  coverage['categories'][cat]=dict(eligibleSourceEntries={'spells':319,'classes':12,'subclasses':12,'races':9,'subraces':4,'spell-lists':8,'weapons':37,'armor':13,'conditions':15,'feats':1}.get(cat,len(candidates) if cat in ['monsters','magic-items','features'] else None),imported=len(imported),sourceCandidates=len(candidates),ambiguous=sum(a['category']==cat for a in ambiguities),sourceInventoryStatus='BOUNDED_SOURCE_SCAN' if candidates else 'RETAINED_PREVIOUS_BATCH',structuralStatus='PARTIAL_METADATA',executable='Only pre-existing separate gameplay allowlists; catalog itself is reference-only')
 assert len(index)==len(doc['entries'])
 previousInventory=json.loads((RES/'source-inventory-v1.json').read_text());previousInventory['equipment']=[e['name'] for e in doc['entries'] if e['category']=='equipment'];write(RES/'source-inventory-v1.json',previousInventory)
 write(RES/'catalog-v1.json',doc);write(RES/'source-coverage-v1.json',coverage)
 print('Expanded catalog:',len(doc['entries']),'entries;',len(acs),'monster AC anchors;',len(itemstarts),'magic-item metadata anchors; ambiguities:',len(ambiguities))
 print(json.dumps(coverage['categories'],indent=2))
if __name__=='__main__':expand(sys.argv[1])
