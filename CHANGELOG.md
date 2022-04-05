QtDocTools release notes
========================

There are currently no compatibility guarantees between versions, especially 
for DOCX format. Incompatibilities should remaing minor, though.


Version 0.3.0 (2022)
----------------------------

Importing Qt documentation: 

* Improved test suite.

Exporting to DvpML: 

* Support for CALS tables.
* Support for xml:id in many places.
* Many fixes. Better error messages.
* Added a unit-test suite in XSpec.

Oxygen XML framework, based on its native DocBook 5 support:

* Access to the provided transformations from DocBook (not yet to DocBook).
* Template documents.


Version 0.2.1 (2 March 2020)
----------------------------

Round-tripping between DocBook and DOCX: 

* More comprehensive system to encode authors and contributions, allowing 
  contributions to be added in DOCX and retrieved in DocBook.
* Translated generated text (only French and Englih for now). 


Version 0.2.0 (29 February 2020)
--------------------------------

Round-tripping between DocBook and DOCX: 

* Implement ubiquitous linking.
* Implement figures in admonitions.

Importing Qt documentation: 

* Switch to qdoc 5.15 and its DocBook support. Far from completely done.

Exporting to DvpML: 

* More precise warning messages for unsupported tags.
* Implement figures.
* Keywords are taken from the keywordset of the document.
* License information is taken from the article configuration. 
* Implement list of references. 

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

* Import from Qt documentation (WebXML) into DocBook (works fairly well, many 
  corner cases remain). 
* Round-tripping between DocBook and application-agnostic DOCX (works fairly well). 
* Export to DvpML from DocBook (works fairly well). 
