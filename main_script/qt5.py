import os
import os.path
import time
import subprocess
import json
import logging

"""
This script runs qdoc to generate a XML-formatted version of the documentation in input.

TODO: actual CLI parameters.
"""

import xml.etree.ElementTree as ETree
import subprocess

try:
    import html5lib
    no_html5 = False
except ImportError:
    no_html5 = True
    html5lib = None
    ETree = None

# Command-line configuration.
sources = "F:/QtDoc/QtDoc/QtSrc/qt-everywhere-opensource-src-5.4.2/"
output = "F:/QtDoc/output/html/"
indexFolder = output
version = [5, 4, 2]

qdoc = "D:/Qt/5.5/mingw492_32/bin/qdoc.exe"
saxon9 = "F:/QtDoc/QtDoc/SaxonHE9-7-0-3J/saxon9he.jar"
xslt2 = "F:/QtDoc/QtDoc/QtDocTools/import/from_qdoc/xslt/qdoc2db_5.4.xsl"

configsFile = output + "configs.json"
outputConfigs = True  # Read the file if it exists (and skip this phase), write it otherwise.

prepare = False
generate = False  # If prepare is not True when generate is, need an indexFolder.
generate_xml = False and not no_html5  # Could be started without generation!
generate_db = True  # Needs XML to be generated first.

logging.basicConfig(format='%(levelname)s at %(asctime)s: %(message)s', level=logging.DEBUG)

# Off-line configuration, should be altered only infrequently.
# Modules to ignore (outside Qt Base). They have no documentation for them.
ignored = ["qttranslations", "qtwayland"]
# Folders to ignore inside Qt Base sources (/Qt/5.3/Src/qtbase/src). This is mostly to avoid spending time on them.
qt_base_ignore = ["3rdparty", "android", "angle", "winmain", "openglextensions", "platformsupport", "plugins"]

# Make the environment variables needed by QDoc.
versionString = [str(x) for x in version]
environment = {"QT_INSTALL_DOCS": sources + "qtbase/doc",
               "QT_VERSION_TAG": ''.join(versionString),  # E.g.: 531
               "QT_VER": '.'.join(versionString[:2]),  # E.g.: 5.3
               "QT_VERSION": '.'.join(versionString)}  # E.g.: 5.3.1


# Retrieve the list of modules in the sources: get the list of files, ensure it is a directory, and a module (first
# letter is q). Handle ignore list.
def get_folders():
    return [folder for folder in os.listdir(sources)
            if os.path.isdir(sources + folder) and folder.startswith('q') and folder not in ignored]


