#!/usr/bin/env python3
"""Re-extract from the exact PDF in isolation and compare all three packaged source artifacts."""
import pathlib,tempfile,subprocess,shutil,sys
root=pathlib.Path(__file__).resolve().parents[1]
source=pathlib.Path(sys.argv[1]).resolve()
with tempfile.TemporaryDirectory(prefix='cubetable-source-verification-') as directory:
 target=pathlib.Path(directory)
 for relative in ['tools/build_srd51_reference.py','tools/expand_srd51_reference.py','src/main/resources/assets/tacticalcombat/ui/feature-reference-v1.json']:
  dst=target/relative;dst.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(root/relative,dst)
 for script in ['build_srd51_reference.py','expand_srd51_reference.py']:
  subprocess.run([sys.executable,str(target/'tools'/script),str(source)],cwd=target,check=True,stdout=subprocess.DEVNULL)
 for name in ['catalog-v1.json','source-inventory-v1.json','source-coverage-v1.json']:
  relative=pathlib.Path('src/main/resources/data/tacticalcombat/reference')/name
  if (root/relative).read_bytes()!=(target/relative).read_bytes():raise SystemExit('Source reconstruction differs: '+name)
 print('Exact SRD source reconstruction passed: catalog, inventory and coverage manifest are byte-identical.')
