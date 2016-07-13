"""
This script runs qdoc to generate a XML-formatted version of the documentation in input.

TODO: actual CLI parameters.
"""

import time
import logging

from qt5_lib import Qt5Worker

# Command-line configuration.
sources = "F:/QtDoc/QtDoc/QtSrc/qt-everywhere-opensource-src-5.4.2/"
output = "F:/QtDoc/output/html/"
version = [5, 4, 2]

qdoc = "D:/Qt/5.5/mingw492_32/bin/qdoc.exe"
saxon9 = "F:/QtDoc/QtDoc/SaxonHE9-7-0-3J/saxon9he.jar"
jing = "F:/QtDoc/QtDoc/jing-20140903-saxon95.jar"
launcher = "F:/QtDoc/QtDoc/QtDocTools/import/from_qdoc/launcher"  # Containing the .class file (Java-imposed hierarchy).

xslt2 = "F:/QtDoc/QtDoc/QtDocTools/import/from_qdoc/xslt/qdoc2db_5.4.xsl"
rng = "F:/QtDoc/QtDoc/QtDocTools/import/from_qdoc/schemas/docbook51/custom.rng"
postprocess = "F:/QtDoc/QtDoc/QtDocTools/import/from_qdoc/postprocessor/postprocessor.exe"

configsFile = output + "configs.json"

prepare = False
generate_html = False  # If prepare is not True when generate is, need an indexFolder.
generate_xml = True
generate_db = False  # Needs XML to be generated first.
validate_db = False

db_vocabulary = 'qtdoctools'  # Choose between: docbook and qtdoctools

logging.basicConfig(format='%(levelname)s at %(asctime)s: %(message)s', level=logging.DEBUG)

# Off-line configuration, should be altered only infrequently.
# Modules to ignore (outside Qt Base). They have no documentation for them, as of Qt 5.4.
ignored = ["qttranslations", "qtwayland", "qlalr"]
# Folders to ignore inside Qt Base sources (/Qt/5.4/Src/qtbase/src). This is mostly to avoid spending time on them.
qt_base_ignore = ["3rdparty", "android", "angle", "winmain", "openglextensions", "platformsupport", "plugins"]

ignored_files = {'qtdoc': ['classes.xml', 'obsoleteclasses.xml', 'hierarchy.xml', 'qmlbasictypes.xml', 'qmltypes.xml']}  # TODO? Specifically ignored files (generated elsewhere).


# Process:
# - retrieve the configuration files (*.qdocconf)
# - create the indexes by going through all source directories
# - start building things
if __name__ == '__main__':
    time_beginning = time.perf_counter()
    worker = Qt5Worker(folders={'sources': sources, 'output': output}, version=version,
                       binaries={'qdoc': qdoc, 'saxon9': saxon9, 'jing': jing,
                                 'postprocess': postprocess, 'launcher': launcher},
                       stylesheet=xslt2, schema=rng, vocabulary='qtdoctools',
                       ignores={'modules': ignored, 'qt_base': qt_base_ignore, 'files': ignored_files})
    time_configs = time.perf_counter()

    # Dependencies: first prepare all modules to get the indexes, then can start conversion in parallel. Once one module
    # is converted (or even started, as long as one file is out), the conversion to XML can start.
    if prepare:
        module_count = 1
        for module in worker.modules_list():
            logging.info('QDoc bootstrapping: starting to work with module %s (#%i out of %i)'
                         % (module, module_count, worker.n_modules()))
            worker.prepare_module(module_name=module)
            logging.info('QDoc bootstrapping: done with module %s (#%i out of %i)'
                         % (module, module_count, worker.n_modules()))
            module_count += 1
    time_prepare = time.perf_counter()

    if generate_html:
        module_count = 1
        for module in worker.modules_list():
            logging.info('Generating QDoc HTML: starting to work with module %s (#%i out of %i)'
                         % (module, module_count, worker.n_modules()))
            worker.generate_module(module_name=module)
            logging.info('Generating QDoc HTML: done with module %s (#%i out of %i)'
                         % (module, module_count, worker.n_modules()))
            module_count += 1
    time_generate = time.perf_counter()

    if generate_xml:
        module_count = 1
        for module in worker.modules_list():
            logging.info('Parsing as XML: starting to work with module %s (#%i out of %i)'
                         % (module, module_count, worker.n_modules()))
            count_xml = worker.generate_module_xml(module_name=module)
            logging.info('Parsing as XML: done with module %s (#%i out of %i); %i XML files generated'
                         % (module, module_count, worker.n_modules(), count_xml))
            module_count += 1
    time_xml = time.perf_counter()

    if generate_db:
        module_count = 1
        for module in worker.modules_list():
            logging.info('XML to DocBook: starting to work with module %s (#%i out of %i)'
                         % (module, module_count, worker.n_modules()))
            count_db = worker.generate_module_db(module_name=module)
            logging.info('XML to DocBook: done with module %s (#%i out of %i); %i DocBook files generated'
                         % (module, module_count, worker.n_modules(), count_db))
            module_count += 1
    time_db = time.perf_counter()

    if validate_db:
        module_count = 1
        for module in worker.modules_list():
            logging.info('DocBook validation: starting to work with module %s (#%i out of %i)'
                         % (module, module_count, worker.n_modules()))
            count_db = worker.validate_module_db(module_name=module)
            logging.info('DocBook validation: done with module %s (#%i out of %i); %i DocBook files validated'
                         % (module, module_count, worker.n_modules(), count_db))
            module_count += 1
    time_rng = time.perf_counter()

    # Finally: deploy, i.e. copy all generated files, change their extensions, copy the images.
    # TODO

    print("Total time: %f s" % (time_rng - time_beginning))
    print("Time to read configuration files: %f s" % (time_configs - time_beginning))
    print("Time to create indexes: %f s" % (time_prepare - time_configs))
    print("Time to generate HTML files: %f s" % (time_generate - time_prepare))
    print("Time to generate XML files: %f s" % (time_xml - time_generate))
    print("Time to generate DocBook files: %f s" % (time_db - time_xml))
    print("Time to validate DocBook files: %f s" % (time_rng - time_db))
