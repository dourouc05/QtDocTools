__author__ = 'Thibaut Cuvelier'

import os, os.path, subprocess

sources = "C:\\Qt\\5.3\\Src\\"
qdoc = "C:\\Qt\\5.3\\mingw482_32\\bin\\qdoc.exe"
output = "C:\\Qt\\_script\\html\\"
conf = "C:\\Qt\\5.3\\Src\\qtbase\\src\\corelib\\doc\\qtcore.qdocconf"

environment = {"QT_INSTALL_DOCS": sources + "qtbase/doc", "QT_VERSION_TAG": "531", "QT_VER": "5.3", "QT_VERSION": "5.3.1"}

#subprocess.call([qdoc, "-version"], env=environment)

# Modules to ignore.
ignored = ["qttranslations"]

# Retrieve the list of modules in the sources.
modules = []
for module in os.listdir(sources):
    if module.startswith('q') and not module.endswith('.pro') and os.path.isdir(sources + module):
        modules.append(module)

# First, create the indexes by going through all source directories.
def prepareModule(path, outputDirectory, installDirectory, conf):
    # for sub in os.listdir(path):
    #     subdir = path + "/" + sub
    #     if os.path.isdir(subdir):
    #         prepareModule(subdir, outputDirectory, installDirectory, conf)

    subprocess.call([qdoc, "-outputdir", outputDirectory, "-installdir", installDirectory, conf], env = environment) # , "-prepare", "-no-link-errors"

for module in modules:
    path = sources + module + "/"

    if module in ignored:
        pass
    elif module == "qtbase" or module == "qtdoc":
        print(module)
    elif os.path.isdir(path + "src"): #  or os.path.isdir(path + "doc"):
        print("Handling preparation of: " + module)
        prepareModule(path = path + "src/", outputDirectory = output + module + "/", installDirectory = output, conf = conf)
    else:
        print("Problem: unknown structure for " + module)