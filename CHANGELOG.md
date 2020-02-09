QtDocTools release notes
========================

Version 0.2
-----------

Round-tripping between DocBook and DOCX: 

* Implement ubiquitous linking.
* Implement figures in admonitions.

Importing Qt documentation: 

* Switch to qdoc 5.15 and its DocBook support.

Exporting to DvpML: 

* More precise warning messages for unsupported tags.
* Implement figures.

Many code-quality improvements.


Version 0.1.1 (27 June 2019)
----------------------------

Round-tripping between DocBook and DOCX: 

* Implement footnotes. 
* Deal with document properties. 

Importing Qt documentation: 

* Update for Qt 5.13.


Version 0.1.0 (21 June 2019)
----------------------------

First public release, with several modules: 

* Import from Qt documentation (WebXML) into DocBook (works fairly well, many corner cases remain). 
* Round-tripping between DocBook and application-agnostic DOCX (works fairly well). 
* Export to DvpML from DocBook (works fairly well). 