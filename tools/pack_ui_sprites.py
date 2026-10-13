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
