#!/usr/bin/env python3
"""Reproduce the reviewed spell/condition batch from the supplied SRD 5.1 PDF.
Requires PyMuPDF for maintainer use only; Minecraft never parses PDFs or downloads rules.
Usage: python3 tools/build_srd51_reference.py /path/to/SRD-OGL_V5.1.pdf
The OGL license pages are deliberately excluded; distribution elects CC BY 4.0.
"""
import fitz,re,json,sys,hashlib
from pathlib import Path
source=Path(sys.argv[1]);pdf=fitz.open(source)
def clean(s):return re.sub(r'-{2,}','-',re.sub(r'\s+',' ',s.replace('\xad','').replace('‐','-').replace('‑','-'))).strip()
def slug(s):return re.sub('[^a-z0-9]+','-',s.lower()).strip('-')
lines=[]
for pi,page in enumerate(pdf):
 for col in [0,1]:
  group=[]
  for b in page.get_text('dict')['blocks']:
   for line in b.get('lines',[]):
    x,y,xx,yy=line['bbox']
    if y<60 or yy>735 or (x>=306)!=bool(col):continue
    text=clean(''.join(s['text'] for s in line['spans']))
    if text:group.append(dict(text=text,page=pi+1,heading=any(abs(s['size']-12)<.1 and s['font']=='GillSans-SemiBold' for s in line['spans']),y=y))
  lines+=sorted(group,key=lambda l:l['y'])
source_id='srd51-2014';entries=[]
def add(category,name,pages,description,data,related=[]):
 entries.append(dict(id='srd51:'+category+':'+slug(name),category=category,name=name,source=source_id,pages=sorted(set(pages)),verification='PDF_TEXT_VERIFIED',description=description,data=data,related=related))
# Class-list membership is independently extracted from the printed spell lists.
class_names=['Bard','Cleric','Druid','Paladin','Ranger','Sorcerer','Warlock','Wizard']
availability={};current=None;listed={}
for l in lines:
 if not 105<=l['page']<=113:continue
 t=l['text'];match=re.fullmatch('('+'|'.join(class_names)+') Spells',t)
 if match:current=match[1].lower();continue
 if current and not re.search(r'Level|Spell Lists',t):
  availability.setdefault(t.replace('’',"'"),set()).add(current);listed.setdefault(current,set()).add(t)
# Exact heading boundaries in the spell-description section, including text continued on p194.
spell_lines=[l for l in lines if 114<=l['page']<=194]
starts=[i for i,l in enumerate(spell_lines) if l['heading']]
for j,start in enumerate(starts):
 chunk=spell_lines[start:starts[j+1] if j+1<len(starts) else len(spell_lines)]
 name=chunk[0]['text'];text='\n'.join(l['text'] for l in chunk[1:]);school_line=chunk[1]['text'].lower()
 school=next((s for s in ['abjuration','conjuration','divination','enchantment','evocation','illusion','necromancy','transmutation'] if s in school_line),None)
 if school is None:raise ValueError(('school',name,school_line))
 level=0 if 'cantrip' in school_line else int(school_line[0]);ritual='(ritual)' in school_line
 m=re.search(r'Casting Time: (.*?)\nRange: (.*?)\nComponents?: (.*?)\nDuration: ([^\n]+)\n(.*)',text,re.S)
 if not m:raise ValueError(('spell fields',name,text[:450]))
 casting,range_,components,duration,description=m.groups();components=components.replace('\n',' ')
 # Duration occasionally wraps into the next line; continuation is part of metadata, not description.
 if duration.endswith(('up to','until','or','for')):raise ValueError(('duration wrap',name,duration))
 classes=sorted(availability.get(name.replace('’',"'"),set()))
 if not classes:raise ValueError(('missing class list',name))
 scaling=description[description.index('At Higher Levels.'):].replace('\n',' ') if 'At Higher Levels.' in description else ''
 # Cantrip scaling is retained verbatim in the description; also index that paragraph.
 if not scaling:
  scaling=' '.join(par for par in description.split('\n') if re.search(r'5th level|11th level|17th level',par))
 add('spells',name,[l['page'] for l in chunk],description.replace('\n',' '),dict(level=level,school=school,castingTime=casting,range=range_,components=components,duration=duration,concentration='Concentration' in duration,ritual=ritual,classes=classes,scaling=scaling),['srd51:classes:'+c for c in classes])