# In order to create the indexes, retrieve the list of configuration files.
def get_configuration_files(configs_output=False, configs_file=None):
    # Generate potential places to find the configuration file for the given module.
    def get_potential_file_names(doc_path, module):
        # qtwebkit-examples is the name of the folder, but the file is named qtwebkitexamples.qdoc!
        return [doc_path + module + ".qdocconf", doc_path + "config/" + module + ".qdocconf",
                doc_path + module.replace("-", "") + ".qdocconf",
                doc_path + "config/" + module.replace("-", "") + ".qdocconf"]

    # Find a configuration file within the given source folder; returns true if one is found, which is put inside the
    # given map. This function is made for modules outside Qt Base.
    def find_configuration_file(src_path, configs):
        # This loop is quite slow, but there is too much variety in those modules (such as a folder containing
        # multiple modules: the loop cannot be stopped at the first file!).
        # One more strange thing with Qt Multimedia: a qtmultimedia-dita.qdocconf file, at least for Qt 5.3.
        found = False  # Is (at least) one file found?
        for root, dirs, files in os.walk(src_path):
            for file in files:
                if file.endswith(".qdocconf") and "global" not in root and "-dita" not in file:
                    configs[file.replace(".qdocconf", "")] = root.replace("\\", "/") + "/" + file
                    found = True
        return found

    # Start the loop to retrieve the configuration files from the file system (Qt sources).
    def retrieve_from_sources():
        configs = {}  # Accumulator for configuration files.
        for folder in get_folders():
            path = sources + folder + "/"

            # Handle Qt Base modules, as they are all in the same folder.
            if folder == "qtbase":
                logging.debug("Handling preparation of Qt Base; path: " + path)

                # Handle QMake, as it does not live inside src/.
                configs["qmake"] = path + "qmake/doc/qmake.qdocconf"
                if not os.path.isfile(configs["qmake"]):
                    logging.error("qmake's qdocconf not found!")

                # Then deal with the modules in Qt Base. Don't do a brute force search on *.qdocconf as it takes a while
                # as long as possible.
                dirs = os.listdir(path + "src/")
                dirs = [x for x in dirs if x not in qt_base_ignore and x != "tools"]  # Folders to ignore.
                dirs = [x for x in dirs if os.path.isdir(path + "src/" + x)]  # Get rid of files.

                # For each Qt Base module, look for documentation configuration files.
                for module in dirs:
                    prefixed_module = "qt" + module if module != "corelib" else "qtcore"

                    module_path = path + "src/" + module + "/"
                    doc_file = module_path + "doc/" + prefixed_module + ".qdocconf"

                    if not os.path.isfile(doc_file):
                        logging.error("Qt Base " + module + "'s qdocconf not found!")
                    else:
                        configs[prefixed_module] = doc_file

                # Finally play with the tools.
                for tool in os.listdir(path + "src/tools/"):
                    tool_path = path + "src/tools/" + tool + "/"
                    doc_path = tool_path + "doc/"
                    if os.path.isdir(doc_path):
                        for doc_file in get_potential_file_names(doc_path, tool):
                            if os.path.isfile(doc_file):
                                configs[tool] = doc_file
                                break
                        else:
                            logging.error("Qt Base tool " + tool + "'s qdocconf not found!")
            # Other modules are directly inside the folder variable.
            else:
                logging.debug("Handling preparation of: " + folder + "; path: " + path)
                has_something = False

                # If the folder has a doc/ subfolder, it may contain a qdocconf file. Qt Sensors 5.3 has such a file,
                # but it should not be used (prefer the one in src/). This case is mainly for documentation-only
                # modules, such as qtdoc and qtwebkit-examples.
                # Qt Sensors has a configuration files in both doc/ and src/sensors/doc/, but the first one is outdated
                # (plus the doc/ folder has many other .qdocconf files which are just useless for this script).
                # @TODO: Enginio seems to have other idiosyncrasies... (documentation both in src/ and doc/).
                if os.path.isdir(path + "doc/") and folder != "qtsensors":
                    for doc_file in get_potential_file_names(path + "doc/", folder):
                        if os.path.isfile(doc_file):
                            configs[folder] = doc_file
                            has_something = True
                            break
                            # @TODO: May it happen there is more than one file?

                # Second try: the sources folder (src/ for most, Source/ for WebKit). This case is not exclusive: some
                # documentation could get lost, as the folder doc/ may exist (Enginio, Quick 1).
                if os.path.isdir(path + "src/"):
                    has_something = find_configuration_file(path + "src/", configs)
                elif os.path.isdir(path + "Source/"):
                    has_something = find_configuration_file(path + "Source/", configs)

                # Final check: there should be something in this directory (otherwise, if there is no bug, add it
                # manually in the ignore list).
                if not has_something:
                    logging.error("Module " + folder + "'s qdocconf not found!")

        return configs

    # Not asking to handle a file: return directly.
    if not configs_output or configs_file is None:
        logging.info("Looking for configuration files...")
        return retrieve_from_sources()
    else:
        if os.path.isfile(configs_file):
            logging.info("Reading existing list of configuration files...")
            # The target file exists: read it and return it.
            with open(configs_file) as jsonFile:
                return json.load(jsonFile)
        else:
            logging.info("Looking for configuration files and building a list...")
            # It does not exist: retrieve the information and write the file.
            configs = retrieve_from_sources()
            with open(configs_file, 'w') as jsonFile:
                json.dump(configs, jsonFile)
            return configs


