"""Lossless packaging of EXISTING approved RGBA sprites; no drawing/resampling.
Requires Pillow, NumPy, SciPy only when regenerating assets (not at mod runtime).
Disconnected decorative pixels follow the nearest connected principal motif.
"""
from pathlib import Path
import json
import numpy as np
from PIL import Image
from scipy import ndimage
ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'src/main/resources/assets/tacticalcombat/textures/gui/creator'
OUT = BASE / 'sprites'
OUT.mkdir(exist_ok=True)
NAMES = [
 ['barbarian','bard','cleric','druid','fighter','monk','ranger','rogue','warlock','wizard'],
 ['str','dex','con','int','wis','cha','skills','saves','expertise','weapons','armor','tools'],
 ['languages','equipment','features','feats','spells','cantrips','action','bonus','reaction','hp','proficiency','inspect'],
 ['custom','acolyte','charlatan','criminal','entertainer','folk_hero','guild_artisan','noble'],
 ['outlander','sage','soldier','urchin']]
image = np.asarray(Image.open(BASE/'icons.png').convert('RGBA'))
labels, _ = ndimage.label(image[:,:,3] > 128)
counts = np.bincount(labels.ravel())
principal = []
for label in np.flatnonzero(counts >= 1500):
 if not label: continue
 yy,xx = np.where(labels == label)
 principal.append((label, float(yy.mean()), float(xx.mean())))
assert len(principal) == 46, 'Artwork changed; inspect principal motifs before repacking'
rows = [[] for _ in NAMES]
for item in principal:
 row = sum(item[1] > boundary for boundary in [235,430,630,850])
 rows[row].append(item)
main = np.zeros(labels.shape, dtype=np.int32)
for label,_,_ in principal: main[labels==label] = label
nearest = ndimage.distance_transform_edt(main==0, return_distances=False, return_indices=True)
assignment = main[tuple(nearest)]
manifest = {'schema':1, 'method':'original RGBA pixels; alpha-component bounds; 2px transparent gutter', 'sprites':{}}
retained = 0
for names,components in zip(NAMES,rows):
 components.sort(key=lambda item:item[2])
 assert len(names)==len(components)
 for name,(label,_,_) in zip(names,components):
  mask = (assignment==label) & (image[:,:,3]>0)
  yy,xx=np.where(mask);x0,y0,x1,y1=int(xx.min()),int(yy.min()),int(xx.max()+1),int(yy.max()+1)
  crop = image[y0:y1,x0:x1].copy()
  crop[~mask[y0:y1,x0:x1]] = 0
  output = np.zeros((crop.shape[0]+4,crop.shape[1]+4,4),dtype=np.uint8)
  output[2:-2,2:-2] = crop
  assert np.array_equal(crop[mask[y0:y1,x0:x1]],image[y0:y1,x0:x1][mask[y0:y1,x0:x1]])
  Image.fromarray(output).save(OUT/(name+'.png'))
  pixels=int(mask.sum());retained+=pixels
  manifest['sprites'][name]={'width':output.shape[1],'height':output.shape[0],'pixels':pixels,'source':'icons.png','sourceBounds':[x0,y0,x1-x0,y1-y0]}
assert retained==int((image[:,:,3]>0).sum()), 'Every visible source pixel must survive exactly once'
extra=np.asarray(Image.open(BASE/'class-extra.png').convert('RGBA'))
for index,name in enumerate(['paladin','sorcerer']):
 part=extra[:,index*887:(index+1)*887];yy,xx=np.where(part[:,:,3]>0)
 x0,y0,x1,y1=int(xx.min()),int(yy.min()),int(xx.max()+1),int(yy.max()+1)
 crop=part[y0:y1,x0:x1];output=np.zeros((crop.shape[0]+4,crop.shape[1]+4,4),dtype=np.uint8);output[2:-2,2:-2]=crop
 Image.fromarray(output).save(OUT/(name+'.png'))
 manifest['sprites'][name]={'width':output.shape[1],'height':output.shape[0],'pixels':int((part[:,:,3]>0).sum()),'source':'class-extra.png','sourceBounds':[x0+index*887,y0,x1-x0,y1-y0]}
