from pathlib import Path

p = Path('android/app/src/main/java/dev/besan/browserbrake/BrowserBlockService.java')
s = p.read_text()
s = s.replace('import android.widget.Toast;\n', '')
s = s.replace('            Toast.makeText(this, rule.getName() + " の解除条件を達成しました", Toast.LENGTH_SHORT).show();\n', '')
p.write_text(s)
