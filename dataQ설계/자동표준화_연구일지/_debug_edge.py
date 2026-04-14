import subprocess, tempfile, os, time
tmp = tempfile.mkdtemp(prefix='dbg_')
html = os.path.join(tmp, 't.html')
with open(html, 'w', encoding='utf-8') as f:
    f.write('<html><body>Test</body></html>')
EDGE = r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
url = 'file:///' + html.replace('\\', '/')

variants = [
    ['--headless'],
    ['--headless=new'],
    ['--headless=old'],
    ['--headless', '--no-sandbox'],
]
for i, extra in enumerate(variants):
    pdf = os.path.join(tmp, f'out{i}.pdf')
    profile = os.path.join(tmp, f'p{i}')
    cmd = [EDGE] + extra + ['--disable-gpu', '--no-pdf-header-footer',
                             f'--user-data-dir={profile}',
                             f'--print-to-pdf={pdf}', url]
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
    print(f'variant {i} {extra}: rc={r.returncode} exists={os.path.exists(pdf)}')
    if r.stderr:
        print('  stderr:', r.stderr[:500])
    time.sleep(1)
