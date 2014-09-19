__author__ = 'Thibaut Cuvelier'

import os, os.path, subprocess

sources = "C:/Qt/5.3/Src/"
qdoc = "C:/Qt/5.3/mingw482_32/bin/qdoc.exe"
output = "C:/Qt/_script/html/"
# conf = "C:/Qt/5.3/Src/qtbase/src/corelib/doc/qtcore.qdocconf"

environment = {"QT_INSTALL_DOCS": sources + "qtbase/doc", "QT_VERSION_TAG": "531", "QT_VER": "5.3", "QT_VERSION": "5.3.1"}

# Modules to ignore (outside Qt Base).
ignored = ["qttranslations"]
# Folders to ignore inside Qt Base (/Qt/5.3/Src/qtbase/src).
qtbaseignore = ["3rdparty", "android", "angle", "winmain", "openglextensions", "platformsupport", "plugins"]

# Retrieve the list of modules in the sources.
folders = []
for folder in os.listdir(sources):
    if folder.startswith('q') and os.path.isdir(sources + folder): # and not module.endswith('.pro')
        folders.append(folder)

# In order to create the indexes, retrieve the list of configuration files.
configs = {}

for folder in folders:
    path = sources + folder + "/"

    if folder in ignored:
        pass
    elif folder == "qtbase": # QMake is hosted in Qt Base.
        print("Handling preparation of Qt Base; path: " + path)

        # Handle QMake, as it does not live inside src/.
        configs["qmake"] = path + "qmake/doc/qmake.qdocconf"
        if not os.path.isfile(configs["qmake"]):
            print("ERROR: QMake's qdocconf not found!")

        # Then deal with the modules in Qt Base. Don't do a brute force search on *.qdocconf as it takes a while.
        dirs = os.listdir(path + "src/")
        # Filter out folders to ignore; tools are handled separately.
        dirs = [x for x in dirs if x not in qtbaseignore and x != "tools"]
        # Filter out files (like src.pro, the project file).
        dirs = [x for x in dirs if os.path.isdir(path + "src/" + x)]

        # For each Qt Base module, look for documentation configuration files.
        for module in dirs:
            prefixedModule = "qtcore" if module == "corelib" else "qt" + module

            modulePath = path + "src/" + module + "/"
            docPath = modulePath + "doc/"
            docFile = docPath + prefixedModule + ".qdocconf"

            if not os.path.isfile(docFile):
                print("ERROR: Qt Base " + module + "'s qdocconf not found!")

            configs[prefixedModule] = docFile

        # Finally play with the tools.
        for tool in os.listdir(path + "src/tools/"):
            toolPath = path + "src/tools/" + tool + "/"
            docPath = toolPath + "doc/"
            if os.path.isdir(docPath):
                docFiles = [docPath + tool + ".qdocconf", docPath + "config/" + tool + ".qdocconf"] # QLalr and QDoc
                for docFile in docFiles:
                    if os.path.isfile(docFile):
                        configs[tool] = docFile
                        break
                else:
                    print("ERROR: Qt Base tool " + tool + "'s qdocconf not found!")
    else: # Other modules.
        print("Handling preparation of: " + folder + "; path: " + path)

        hasSomething = False

        # If the folder has a doc/ subfolder, it may contain a qdocconf file. Qt Sensors 5.3 has such a file, but
        # it should not be used (prefer the one in src/). This case is mainly for documentation-only modules,
        # such as qtdoc and qtwebkit-examples.
        if os.path.isdir(path + "doc/") and folder != "qtsensors":
            docPath = path + "doc/"

            # qtwebkit-examples is the name of the folder, but the file is named qtwebkitexamples.qdoc!
            # Enginio seems to have other idiosyncracies... (documentation both in src/ and doc/).
            docFiles = [docPath + folder + ".qdocconf", docPath + "config/" + folder + ".qdocconf",
                        docPath + folder.replace("-", "") + ".qdocconf",
                        docPath + "config/" + folder.replace("-", "") + ".qdocconf"]

            for docFile in docFiles:
                if os.path.isfile(docFile):
                    configs[folder] = docFile
                    hasSomething = True
                    break
                    # @TODO: May it happen there is more than one file?

        # Second try: the sources folder (either src/ or Source/).
        if os.path.isdir(path + "src/"): # Not exclusive! Otherwise, some documentation is lost (Enginio, Quick 1).
            srcPath = path + "src/"

            # This loop is quite slow, but there is too much variety in those modules (such as a folder containing
            # multiple modules).
            # One more strange thing with Qt Multimedia: a qtmultimedia-dita.qdocconf file.
            found = False # Is (at least) one file found?
            for dirpath, dirnames, files in os.walk(srcPath):
                for file in files:
                    if file.endswith(".qdocconf") and "global" not in dirpath and "-dita" not in file:
                        configs[file.replace(".qdocconf", "")] = dirpath.replace("\\", "/") + "/" + file
                        found = True
            if not found:
                print("ERROR: module " + folder + "'s qdocconf not found!")
            else:
                hasSomething = True
        elif os.path.isdir(path + "Source/"): # This one is for WebKit...
            srcPath = path + "Source/"

            # This loop is quite slow, but there is too much variety in those modules...
            found = False # Is (at least) a file found?
            for dirpath, dirnames, files in os.walk(srcPath):
                for file in files:
                    if file.endswith(".qdocconf") and "global" not in dirpath:
                        configs[file.replace(".qdocconf", "")] = dirpath + file
                        found = True
            if not found:
                print("ERROR: module " + folder + "'s qdocconf not found!")
            else:
                hasSomething = True

        # Final check: there should be something in this directory (otherwise, add it in the ignore list).
        if not hasSomething:
            print("ERROR: module " + folder + " completely unrecognised.")

print(configs)
# Second stage: prepare the documentation, the indexes. Qt Core must be done first, the order has no importance for
# the others.

# First, create the indexes by going through all source directories.
# This requires to first handle Qt Core, to generate its index, then to give it as an argument to all the others.
# @TODO: Triple check whether Qt Core must be done first.
# @TODO: Seek for parallelisation when running qdoc to fully use multiple cores.
def prepareModule(moduleName, conf):
    params = [qdoc,
              "-outputdir", output + moduleName + "/",
              "-installdir", output,
              conf,
              "-prepare",
              "-indexdir", output,
              "-no-link-errors",
              "-log-progress"]

    print(params)
    subprocess.call(params, env = environment)

prepareModule(moduleName = "qtcore", conf = configs["qtcore"])
for moduleName, conf in configs.items():
    if moduleName == "qtcore":
        continue

    prepareModule(moduleName = moduleName, conf = conf)

# Eventually start building things.
def generateModule(moduleName, conf):
    params = [qdoc,
              "-outputdir", output + moduleName + "/",
              "-installdir", output,
              conf,
              "-generate",
              "-indexdir", output,
              "-no-link-errors",
              "-log-progress"]

    print(params)
    subprocess.call(params, env = environment)

for moduleName, conf in configs.items():
    generateModule(moduleName = moduleName, conf = conf)