# Bounded class metadata batch. Full level progression remains explicitly uncovered.
class_pages={'barbarian':8,'bard':12,'cleric':16,'druid':20,'fighter':24,'monk':26,'paladin':30,'ranger':34,'rogue':38,'sorcerer':40,'warlock':45,'wizard':49}
for name,hd in zip(['Barbarian','Bard','Cleric','Druid','Fighter','Monk','Paladin','Ranger','Rogue','Sorcerer','Warlock','Wizard'],[12,8,8,8,10,8,10,10,8,6,8,6]):
 # Locate class chapter directly, rather than assuming page numbers from another edition.
 heading=next(l for l in lines if l['text']==name and 7<=l['page']<=54)
 add('classes',name,[heading['page']],f'{name} class reference. Hit Die: d{hd}. This batch indexes class identity and hit dice only; full progression and feature grants are not yet cataloged.',dict(hitDie=hd,progressionComplete=False),[])
# Conditions: all fifteen exact condition headings, no unrelated appendix content.
conditions=['Blinded','Charmed','Deafened','Exhaustion','Frightened','Grappled','Incapacitated','Invisible','Paralyzed','Petrified','Poisoned','Prone','Restrained','Stunned','Unconscious']
cl=[l for l in lines if 358<=l['page']<=359];indices=[(i,l['text']) for i,l in enumerate(cl) if l['text'] in conditions]
for j,(i,name) in enumerate(indices):
 chunk=cl[i+1:indices[j+1][0] if j+1<len(indices) else len(cl)]
 add('conditions',name,[cl[i]['page']]+[l['page'] for l in chunk],' '.join(l['text'] for l in chunk),dict(automated=False))
assert len(indices)==15
# Weapon and armor tables are parsed by full printed rows, not by inferred mechanics.
weapon_category='simple';weapon_mode='melee';weapon_names=[]
for raw in pdf[65].get_text(sort=True).splitlines():
 t=clean(raw);t=re.sub('-{2,}','-',t)
 if t=='Simple Ranged Weapons':weapon_mode='ranged'
 if t=='Martial Melee Weapons':weapon_category='martial';weapon_mode='melee'
 if t=='Martial Ranged Weapons':weapon_mode='ranged'
 m=re.fullmatch(r'(.+?) (\d[\d,]* (?:cp|sp|gp)) (.+?) (\d+(?:/\d+)? lb\.|—) (.*)',t)
 if not m:continue
 name,cost,damage,weight,properties=m.groups()
 dm=re.fullmatch(r'(\d+d\d+|1) (bludgeoning|piercing|slashing)',damage)
 if not dm and damage!='—':raise ValueError(('damage',name,damage))
 props=[prop for prop in ['ammunition','finesse','heavy','light','loading','reach','special','thrown','two-handed','versatile'] if prop in properties.lower()]
 ranged=re.search(r'range (\d+)/(\d+)',properties);versatile=re.search(r'versatile \((\dd\d+)\)',properties,re.I)
 add('weapons',name,[66],t,dict(category=weapon_category,mode=weapon_mode,cost=cost,weight=weight,damageDice=dm[1] if dm else 'none',damageType=dm[2] if dm else 'none',properties=props,propertyText=properties,rangeNormal=int(ranged[1]) if ranged else 0,rangeLong=int(ranged[2]) if ranged else 0,versatile=versatile[1] if versatile else ''))
 weapon_names.append(name)
