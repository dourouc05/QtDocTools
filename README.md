## A journey from QDoc 5 to DocBook ##

The goal of this step is to take Qt's sources and to turn them into DocBook documents. 
Qt's internal documentation system is QDoc, whose output formats are HTML and WebXML
([it used to have DITA](http://lists.qt-project.org/pipermail/development/2013-June/011311.html)). 
Overall, the process is to run QDoc to get HTML files, then to turn them into DocBook
with some XML-to-XML transformation. 

The end goal is to produce the best DocBook output as possible, to encode a lot of information, 
so that the transformation does not need to be repeated often to add new tags to 
have a better representation of the content. 

In more details, the following steps are required: 

- Apply QDoc on Qt's sources (`main_script/qt5.py`): 
    - Each module has its own QDocConf file, that gives sufficient information to QDoc 
      for its operations; a directory may contain multiple QDocConf files
    - For each module, QDoc must first build an index so that links between modules are 
      possible
    - Finally, QDoc can build the HTML documentation with all these files
- Transform the HTML5 files into XML content (`main_script/qt5.py`)
- Perform an XSL transformation from this XML into DocBook (`import/from_qdoc/xslt/qdoc2db_5.4.xsl`)
- Use a C++ parser to deal with the function prototypes and make the DocBook content
  fully exploitable (`import/from_qdoc/postprocessor/main.cpp`). Before the script, 
  only the C++ prototype is available in DocBook (with things like `int main()`); 
  it parses the prototypes to generate the corresponding DocBook tags and separate the 
  return type from the function name and from its arguments. 
   
The first two steps are performed within the main Python script `main_script/qt5.py`, 
which also automates all the other steps. 
The QDoc automation is also available as a stand-alone script in 
`import/from_qdoc/qdoc2html`. The HMLT5-to-XML transformation is also available as a 
stand-alone script in `import/from_qdoc/html2xml`. The XSL transformations is performed 
with the stylesheets in `import/from_qdoc/xslt`, whilst the C++ parsing is done with 
the executable in `import/from_qdoc/postprocessor`.

Notes
=====

Very useful tool to debug OOXML documents: the tools from 
[the official SDK, v2.5](https://www.microsoft.com/en-us/download/details.aspx?id=30425)
or [the up-to-date SDK](https://github.com/OfficeDev/Open-XML-SDK) 
(validation only in C#, no desktop application)