# Generates the set of parameters to give to QDoc depending on the action.
def parameters(configuration_file, module_name, prepare=True):
    params = [qdoc,
              "-outputdir", output + module_name + "/",
              "-installdir", output,
              "-log-progress",
              configuration_file,
              "-prepare" if prepare else "-generate"]

    if prepare:
        params.extend(["-no-link-errors"])
    else:
        params.extend(["-indexdir", indexFolder])

    return params


# Prepare a module, meaning creating sub-folders for assets and (most importantly) the indexes.
def prepare_module(module_name, configuration_file):
    params = parameters(configuration_file, module_name, prepare=True)
    logging.debug(params)
    subprocess.call(params, env=environment)


# Build the documentation for the given module.
def generate_module(module_name, configuration_file):
    params = parameters(configuration_file, module_name, prepare=False)
    logging.debug(params)
    subprocess.call(params, env=environment)


# Convert the documentation HTML files as XML for the given module.
def generate_module_xml(module_name, configuration_file):
    for root, subdirs, files in os.walk(output + module_name + "/"):
        if root.endswith('/style') or root.endswith('/scripts') or root.endswith('/images'):
            continue

        for file in files:
            if file.endswith('.html'):
                base_file_name = os.path.join(root, file[:-5])
                in_file_name = base_file_name + '.html'
                out_file_name = base_file_name + '.xml'
                with open(in_file_name, "rb") as f:
                    tree = html5lib.parse(f)
                with open(out_file_name, 'wb') as f:
                    f.write(ETree.tostring(tree))
    logging.info('Parsing as XML: done with module %s' % module_name)


# Call an XSLT 2 engine to convert a single XHTML5 file into DocBook.
# Here, specific to one XSLT2 engine: Saxon 9. Displays errors to the user.
def call_xslt(file_in, file_out, stylesheet):
    result = subprocess.run(['java', '-jar', saxon9, '-s:%s' % file_in, '-xsl:%s' % stylesheet, '-o:%s' % file_out],
                            stdout=subprocess.PIPE)
    print(result.stdout)


# Convert the documentation XML files as DocBook for the given module.
def generate_module_db(module_name, configuration_file):
    for root, subdirs, files in os.walk(output + module_name + "/"):
        if root.endswith('/style') or root.endswith('/scripts') or root.endswith('/images'):
            continue

        for file in files:
            if file.endswith('.xml'):
                base_file_name = os.path.join(root, file[:-4])
                in_file_name = base_file_name + '.xml'
                out_file_name = base_file_name + '.db'
                call_xslt(in_file_name, out_file_name, xslt2)
    logging.info('XML to DocBook: done with module %s' % module_name)


# Algorithm:
# - retrieve the configuration files (*.qdocconf)
# - create the indexes by going through all source directories
# - start building things
if __name__ == '__main__':
    time_beginning = time.perf_counter()
    configs = get_configuration_files(outputConfigs, configsFile)
    time_configs = time.perf_counter()

    # @TODO: Seek for parallelism when running qdoc to fully use multiple cores (HDD may become a bottleneck).
    # Dependencies: first prepare all modules to get the indexes, then can start conversion in parallel. Once one module
    # is converted (or even started, as long as one file is out), the conversion to XML can start.
    if prepare:
        for moduleName, conf in configs.items():
            prepare_module(module_name=moduleName, configuration_file=conf)
    time_prepare = time.perf_counter()

    if generate:
        for moduleName, conf in configs.items():
            generate_module(module_name=moduleName, configuration_file=conf)
    time_generate = time.perf_counter()

    if generate_xml:
        for moduleName, conf in configs.items():
            generate_module_xml(module_name=moduleName, configuration_file=conf)
    time_xml = time.perf_counter()

    if generate_db:
        for moduleName, conf in configs.items():
            generate_module_db(module_name=moduleName, configuration_file=conf)
    time_db = time.perf_counter()

    print("Total time: %f s" % (time_db - time_beginning))
    print("Time to read configuration files: %f s" % (time_configs - time_beginning))
    print("Time to create indexes: %f s" % (time_prepare - time_configs))
    print("Time to generate HTML files: %f s" % (time_generate - time_prepare))
    print("Time to generate XML files: %f s" % (time_xml - time_generate))
    print("Time to generate DocBook files: %f s" % (time_db - time_xml))