assert len(weapon_names)==37,len(weapon_names)
armor_category='light';armor_names=[]
for pi in [62,63]:
 for raw in pdf[pi].get_text(sort=True).splitlines():
  t=clean(raw)
  if t=='Medium Armor':armor_category='medium'
  if t=='Heavy Armor':armor_category='heavy'
  if t=='Shield':armor_category='shield'
  m=re.fullmatch(r'(.+?) (\d[\d,]* gp) (.*?) (\d+) lb\.',t)
  if not m:continue
  name,cost,detail,weight=m.groups()
  if name not in ['Padded','Leather','Studded leather','Hide','Chain shirt','Scale mail','Breastplate','Half plate','Ring mail','Chain mail','Splint','Plate','Shield']:continue
  ac=int(re.search(r'\d+',detail)[0]);strength=re.search(r'Str (\d+)',detail)
  add('armor',name,[pi+1],t,dict(category=armor_category,cost=cost,weightLb=int(weight),ac=ac,acBonus=armor_category=='shield',dexterity='capped-2' if 'max 2' in detail else 'full' if 'Dex' in detail else 'none',strength=int(strength[1]) if strength else 0,stealthDisadvantage='Disadvantage' in detail,don={'light':'1 minute','medium':'5 minutes','heavy':'10 minutes','shield':'1 action'}[armor_category],doff={'light':'1 minute','medium':'1 minute','heavy':'5 minutes','shield':'1 action'}[armor_category]))
  armor_names.append(name)
assert len(armor_names)==13,len(armor_names)
# Three encounter-preparation stat blocks, exact source sections, with no executable adapter.
for name,end in [('Zombie','Ogre Zombie'),('Goblin','Golems'),('Skeleton','Minotaur Skeleton')]:
 start=next(i for i,l in enumerate(lines) if l['text']==name and 260<l['page']<358)
 stop=next(i for i in range(start+1,len(lines)) if lines[i]['text']==end)
 chunk=lines[start:stop];text='\n'.join(l['text'] for l in chunk[1:]);body=' '.join(l['text'] for l in chunk[1:])
 abilities=re.search(r'STR\s+DEX\s+CON\s+INT\s+WIS\s+CHA\s+(.*?)\s+(?:Saving Throws|Skills|Damage|Condition|Senses)',text,re.S)
 scores=[int(n) for n in re.findall(r'(\d+) \([+−-]\d+\)',abilities[1])];assert len(scores)==6,(name,scores)
 def field(label,following):
  m=re.search(re.escape(label)+r' (.*?)(?=\n(?:'+following+r')|$)',text,re.S);return clean(m[1]) if m else ''
 kind=text.splitlines()[0];ac=int(re.search(r'Armor Class (\d+)',text)[1]);hp=int(re.search(r'Hit Points (\d+)',text)[1]);challenge=re.search(r'Challenge ([\d/]+) \(([^)]+)\)',text)
 trait_start=text.index(challenge[0])+len(challenge[0]);action_start=text.index('\nActions\n');traits=clean(text[trait_start:action_start]);actions=clean(text[action_start+9:])
 add('monsters',name,[l['page'] for l in chunk],body,dict(size=kind.split()[0],type=kind.split(',')[0].split(' ',1)[1],alignment=kind.split(', ',1)[1],ac=ac,armor=field('Armor Class','Hit Points'),hp=hp,hitDice=re.search(r'Hit Points \d+ \(([^)]+)\)',text)[1],speed=field('Speed','STR'),abilities=dict(zip(['str','dex','con','int','wis','cha'],scores)),saves=field('Saving Throws','Skills|Damage|Condition|Senses'),skills=field('Skills','Damage|Condition|Senses'),senses=field('Senses','Languages'),languages=field('Languages','Challenge'),resistances=field('Damage Resistances','Damage Immunities|Condition|Senses'),vulnerabilities=field('Damage Vulnerabilities','Damage Immunities|Condition|Senses'),immunities=field('Damage Immunities','Condition|Senses'),conditionImmunities=field('Condition Immunities','Senses'),challenge=challenge[1],xp=challenge[2],traits=traits,actions=actions,spellcasting='None in this stat block'))
