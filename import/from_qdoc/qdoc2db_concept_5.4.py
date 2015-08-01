__author__ = 'Thibaut Cuvelier'

import html5lib
import xml.etree.ElementTree as ET

input_directory = 'C:/Qt/_script/html'
input_extension = '.html'
output_directory = 'C:/Qt/_script/db'
output_extension = '.xml'
path = ['qtdoc', 'accessibility']


def convert(in_file_name, out_file_name):
    # Read the input file.
    with open(in_file_name, "rb") as f:
        tree = html5lib.parse(f)

    with open(out_file_name, 'wb') as f:
        f.write(ET.tostring(tree))

    # # Find the relevant section in it.
    # ns = {'html': '{http://www.w3.org/1999/xhtml}'}
    # tree = tree.findall('.//{http://www.w3.org/1999/xhtml}div[@class="content mainContent"]')
    #
    # # Build the output document.
    # out = ET.Element('{http://docbook.org/ns/docbook}article')
    #
    # print(tree)


convert(input_directory + '/' + '/'.join(path) + input_extension, output_directory + '/' + '/'.join(path) + output_extension)
