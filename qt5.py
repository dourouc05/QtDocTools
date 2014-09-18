__author__ = 'Thibaut Cuvelier'

import os, os.path, subprocess

sources = "C:/Qt/5.3/Src/"
qdoc = "C:/Qt/5.3/mingw482_32/bin/qdoc.exe"
output = "C:/Qt/_script/html/"
# conf = "C:/Qt/5.3/Src/qtbase/src/corelib/doc/qtcore.qdocconf"

environment = {"QT_INSTALL_DOCS": sources + "qtbase/doc", "QT_VERSION_TAG": "531", "QT_VER": "5.3", "QT_VERSION": "5.3.1"}

# Modules to ignore.
ignored = ["qttranslations"]

# Retrieve the list of modules in the sources, qtbase being the first one.
modules = ["qtbase", "qtdoc", "qmake"]
for module in os.listdir(sources):
    if module.startswith('q') and os.path.isdir(sources + module) and module != "qtbase": # and not module.endswith('.pro')
        modules.append(module)

# First, create the indexes by going through all source directories.
# This requires to first handle Qt Base, to generate its index, then to give it as an argument to all the others.
def prepareModule(path, outputDirectory, installDirectory, conf):
    print("  " + path)

    os.chdir(path)
    params = [qdoc,
              "-outputdir", outputDirectory,
              "-installdir", installDirectory,
              conf,
              "-prepare", "-no-link-errors"]

    if "qtbase" not in path:
        params.extend(["-indexdir", sources + "qtbase/doc"])

    subprocess.call(params, env = environment)

# In order to create the indexes, retrieve the list of configuration files.
configs = {}

for module in modules:
    path = sources + module + "/"

    if module in ignored:
        pass
    elif module == "qtbase": # QMake is hosted in Qt Base.
        print("Handling preparation of QMake; path: " + path)
        for dirpath, dirnames, files in os.walk(path):
            for file in files:
                if file.lower().endswith(".qdocconf"):
                    print("-- " + file)
    elif module == "qmake": # QMake is hosted in Qt Base.
        path = sources + "qtbase/qmake"
        print("Handling preparation of QMake; path: " + path)
        for dirpath, dirnames, files in os.walk(path): # Don't look in src/!
            for file in files:
                if file.lower().endswith(".qdocconf"):
                    print("-- " + file)
    #elif module == "qtbase" or module == "qtdoc":
    #    print(module)
    #elif os.path.isdir(path + "src"): #  or os.path.isdir(path + "doc"):
    else:
        print("Handling preparation of: " + module + "; path: " + path)

        # Find all documentation configuration files.
        for dirpath, dirnames, files in os.walk(path):
            for file in files:
                if file.lower().endswith(".qdocconf"):
                    print("-- " + file)


        conf = sources + module + "/" # "qtbase/src/corelib/doc/qtcore.qdocconf"
        #prepareModule(path = path, outputDirectory = output + module + "/", installDirectory = output, conf = conf) #  + "src/"
    #else:
    #    print("Problem: unknown structure for " + module)