# Spell-list references reuse spell identities; no copies are embedded into saved characters.
for class_name in class_names:
 spells=[e for e in entries if e['category']=='spells' and class_name.lower() in e['data']['classes']]
 add('spell-lists',class_name+' Spell List',list(range(105,114)),f'Printed SRD 5.1 {class_name} spell-list membership; reference only.',dict(characterClass='srd51:classes:'+class_name.lower(),spells=[e['id'] for e in spells]),['srd51:classes:'+class_name.lower()]+[e['id'] for e in spells])

# Additional bounded reference batches. These remain descriptions, never automatic grants.
def section(name,minpage,maxpage,end=None):
 start=next(i for i,l in enumerate(lines) if l['text']==name and minpage<=l['page']<=maxpage)
 if end:stop=next(i for i in range(start+1,len(lines)) if lines[i]['text']==end)
 else:stop=next((i for i in range(start+1,len(lines)) if lines[i]['heading']),start+2)
 return lines[start:stop]
race_names=['Dwarf','Elf','Halfling','Human','Dragonborn','Gnome','Half-Elf','Half-Orc','Tiefling']
for ri,name in enumerate(race_names):
 chunk=section(name,3,7,race_names[ri+1] if ri+1<len(race_names) else 'Barbarian')
 body=' '.join(l['text'] for l in chunk[1:]);speed=re.search(r'base walking speed is (\d+) feet',body)
 add('races',name,[l['page'] for l in chunk],body,dict(size='Small' if name in ['Gnome','Halfling'] else 'Medium',speedFeet=int(speed[1]),traitsText=body,automated=False))
for name,parent,end in [('Hill Dwarf','Dwarf','Elf'),('High Elf','Elf','Halfling'),('Lightfoot','Halfling','Human'),('Rock Gnome','Gnome','Half-Elf')]:
 chunk=section(name,3,7,end);body=' '.join(l['text'] for l in chunk[1:]);add('subraces',name,[l['page'] for l in chunk],body,dict(parent='srd51:races:'+slug(parent),traitsText=body,automated=False),['srd51:races:'+slug(parent)])
subclasses=[('Path of the Berserker','barbarian',3),('College of Lore','bard',3),('Life Domain','cleric',1),('Circle of the Land','druid',2),('Champion','fighter',3),('Way of the Open Hand','monk',3),('Oath of Devotion','paladin',3),('Hunter','ranger',3),('Thief','rogue',3),('Draconic Bloodline','sorcerer',1),('The Fiend','warlock',1),('School of Evocation','wizard',2)]
for name,parent,level in subclasses:
 chunk=section(name,8,54);body=' '.join(l['text'] for l in chunk[1:]);add('subclasses',name,[l['page'] for l in chunk],body,dict(parent='srd51:classes:'+parent,minimumLevel=level,automated=False),['srd51:classes:'+parent])
# Reuse legacy class-feature identities/levels, but extract descriptions from the uploaded source.
legacy=json.loads(Path('src/main/resources/assets/tacticalcombat/ui/feature-reference-v1.json').read_text());omitted_features=[]
class_starts={name:next(i for i,l in enumerate(lines) if l['text']==name.title() and 8<=l['page']<=54) for name in legacy['classes']}
for class_name,c in legacy['classes'].items():
 start=class_starts[class_name];stop=min([n for n in class_starts.values() if n>start]+[next(i for i,l in enumerate(lines) if l['text']=='Beyond 1st Level' and l['page']==56)])
 for f in c['features']:
  found=next((i for i in range(start,stop) if lines[i]['text']==f['name']),None)
  if found is None:omitted_features.append(class_name+': '+f['name']);continue
  end=next((i for i in range(found+1,stop) if lines[i]['heading']),stop);chunk=lines[found:end];body=' '.join(l['text'] for l in chunk[1:])
  add('features',class_name.title()+': '+f['name'],[l['page'] for l in chunk],body,dict(characterClass='srd51:classes:'+class_name,minimumLevel=f['level'],legacyReferenceId=f['id'],automated=False),['srd51:classes:'+class_name])
for name,end in [('Short Rest','Long Rest'),('Long Rest','Between Adventures')]:
 chunk=section(name,87,87,end);add('rests',name,[l['page'] for l in chunk],' '.join(l['text'] for l in chunk[1:]),dict(automated=False))
