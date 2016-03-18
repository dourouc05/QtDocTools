## A journey from QDoc 5 to DocBook ##

The goal of this step is to take Qt's sources and to turn them into DocBook documents. 
Qt's internal documentation system is QDoc, whose sole output format is HTML. 
Overall, the process is to run QDoc to get HTML files, then to turn them into DocBook
with some XML-to-XML transformation. 

In more details, the following steps are required: 

- Apply QDoc on Qt's sources: 
    - Each module has its own QDocConf file, that gives sufficient information to QDoc 
      for its operations; a directory may contain multiple QDocConf files
    - For each module, QDoc must first build an index so that links between modules are 
      possible
    - Finally, QDoc can build the HTML documentation with all these files
- Transform the HTML5 files into XML content
- Perform an XSL transformation from this XML into DocBook
- Use a C++ parser to deal with the function prototypes and make the DocBook content
  fully exploitable
   
The first two steps are performed within the main Python script `main_script/qt5.py`. 
The QDoc automation is also available as a stand-alone script in 
`import/from_qdoc/qdoc2html`. The HMLT5-to-XML transformation is also available as a 
stand-alone script in `import/from_qdoc/html2xml`. The XSL transformations is performed 
with the stylesheets in `import/from_qdoc/xslt`, whilst the C++ parsing is done with 
the executable in `import/from_qdoc/postprocessor`.