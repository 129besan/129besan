from pathlib import Path

p = Path('lib/catalog_pages.dart')
s = p.read_text()
old = "separatorBuilder: (_, __) => const SizedBox(height: 6),"
new = "separatorBuilder: (context, index) => const SizedBox(height: 6),"
if old not in s:
    raise SystemExit('separatorBuilder pattern missing')
p.write_text(s.replace(old, new, 1))