for name,low,high,end,category in [('Movement',84,84,'Difficult Terrain','movement'),('Difficult Terrain',91,91,'Being Prone','movement'),('Actions in Combat',93,93,'Making an Attack','combat'),('Attack Rolls',94,94,None,'combat'),('Beyond 1st Level',56,56,'Equipment','advancement')]:
 chunk=section(name,low,high,end);add(category,name,[l['page'] for l in chunk],' '.join(l['text'] for l in chunk[1:]),dict(automated=False))
chunk=section('Grappler',75,75,'Using Ability');body=' '.join(l['text'] for l in chunk[1:]);add('feats','Grappler',[l['page'] for l in chunk],body,dict(prerequisiteAbility='str',minimumScore=13,automated=False))
for name,end in [('Bag of Holding','Bag of Tricks'),('Potion of Healing','Potion of Heroism'),('Wand of Magic Missiles','Wand of Paralysis')]:
 chunk=section(name,207,250,end);add('magic-items',name,[l['page'] for l in chunk],' '.join(l['text'] for l in chunk[1:]),dict(itemType=chunk[1]['text'],automated=False),['srd51:spells:magic-missile'] if name=='Wand of Magic Missiles' else [])

# Price/weight rows from the printed adventuring-gear and tools tables only.
equipment_names=[]
for pi in [69,70]:
 table=[l for l in lines if l['page']==pi]
 for i in range(1,len(table)-1):
  if re.fullmatch(r"[0-9,]+ (?:cp|sp|ep|gp|pp)",table[i]['text']) and re.fullmatch(r"(?:[0-9½¼/.,]+ lb\.|—)",table[i+1]['text']):
   name=table[i-1]['text'];cost=table[i]['text'];weight=table[i+1]['text']
   if name in equipment_names:raise ValueError(('duplicate equipment',name))
   add('equipment',name,[pi],name+': '+cost+'; '+weight+'. Reference price/weight only; special uses require their own verified mechanics.',dict(cost=cost,weight=weight,automated=False));equipment_names.append(name)

assert len({e['id'] for e in entries})==len(entries)
root=Path('src/main/resources/data/tacticalcombat/reference');root.mkdir(parents=True,exist_ok=True)
source_info=dict(id=source_id,title='System Reference Document 5.1 (2014)',license='CC-BY-4.0',licenseUrl='https://creativecommons.org/licenses/by/4.0/legalcode',officialUrl='https://www.dndbeyond.com/srd',ccEditionUrl='https://media.dndbeyond.com/compendium-images/srd/5.1/SRD_CC_v5.1.pdf',archiveUrl='https://commons.wikimedia.org/wiki/File:SRD5.1-CCBY4.0License.pdf',referenceSha256=hashlib.sha256(source.read_bytes()).hexdigest(),referenceEdition='User-provided OGL-layout SRD 5.1; license pages excluded',licenseVerification='User supplied official dual-license confirmation; live official/archive access returned HTTP 403. Independent CC-edition text comparison pending.',attribution='This work includes material taken from the System Reference Document 5.1 (SRD 5.1) by Wizards of the Coast LLC and available at https://www.dndbeyond.com/srd. The SRD 5.1 is licensed under the Creative Commons Attribution 4.0 International License available at https://creativecommons.org/licenses/by/4.0/legalcode.',changes='PDF typography and whitespace normalized; structured metadata and stable IDs added. Reference only; no endorsement or gameplay implementation implied.')
(root/'catalog-v1.json').write_text(json.dumps(dict(schema=1,sources=[source_info],entries=entries),ensure_ascii=False,indent=2)+'\n')
(root/'source-inventory-v1.json').write_text(json.dumps(dict(schema=1,spells=[spell_lines[i]['text'] for i in starts],conditions=conditions,weapons=weapon_names,armor=armor_names,equipment=equipment_names,omittedLegacyFeatures=omitted_features,classLists={c:sorted(names) for c,names in listed.items()}),ensure_ascii=False,indent=2)+'\n')
print('Imported',len(starts),'spells,',len(indices),'conditions, 12 class identities')
