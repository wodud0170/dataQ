import time, base64
from selenium import webdriver
from selenium.webdriver.edge.options import Options

options = Options()
options.add_argument('--headless')
options.add_argument('--window-size=1200,900')

driver = webdriver.Edge(options=options)

html_path = 'c:/Users/naraedata/Desktop/dataQ/ndata-quality-master/dataQ설계/Narae_DataQ_사용자매뉴얼.html'
pdf_path = 'c:/Users/naraedata/Desktop/dataQ/ndata-quality-master/dataQ설계/Narae_DataQ_사용자매뉴얼.pdf'

driver.get('file:///' + html_path)
time.sleep(5)

result = driver.execute_cdp_cmd('Page.printToPDF', {
    'printBackground': True,
    'preferCSSPageSize': True,
    'marginTop': 0.5,
    'marginBottom': 0.5,
    'marginLeft': 0.5,
    'marginRight': 0.5,
    'paperWidth': 8.27,
    'paperHeight': 11.69,
})

pdf_data = base64.b64decode(result['data'])
with open(pdf_path, 'wb') as f:
    f.write(pdf_data)

print(f'PDF saved: {pdf_path}')
print(f'Size: {len(pdf_data)} bytes ({len(pdf_data)//1024} KB)')

driver.quit()
