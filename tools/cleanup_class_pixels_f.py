"""Authorized lossless F cleanup: only detached <=8px dark alpha components.
No resizing, color changes, outline tracing or artwork generation. Ranger is excluded.
Run this script, then tools/pack_ui_sprites.py. Requires Pillow/NumPy/SciPy.
"""
from pathlib import Path
import json
import numpy as np
from PIL import Image
from scipy import ndimage
BASE=Path(__file__).resolve().parents[1]/'src/main/resources/assets/tacticalcombat/textures/gui/creator'
GROUPS=[['barbarian','bard','cleric','monk'],['druid','fighter','rogue','warlock'],['ranger','paladin','sorcerer','wizard']]
audit={'method':'8-connected alpha components <=8px; max RGB <=48; every pixel >=4px from colored artwork; Ranger excluded; other RGBA pixels exact','icons':{}}
for group,names in enumerate(GROUPS,1):
 original=np.asarray(Image.open(BASE/f'class-cleanup-e-{group}.png').convert('RGBA'));rgba=original.copy();visible=rgba[:,:,3]>0
 labels,n=ndimage.label(visible,np.ones((3,3)));counts=np.bincount(labels.ravel())
 principal_labels,_=ndimage.label(rgba[:,:,3]>128,np.ones((3,3)));pc=np.bincount(principal_labels.ravel());principals=np.argsort(pc[1:])[-4:]+1;seeds=np.zeros(visible.shape,np.int32);names_by_label={}
 for label in principals:
  yy,xx=np.where(principal_labels==label);index=int(xx.mean()>=rgba.shape[1]/2)+2*int(yy.mean()>=rgba.shape[0]/2);names_by_label[label]=names[index];seeds[principal_labels==label]=label
 nearest=ndimage.distance_transform_edt(seeds==0,return_distances=False,return_indices=True);assigned=seeds[tuple(nearest)]
 colored=visible&(rgba[:,:,:3].max(axis=2)>48);distance=ndimage.distance_transform_edt(~colored);removed=np.zeros(visible.shape,bool)
 for name in names:audit['icons'][name]={'componentsRemoved':0,'pixelsRemoved':0}
 for label in np.flatnonzero((counts>0)&(counts<=8)):
  if label==0:continue
  mask=labels==label
  if rgba[:,:,:3][mask].max()>48 or distance[mask].min()<4:continue
  owners=np.unique(assigned[mask])
  if len(owners)!=1:continue
  name=names_by_label[owners[0]]
  if name=='ranger':continue
  removed|=mask;audit['icons'][name]['componentsRemoved']+=1;audit['icons'][name]['pixelsRemoved']+=int(mask.sum())
 rgba[removed]=0
 assert np.array_equal(rgba[~removed],original[~removed]),'All retained RGBA pixels must be exact'
 assert not np.any(removed&(original[:,:,3]==0))
 Image.fromarray(rgba).save(BASE/f'class-cleanup-f-{group}.png')
(BASE/'class-cleanup-f-audit.json').write_text(json.dumps(audit,indent=2)+'\n')
print(json.dumps(audit,indent=2))
