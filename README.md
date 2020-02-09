## A journey from QDoc 5 to DocBook

This tool works around the DocBook 5.2 format. It was first built to allow a 
more modern toolkit to work with Qt qdoc's generated output, and grew to offer
many DocBook-related functionalities. Its capabilities: 

- Convert DocBook documents into DOCX and back. 
- Convert DocBook documents into ODT and back {WIP}. 
- Convert DocBook documents into DvpML (proprietary XML vocabulary used only
  by Developpez.com) and back (although the possibilities are more limited
  than with DOCX). 
- Run Developpez.com's proprietary tools to convert a document into HTML and 
  upload it {WIP}.
- Merge two versions of a document (either because it was proofread, and some
  metadata was lost, or because a new version of Qt was released and the 
  existing documentation translation should be updated) {WIP}.
- Run qdoc to generate Qt's documentation (requires qdoc 5.15, as it is the 
  first version able to generate DocBook) {WIP}. 
  
### Repository organisation

Filters for most formats reside in the `import` and `export` folders, each 
within its own folder. These contain the XSLT stylesheets and possibly a few
test cases. For transformations that do not rely on XSLT (like DOCX, using 
Apache POI), there are only tests. 

The main code is in `main_script`. 

The folder `merge` contains stuff about merging two documents. Its contents 
is in bad shape, right now. 


### Notes

Very useful tool to debug OOXML documents: the tools from 
[the official SDK, v2.5](https://www.microsoft.com/en-us/download/details.aspx?id=30425)
or [the up-to-date SDK](https://github.com/OfficeDev/Open-XML-SDK) 
(validation only in C#, no desktop application)

CLI to get HTML from the wiki itself: https://www.dokuwiki.org/tips:dokuwiki_parser_cli. 
XML-RPC from a working wiki: https://www.dokuwiki.org/devel:xmlrpc. 
Pandoc supports DokuWiki dialect: https://pandoc.org/ (since 2.6)