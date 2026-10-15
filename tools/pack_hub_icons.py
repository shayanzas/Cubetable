"""Lossless cell isolation of the original H 2x3 navigation artwork. No drawing or resizing."""
from pathlib import Path
from PIL import Image
import json
base=Path(__file__).resolve().parents[1]/'src/main/resources/assets/tacticalcombat/textures/gui/hub'
source=Image.open(base/'navigation-h.png').convert('RGBA')
assert source.size==(1024,1536)
meta={'schema':1,'source':'navigation-h.png','sprites':{}}
for i,name in enumerate(['campaigns','characters','encounters','dm','tabletop','settings']):
 x=i%2*512;y=i//2*512;part=source.crop((x,y,x+512,y+512));out=Image.new('RGBA',(516,516));out.paste(part,(2,2));out.save(base/(name+'.png'));(base/(name+'.png.mcmeta')).write_text('{"texture":{"blur":false,"clamp":true}}\n');meta['sprites'][name]={'width':516,'height':516,'sourceBounds':[x,y,512,512]}
(base/'sprites.json').write_text(json.dumps(meta,indent=2)+'\n')
