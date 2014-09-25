__author__ = 'Thibaut Cuvelier'

import os
import os.path
import subprocess
import json
import logging
import sys

# Command-line configuration.
sources = "C:/Qt/5.3/Src/"
qdoc = "C:/Qt/5.3/mingw482_32/bin/qdoc.exe"
output = "C:/Qt/_script/dita/"
indexFolder = output
version = [5, 3, 1]

configsFile = output + "configs.json"
outputConfigs = True  # Read the file if it exists (and skip this phase), write it otherwise.

prepare = False
generate = False  # If prepare is not True when generate is, need an indexFolder.
outputFormat = "dita"  # "html" or "dita"; read only if generate is True.

logging.basicConfig(format='%(levelname)s at %(asctime)s: %(message)s', level=logging.DEBUG)

# Off-line configuration, should be altered only infrequently.
# Modules to ignore (outside Qt Base). They have no documentation for them.
ignored = ["qttranslations"]
# Folders to ignore inside Qt Base sources (/Qt/5.3/Src/qtbase/src). This is mostly to avoid spending time on them.
qtbaseignore = ["3rdparty", "android", "angle", "winmain", "openglextensions", "platformsupport", "plugins"]

# Make the environment variables needed by QDoc.
versionString = [str(x) for x in version]
environment = {"QT_INSTALL_DOCS": sources + "qtbase/doc",
               "QT_VERSION_TAG":  ''.join(versionString),       # E.g.: 531
               "QT_VER":          '.'.join(versionString[:2]),  # E.g.: 5.3
               "QT_VERSION":      '.'.join(versionString)}      # E.g.: 5.3.1

# Retrieve the list of modules in the sources: get the list of files, ensure it is a directory, and a module (first
# letter is q). Handle ignore list.
def getFolders():
    return [folder for folder in os.listdir(sources)
            if os.path.isdir(sources + folder) and folder.startswith('q') and folder not in ignored]