for name in manifest['sprites']:(OUT/(name+'.png.mcmeta')).write_text('{"texture":{"blur":false,"clamp":true}}\n')
(BASE/'sprites.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(f'Packed {len(manifest["sprites"])} unchanged sprite motifs; retained {retained} atlas pixels.')

# Checkpoint C: approved originals remain the source of 43 sprites. Four cleaned
# motifs and a rebuilt Ranger have explicit new source images, never race art.
correction_file = BASE / 'class-corrections-c.png'
if correction_file.exists():
 corrected = np.asarray(Image.open(correction_file).convert('RGBA'))
 def package_correction(name, source, rgba, mask):
  yy,xx=np.where(mask);x0,y0,x1,y1=int(xx.min()),int(yy.min()),int(xx.max()+1),int(yy.max()+1)
  crop=rgba[y0:y1,x0:x1].copy();crop[~mask[y0:y1,x0:x1]]=0
  output=np.zeros((crop.shape[0]+4,crop.shape[1]+4,4),dtype=np.uint8);output[2:-2,2:-2]=crop
  Image.fromarray(output).save(OUT/(name+'.png'))
  manifest['sprites'][name]={'width':output.shape[1],'height':output.shape[0],'pixels':int(mask.sum()),'source':source,'sourceBounds':[x0,y0,x1-x0,y1-y0]}
 assert corrected.shape[0] == corrected.shape[1] and corrected.shape[0] % 2 == 0
 half=corrected.shape[0]//2
 for index,name in enumerate(['barbarian','bard','cleric','monk']):
  mask=np.zeros(corrected.shape[:2],dtype=bool);x=index%2*half;y=index//2*half
  mask[y:y+half,x:x+half]=corrected[y:y+half,x:x+half,3]>0
  package_correction(name,correction_file.name,corrected,mask)
 ranger=np.asarray(Image.open(BASE/'ranger-c.png').convert('RGBA'))
 package_correction('ranger','ranger-c.png',ranger,ranger[:,:,3]>0)
 manifest['method']='43 original motifs; four cleaned class motifs and rebuilt Ranger; lossless RGBA isolation; 2px transparent gutter'
 (BASE/'sprites.json').write_text(json.dumps(manifest,indent=2)+'\n')
 print('Packaged five Checkpoint C class corrections; 43 original sprites retained.')

# Checkpoint D: repaired sources override only the six reported defective motifs.
# Bard, Monk, Paladin, Sorcerer and the existing race presentation are untouched.
if (BASE / 'class-corrections-d.png').exists():
 corrected = np.asarray(Image.open(BASE/'class-corrections-d.png').convert('RGBA'))
 assert corrected.shape[0] == corrected.shape[1] and corrected.shape[0] % 2 == 0
 half=corrected.shape[0]//2
 for index,name in enumerate(['druid','fighter','rogue','warlock']):
  mask=np.zeros(corrected.shape[:2],dtype=bool);x=index%2*half;y=index//2*half
  mask[y:y+half,x:x+half]=corrected[y:y+half,x:x+half,3]>0
  package_correction(name,'class-corrections-d.png',corrected,mask)
 wizard=np.asarray(Image.open(BASE/'wizard-source-d.png').convert('RGBA'))
 mask=np.zeros(wizard.shape[:2],dtype=bool)
 mask[:,:wizard.shape[1]//2]=wizard[:,:wizard.shape[1]//2,3]>0
 package_correction('wizard','wizard-source-d.png',wizard,mask)
 ranger=np.asarray(Image.open(BASE/'ranger-d.png').convert('RGBA'))
 package_correction('ranger','ranger-d.png',ranger,ranger[:,:,3]>0)
 manifest['method']='38 original motifs; four retained Checkpoint C repairs; six Checkpoint D class repairs; lossless RGBA isolation; 2px transparent gutter'
 (BASE/'sprites.json').write_text(json.dumps(manifest,indent=2)+'\n')
 print('Packaged six Checkpoint D class repairs; retained the four good C repairs and 38 originals.')

# E alpha cleanup sources preserve the approved motifs. Isolate by connected
# principal motif rather than cutting at nominal cells: Ranger's lower tip and
# Wizard's book extend over their grid midpoint, and must never be cropped off.
e_sources=[('class-cleanup-e-1.png',['barbarian','bard','cleric','monk']),
           ('class-cleanup-e-2.png',['druid','fighter','rogue','warlock']),
           ('class-cleanup-e-3.png',['ranger','paladin','sorcerer','wizard'])]
if all((BASE/source).exists() for source,_ in e_sources):
 for source,names in e_sources:
  rgba=np.asarray(Image.open(BASE/source).convert('RGBA'))
  labels,_=ndimage.label(rgba[:,:,3]>128,np.ones((3,3)))
  counts=np.bincount(labels.ravel());principal=np.argsort(counts[1:])[-4:]+1
  main=np.zeros(labels.shape,dtype=np.int32);motifs={}
  for label in principal:
   yy,xx=np.where(labels==label);index=int(xx.mean()>=rgba.shape[1]/2)+2*int(yy.mean()>=rgba.shape[0]/2)
   assert index not in motifs,'Inspect connected principal motifs before packing'
   motifs[index]=int(label);main[labels==label]=label
  assert len(motifs)==4
  nearest=ndimage.distance_transform_edt(main==0,return_distances=False,return_indices=True)
  assignment=main[tuple(nearest)];retained=0
  for index,name in enumerate(names):
   mask=(assignment==motifs[index])&(rgba[:,:,3]>0)
   package_correction(name,source,rgba,mask);retained+=int(mask.sum())
  assert retained==int((rgba[:,:,3]>0).sum()),'Every cleaned source pixel belongs to one motif'
 manifest['method']='36 unchanged non-class sprites; 12 approved class motifs with E alpha cleanup; connected-motif isolation without resampling; 2px transparent gutter'
 (BASE/'sprites.json').write_text(json.dumps(manifest,indent=2)+'\n')
 print('Packaged twelve E class cleanup motifs; 36 non-class sprites retained.')
 # Ranger's approved D source has fewer isolated dark fragments than the trial
 # E edit. Retain it exactly; do not trade away approved bow geometry/quality.
 ranger=np.asarray(Image.open(BASE/'ranger-d.png').convert('RGBA'))
 package_correction('ranger','ranger-d.png',ranger,ranger[:,:,3]>0)
 manifest['method']='36 unchanged non-class sprites; 11 class source cleanups; approved D Ranger retained exactly; whole connected-motif isolation; 2px transparent gutter'
 (BASE/'sprites.json').write_text(json.dumps(manifest,indent=2)+'\n')
 print('Retained approved D Ranger exactly; rejected the noisier trial cleanup.')
