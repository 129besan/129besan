from pathlib import Path

p = Path('lib/main.dart')
s = p.read_text()
old = """                  if ([b('challengeWait'), b('challengeWalk')]\n                          .where((e) => e)\n                          .length >=\n                      2)\n"""
new = """                  if ([b('challengeWait'), b('challengeWalk'), b('challengePhoneBreak')]\n                          .where((e) => e)\n                          .length >=\n                      2)\n"""
if old not in s:
    raise SystemExit('missing legacy challenge combination condition')
s = s.replace(old, new, 1)
p.write_text(s)