# In order to create the indexes, retrieve the list of configuration files.
def getConfigurationFiles(configsOutput=False, configsFile=None):
    # Generate potential places to find the configuration file for the given module.
    def getPotentialFileNames(docPath, module):
        # qtwebkit-examples is the name of the folder, but the file is named qtwebkitexamples.qdoc!
        return [docPath + module + ".qdocconf", docPath + "config/" + module + ".qdocconf",
                docPath + module.replace("-", "") + ".qdocconf",
                docPath + "config/" + module.replace("-", "") + ".qdocconf"]

    # Find a configuration file within the given source folder; returns true if one is found, which is put inside the
    # given map. This function is made for modules outside Qt Base.
    def findConfigurationFile(srcPath, configs):
        # This loop is quite slow, but there is too much variety in those modules (such as a folder containing
        # multiple modules: the loop cannot be stopped at the first file!).
        # One more strange thing with Qt Multimedia: a qtmultimedia-dita.qdocconf file.
        found = False  # Is (at least) one file found?
        for root, dirs, files in os.walk(srcPath):
            for file in files:
                if file.endswith(".qdocconf") and "global" not in root and "-dita" not in file:
                    configs[file.replace(".qdocconf", "")] = root.replace("\\", "/") + "/" + file
                    found = True
        return found

    # Start the loop to retrieve the configuration files from the file system (Qt sources).
    def retrieveFromSources():
        configs = {}  # Accumulator for configuration files.
        for folder in getFolders():
            path = sources + folder + "/"

            # Handle Qt Base modules, as they are all in the same folder.
            if folder == "qtbase":
                logging.debug("Handling preparation of Qt Base; path: " + path)

                # Handle QMake, as it does not live inside src/.
                configs["qmake"] = path + "qmake/doc/qmake.qdocconf"
                if not os.path.isfile(configs["qmake"]):
                    logging.error("QMake's qdocconf not found!")

                # Then deal with the modules in Qt Base. Don't do a brute force search on *.qdocconf as it takes a while as
                # long as possible.
                dirs = os.listdir(path + "src/")
                dirs = [x for x in dirs if x not in qtbaseignore and x != "tools"]  # Folders to ignore.
                dirs = [x for x in dirs if os.path.isdir(path + "src/" + x)]  # Get rid of files.

                # For each Qt Base module, look for documentation configuration files.
                for module in dirs:
                    prefixedModule = "qt" + module if module != "corelib" else "qtcore"

                    modulePath = path + "src/" + module + "/"
                    docFile = modulePath + "doc/" + prefixedModule + ".qdocconf"

                    if not os.path.isfile(docFile):
                        logging.error("Qt Base " + module + "'s qdocconf not found!")
                    else:
                        configs[prefixedModule] = docFile

                # Finally play with the tools.
                for tool in os.listdir(path + "src/tools/"):
                    toolPath = path + "src/tools/" + tool + "/"
                    docPath = toolPath + "doc/"
                    if os.path.isdir(docPath):
                        for docFile in getPotentialFileNames(docPath, tool):
                            if os.path.isfile(docFile):
                                configs[tool] = docFile; break
                        else:
                            logging.error("Qt Base tool " + tool + "'s qdocconf not found!")
            # Other modules are directly inside the folder variable.
            else:
                logging.debug("Handling preparation of: " + folder + "; path: " + path)
                hasSomething = False

                # If the folder has a doc/ subfolder, it may contain a qdocconf file. Qt Sensors 5.3 has such a file, but
                # it should not be used (prefer the one in src/). This case is mainly for documentation-only modules,
                # such as qtdoc and qtwebkit-examples.
                # Qt Sensors has a configuration files in both doc/ and src/sensors/doc/, but the first one is outdated
                # (plus the doc/ folder has many other .qdocconf files which are just useless for this script).
                # @TODO: Enginio seems to have other idiosyncrasies... (documentation both in src/ and doc/).
                if os.path.isdir(path + "doc/") and folder != "qtsensors":
                    for docFile in getPotentialFileNames(path + "doc/", folder):
                        if os.path.isfile(docFile):
                            configs[folder] = docFile
                            hasSomething = True
                            break
                            # @TODO: May it happen there is more than one file?

                # Second try: the sources folder (src/ for most, Source/ for WebKit). This case is not exclusive: some
                # documentation could get lost, as the folder doc/ may exist (Enginio, Quick 1).
                if os.path.isdir(path + "src/"):
                    hasSomething = findConfigurationFile(path + "src/", configs)
                elif os.path.isdir(path + "Source/"):
                    hasSomething = findConfigurationFile(path + "Source/", configs)

                # Final check: there should be something in this directory (otherwise, if there is no bug, add it manually
                # in the ignore list).
                if not hasSomething:
                    logging.error("Module " + folder + "'s qdocconf not found!")

        return configs

    # Not asking to handle a file: return directly.
    if not configsOutput or configsFile is None:
        logging.info("Looking for configuration files...")
        return retrieveFromSources()
    else:
        if os.path.isfile(configsFile):
            logging.info("Reading existing list of configuration files...")
            # The target file exists: read it and return it.
            with open(configsFile) as jsonFile:
                return json.load(jsonFile)
        else:
            logging.info("Looking for configuration files and building a list...")
            # It does not exist: retrieve the information and write the file.
            configs = retrieveFromSources()
            with open(configsFile, 'w') as jsonFile:
                json.dump(configs, jsonFile)
            return configs

# Generates the set of parameters to give to QDoc depending on the action.
def parameters(configurationFile, moduleName, prepare=True):
    return [qdoc,
            "-outputdir", output + moduleName + "/",
            "-installdir", output,
            "-indexdir", indexFolder,
            "-log-progress",
            configurationFile,
            "-prepare" if prepare else "-generate",
            "-no-link-errors" if prepare else ""]

# Prepare a module, meaning creating sub-folders for assets and (most importantly) the indexes.
def prepareModule(moduleName, configurationFile):
    params = parameters(configurationFile, moduleName, prepare=True)
    logging.debug(params)
    subprocess.call(params, env=environment)

# Build the documentation for the given module.
def generateModule(moduleName, configurationFile):
    params = parameters(configurationFile, moduleName, prepare=False)
    logging.debug(params)
    subprocess.call(params, env=environment)

# Algorithm:
# - retrieve the configuration files (*.qdocconf)
# - create the indexes by going through all source directories
# - rewrite the configuration files if needed.
# - start building things
if __name__ == '__main__':
    configs = getConfigurationFiles(outputConfigs, configsFile)

    # @TODO: Seek for parallelism when running qdoc to fully use multiple cores (HDD may become a bottleneck)
    if prepare:
        for moduleName, conf in configs.items():
            prepareModule(moduleName=moduleName, configurationFile=conf)

    # @TODO: Parallel too.
    if outputFormat != "html":
        if outputFormat == "dita":
            logging.info("Rewriting configurations files from HTML to DITA...")
            sys.exit(0)
        else:
            logging.error("Asked to generate " + outputFormat + "files, but unrecognised format")

    # @TODO: This one should be embarrassingly parallel too.
    if generate:
        for moduleName, conf in configs.items():
            generateModule(moduleName=moduleName, configurationFile=conf)