__author__ = 'Thibaut Cuvelier'

import sys
import html5lib
import xml.etree.ElementTree as ET


tree = html5lib.parse(sys.stdin.read())
html = ET.tostring(tree)
print(html.decode('utf-8'))
