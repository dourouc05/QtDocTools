## How is the code organised?

All classes belong to a package within `be.tcuvelier.qdoctools`, except the main class 
(the one that runs the CLI).

The package `core` contains the core functionality (i.e. something that does not belong
to `consistency` or to `io`). Within it: 
  * the package `handlers` implement most of the functionalities, with a fine-grained level
  * the classes `*Core` implement a higher-level interface on top of the `handlers` package
  * the package `helpers` contains very small methods that make the rest of the code easier to read
  * the package `utils` contains all sorts of methods and classes that may be of use anywhere

The package `cli` contains the glue code between Picocli and the `core` package. It should
not depend on other packages. 

The package `io` deals with input and output with the DOCX format (and, at some point, ODT). 
It should not have any dependency with QtDocTools. 

The package `consistency` implements all consistency checks that are performed on qdoc's output.
It should not have very limited dependency towards QtDocTools (mostly its `utils`). 