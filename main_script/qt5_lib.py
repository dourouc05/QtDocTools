import os
import os.path
import subprocess
import json
import logging
from collections import Counter
import re
import xml.etree.ElementTree as ETree
import html5lib


# TODO: investigate replacing html5lib by http://doc.scrapy.org/en/1.1/topics/feed-exports.html, should be much faster!

class Qt5Worker:
    def __init__(self, folders, version, binaries, stylesheet, schema, vocabulary='qdoctools', ignores=None,
                 list_configuration_cache=True):
        if ignores is None:
            ignores = {}

        # Copy the given values.
        self.folders = folders
        self.version = version
        self.binaries = binaries
        self.stylesheet = stylesheet
        self.schema = schema
        self.ignores = ignores
        self.vocabulary = vocabulary

        # Check the lists of folders.
        assert 'sources' in self.folders
        assert 'output' in self.folders
        if 'index_folder' not in self.folders:
            folders['index_folder'] = folders['output']

        # Check the version is well-formed.
        assert len(self.version) == 3  # Albeit not all of them are necessarily integers, e.g. 5.6.1-1.

        # Check the lists of binaries.
        assert 'qdoc' in self.binaries
        assert 'saxon9' in self.binaries
        assert 'jing' in self.binaries
        assert 'postprocess' in self.binaries
        # If 'launcher' in self.binaries, then use this to run Saxon; otherwise, full Python implementation.

        # Check the lists of items to ignore.
        if 'modules' not in self.ignores:
            self.ignores['modules'] = []
        if 'qt_base' not in self.ignores:
            self.ignores['qt_base'] = []
        if 'files' not in self.ignores:
            self.ignores['files'] = {}

        # Check the vocabulary is recognised.
        assert vocabulary == 'qtdoctools' or vocabulary == 'docbook'

        # Check the schema format is recognised.
        assert self.schema.endswith('.rng') or self.schema.endswith('.rnc')

        # Make the environment variables needed by QDoc.
        version_string = [str(x) for x in version]
        self.environment = {"QT_INSTALL_DOCS": self.folders['sources'] + "qtbase/doc",
                            "QT_VERSION_TAG": ''.join(version_string),  # E.g.: 531
                            "QT_VER": '.'.join(version_string[:2]),  # E.g.: 5.3
                            "QT_VERSION": '.'.join(version_string)}  # E.g.: 5.3.1

        # Deal with the list of configuration files.
        self.configuration_cache = folders['output'] + "configs.json"
        self.configuration_list = self._get_configuration(write_config=list_configuration_cache)

    def __get_folders(self):
        """ Retrieve the list of modules in the sources: get the list of files, ensure it is a directory, and a module
        (first letter is q). Handle ignore list.
        """
        sources = self.folders['sources']
        ignored = self.ignores['modules']
        return [folder for folder in os.listdir(sources)
                if os.path.isdir(sources + folder) and folder.startswith('q') and folder not in ignored]

    @staticmethod
    def __find_qdocconf_file(src_path, configs):
        """Find a configuration file within the given source folder; returns true if one is found, which is put
        inside the given map. This function is made for modules outside Qt Base.
        """

        # This loop is quite slow, but there is too much variety in those modules (such as a folder containing
        # multiple modules: the loop cannot be stopped at the first file!).
        # One more strange thing with Qt Multimedia: a qtmultimedia-dita.qdocconf file, at least for Qt 5.3.
        # Many cases to ignore...
        found = False  # Is (at least) one file found?
        for root, dirs, files in os.walk(src_path):
            for file in files:
                if file.endswith('.qdocconf') and 'global' not in root and '-dita' not in file:
                    configs[file.replace('.qdocconf', '')] = root.replace("\\", '/') + '/' + file
                    found = True
        return found

    @staticmethod
    def __get_potential_qdocconf_file_names(doc_path, module):
        """Generate potential places to find the configuration file for the given module."""

        # qtwebkit-examples is the name of the folder, but the file is named qtwebkitexamples.qdoc!
        return [doc_path + module + ".qdocconf", doc_path + "config/" + module + ".qdocconf",
                doc_path + module.replace("-", "") + ".qdocconf",
                doc_path + "config/" + module.replace("-", "") + ".qdocconf"]

    def __retrieve_configuration_from_sources(self):
        """Start the loop to retrieve the configuration files from the file system (Qt sources)."""

        configs = {}  # Accumulator for configuration files.
        for folder in self.__get_folders():
            path = self.folders['sources'] + folder + '/'

            # Handle Qt Base modules, as they are all in the same folder.
            if folder == "qtbase":
                logging.debug("Handling preparation of Qt Base; path: " + path)

                # Handle QMake, as it does not live inside src/.
                configs["qmake"] = path + "qmake/doc/qmake.qdocconf"
                if not os.path.isfile(configs["qmake"]):
                    logging.error("qmake's qdocconf not found!")

                # Then deal with the modules in Qt Base. Don't do a brute force search on *.qdocconf (as it takes
                # a while) as long as possible.
                dirs = os.listdir(path + "src/")
                dirs = [x for x in dirs if x not in self.ignores['qt_base'] and x != "tools"]  # Folders to ignore.
                dirs = [x for x in dirs if os.path.isdir(path + "src/" + x)]  # Get rid of files.

                # For each Qt Base module, look for documentation configuration files.
                for module in dirs:
                    prefixed_module = "qt" + module if module != "corelib" else "qtcore"

                    module_path = path + "src/" + module + '/'
                    doc_file = module_path + "doc/" + prefixed_module + ".qdocconf"

                    if not os.path.isfile(doc_file):
                        logging.error("Qt Base " + module + "'s qdocconf not found!")
                    else:
                        configs[prefixed_module] = doc_file

                # Finally play with the tools.
                for tool in os.listdir(path + "src/tools/"):
                    tool_path = path + "src/tools/" + tool + '/'
                    doc_path = tool_path + "doc/"
                    if os.path.isdir(doc_path):
                        for doc_file in self.__get_potential_qdocconf_file_names(doc_path, tool):
                            if os.path.isfile(doc_file):
                                configs[tool] = doc_file
                                break
                        else:
                            logging.error("Qt Base tool " + tool + "'s qdocconf not found!")
            # Other modules are directly inside the folder variable.
            else:
                logging.debug("Handling preparation of: " + folder + "; path: " + path)
                has_something = False

                # If the folder has a doc/ subfolder, it may contain a qdocconf file. Qt Sensors 5.3 has such
                # a file, but it should not be used (prefer the one in src/). This case is mainly for
                # documentation-only modules, such as qtdoc and qtwebkit-examples.
                # Qt Sensors has a configuration files in both doc/ and src/sensors/doc/, but the first one is
                # outdated (plus the doc/ folder has many other .qdocconf files which are just useless
                # for this script).
                # @TODO: Enginio seems to have other idiosyncrasies... (documentation both in src/ and doc/).
                if os.path.isdir(path + "doc/") and folder != "qtsensors":
                    for doc_file in self.__get_potential_qdocconf_file_names(path + "doc/", folder):
                        if os.path.isfile(doc_file):
                            configs[folder] = doc_file
                            has_something = True
                            break
                            # @TODO: May it happen there is more than one file?

                # Second try: the sources folder (src/ for most, Source/ for WebKit). This case is not exclusive:
                # some documentation could get lost, as the folder doc/ may exist (Enginio, Quick 1).
                if os.path.isdir(path + "src/"):
                    has_something = self.__find_qdocconf_file(path + "src/", configs)
                elif os.path.isdir(path + "Source/"):
                    has_something = self.__find_qdocconf_file(path + "Source/", configs)

                # Final check: there should be something in this directory (otherwise, if there is no bug, add it
                # manually in the ignore list).
                if not has_something:
                    logging.error("Module " + folder + "'s qdocconf not found!")

        return configs

    def _get_configuration(self, write_config=False):
        """Retrieve the list of configuration files."""

        if not write_config:
            logging.info("Looking for configuration files...")
            return self.__retrieve_configuration_from_sources()
        elif os.path.isfile(self.configuration_cache):  # The target file exists: read it and return it.
            logging.info("Reading existing list of configuration files...")
            with open(self.configuration_cache) as jsonFile:
                return json.load(jsonFile)
        else:  # It does not exist: retrieve the information and write the file.
            logging.info("Looking for configuration files and building a list...")
            configs = self.__retrieve_configuration_from_sources()

            # Create the file (and its containing directory!).
            os.makedirs(os.path.dirname(self.configuration_cache), exist_ok=True)
            with open(self.configuration_cache, 'w') as jsonFile:
                json.dump(configs, jsonFile)

            return configs

    def _qdoc_parameters(self, module_name, prepare):
        """Generates the set of parameters to give to QDoc depending on the action."""

        params = [self.binaries['qdoc'],
                  "-outputdir", self.folders['output'] + module_name + '/',
                  "-installdir", self.folders['output'],
                  "-log-progress",
                  self.configuration_list[module_name],
                  "-prepare" if prepare else "-generate"]
        params.extend(["-no-link-errors"] if prepare else ["-indexdir", self.folders['index_folder']])
        return params

    def __saxon_parameters(self, file_in, file_out):
        return ['java', '-jar', self.binaries['saxon9'],
                '-s:%s' % file_in,
                '-xsl:%s' % self.stylesheet,
                '-o:%s' % file_out,
                'vocabulary=%s' % self.vocabulary]

    def _call_xslt(self, file_in, file_out, error_recovery=True):
        """Call an XSLT 2 engine to convert a single XHTML5 file into DocBook. If there is a comment issue
         (something like <!-- -- -->), then deal with it. Displays errors to the user.
        """

        result = subprocess.run(self.__saxon_parameters(file_in, file_out), stderr=subprocess.PIPE)
        if len(result.stderr) > 0:
            error = result.stderr.decode('utf-8')

            if not error_recovery:
                logging.warning("Problem(s) with file '%s' at stage XSLT: \n%s" % (file_in, error))
            else:
                # One-line comments about the function operator--; remove all one-line comments.
                if 'SXXP0003: Error reported by XML parser: The string "--" is not permitted within comments.' in error:
                    def remove_comments(l):
                        ls = l.strip()
                        return '' if ls.startswith('<!--') and ls.endswith('-->') else l

                    with open(file_in, 'r') as file:
                        lines = file.readlines()
                    lines = [remove_comments(l) for l in lines]
                    with open(file_in, 'w') as file:
                        file.write("\n".join(lines))

                # Invalid characters happening in the XML files (i.e. binary output within the doc!).
                elif 'SXXP0003: Error reported by XML parser: An invalid XML character' in error:
                    with open(file_in, 'r') as file:
                        lines = file.readlines()
                    regex = re.compile('[^\x09\x0A\x0D\x20-\uD7FF\uE000-\uFFFD\u10000-\u10FFF]')
                    lines = [regex.sub('', l) for l in lines]
                    with open(file_in, 'w') as file:
                        file.write("\n".join(lines))

                # Identifiers occurring multiple times: rewrite the next occurrences.
                elif 'XTMM9000: Processing terminated by xsl:message' in error \
                        and 'ERROR: Some ids are not unique!' in error:
                    with open(file_in, 'r') as file:
                        lines = file.readlines()

                    # Algorithm: remember the number of times each ID was ever seen in the document; if it is higher
                    # than two, then rewrite the line containing the ID. Pay attention to the fact that those IDs are
                    # sometimes duplicated, i.e. present at the same time within a <a name> tag and
                    # within the title <h? id> tag.
                    # If there are multiple <html:a>, they have the same ID and lie on the same line
                    # (qtquick-cppextensionpoints).
                    a_seen = Counter()  # Number of times this ID was seen for a <a name=""> tag.
                    h_seen = Counter()  # Number of times this ID was seen for a <h? id="">  tag.
                    lines_new = []
                    for line in lines:
                        # Detect an identifier.
                        if '<html:a name="' in line or ('<html:h' in line and 'id="' in line):
                            found_id_results = re.search('id="(.*)"|name="(.*)"', line, re.IGNORECASE)
                            found_id = (found_id_results.group(0) if found_id_results.group(0) is not None
                                        else found_id_results.group(1)).split('"')[1]
                            is_a = '<html:a' in line
                            is_h = '<html:h' in line

                            # Count this occurrence in what has been seen.
                            if is_a:
                                a_seen[found_id] += 1
                            elif is_h:
                                h_seen[found_id] += 1

                            # Rewrite the line if need be.
                            increment = max(a_seen.get(found_id, 0), h_seen.get(found_id, 0))
                            if increment >= 2:
                                line = line.replace(found_id, '%s-%d' % (found_id, increment))

                        lines_new.append(line)

                    with open(file_in, 'w') as file:
                        file.write("".join(lines_new))

                # Not a recognised issue, nothing you can do.
                else:
                    logging.warning("Problem(s) with file '%s' at stage XSLT: \n%s" % (file_in, error))

                # Restart the XSLT engine, see if changed anything in the process.
                result = subprocess.run(self.__saxon_parameters(file_in, file_out), stderr=subprocess.PIPE)
                if len(result.stderr) == 0:
                    return
                else:
                    logging.warning("Problem(s) with file '%s' at stage XSLT: \n%s" % (file_in, error))

    def _call_xslt_launcher(self, folder, error_recovery=True):
        """Call the XSLT launcher script. As it reuses the JVM for the operations, this is much faster than the
        pure Java solution."""

        params = ['java', '-cp', self.binaries['launcher'], self.stylesheet, folder, 'true' if error_recovery else 'false']
        result = subprocess.run(params, stderr=subprocess.PIPE)
        if len(result.stderr) > 0:
            error = result.stderr.decode('utf-8')
            logging.warning("Problem(s) at stage XSLT: \n%s" % error)

    def _call_cpp_parser(self, file_in, file_out):
        """Call the C++ parser for prototypes."""

        params = [self.binaries['postprocess'], '-s:%s' % file_in, '-o:%s' % file_out]
        result = subprocess.run(params, stderr=subprocess.PIPE)
        if len(result.stderr) > 0:
            errors = result.stderr.decode('utf-8')
            logging.warning("Problem(s) with file '%s' at C++ prototypes: \n%s" % (file_in, errors))

    def _call_validator(self, file):
        """Call a validator on the given file."""

        params = ['java', '-jar', self.binaries['jing']]
        if self.schema.endswith('.rnc'):
            params.extend(['-c', self.schema, file])
        elif self.schema.endswith('.rng'):
            params.extend([self.schema, file])

        result = subprocess.run(params, stderr=subprocess.PIPE)
        if len(result.stderr) > 0:
            logging.warning(
                "Problem(s) with file '%s' at validation: \n%s" % (file, result.stderr.decode('utf-8')))

    def modules_list(self):
        return self.configuration_list.keys()

    def n_modules(self):
        return len(self.configuration_list)

    def prepare_module(self, module_name):
        """Prepare a module, meaning creating sub-folders for assets and (most importantly) the indexes."""

        params = self._qdoc_parameters(module_name, prepare=True)
        logging.debug(params)
        subprocess.call(params, env=self.environment)

    def generate_module(self, module_name):
        """Build the documentation for the given module."""

        params = self._qdoc_parameters(module_name, prepare=False)
        logging.debug(params)
        subprocess.call(params, env=self.environment)

    def generate_module_xml(self, module_name):
        """Convert the documentation HTML files as XML for the given module."""

        count_xml = 0
        for root, sub_dirs, files in os.walk(self.folders['output'] + module_name + '/'):
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
                    count_xml += 1
        return count_xml

    def generate_module_db(self, module_name):
        """Convert the documentation XML files as DocBook for the given module."""

        # Launcher not present in the arguments:
        if 'launcher' not in self.binaries:
            ext = '.xml'  # Extension for files that are recognised here.
            forbidden_suffixes = ['-members', '-compat', '-obsolete']  # Supplementary files to some base class.
            ignored_suffixes = ['-manifest']  # Suffixes for ignored files.
            count_db = 0

            for root, sub_dirs, files in os.walk(self.folders['output'] + module_name + '/'):
                if root.endswith('/style') or root.endswith('/scripts') or root.endswith('/images'):
                    continue

                count = 0
                n_files = len(files)
                for file in files:
                    count += 1

                    # Handle a bit of output (even though the DocBook counter is not yet updated for this iteration).
                    if count % 10 == 0:
                        logging.info('XML to DocBook: module %s, %i files done out of %i (%i DocBook files generated)'
                                     % (module_name, count, n_files, count_db))

                    # Avoid lists of examples (-manifest.xml) and files automatically included within the output
                    # with the XSLT stylesheet (-members.xml, -obsolete.xml, -compat.xml).
                    if not file.endswith(ext):
                        continue
                    if any([file.endswith(fs + ext) for fs in ignored_suffixes]):
                        continue
                    if any([file.endswith(fs + ext) for fs in forbidden_suffixes]):
                        continue
                    if module_name in self.ignores['files'] and file in self.ignores['files'][module_name]:
                        continue
                    count_db += 1

                    # Actual processing.
                    base_file_name = os.path.join(root, file[:-4])
                    in_file_name = base_file_name + '.xml'
                    out_file_name = base_file_name + '.db'
                    self._call_xslt(in_file_name, out_file_name)

                    # For C++ classes, also handle the function prototypes with the C++ application.
                    if file.startswith('q') and not file.startswith('qml-'):
                        self._call_cpp_parser(out_file_name, out_file_name)

            return count_db
        else:
            count_db = 0

            # Start the launcher; it does not do anything with the C++ parser, so do it afterwards.
            self._call_xslt_launcher(self.folders['output'] + module_name, error_recovery=True)
            for root, sub_dirs, files in os.walk(self.folders['output'] + module_name + '/'):
                if root.endswith('/style') or root.endswith('/scripts') or root.endswith('/images'):
                    continue

                for file in files:
                    out_file_name = os.path.join(root, file[:-4]) + '.db'
                    if file.startswith('q') and not file.startswith('qml-'):
                        self._call_cpp_parser(out_file_name, out_file_name)

            return count_db

    def validate_module_db(self, module_name):
        """"Convert the documentation XML files as DocBook for the given module."""

        ext = '.db'  # Extension for files that are recognised here.
        count_db = 0

        for root, sub_dirs, files in os.walk(self.folders['output'] + module_name + '/'):
            if root.endswith('/style') or root.endswith('/scripts') or root.endswith('/images'):
                continue

            count = 0
            n_files = len(files)
            for file in files:
                count += 1

                # Handle a bit of output (even though the DocBook counter is not yet updated for this iteration).
                if count % 10 == 0:
                    logging.info('DocBook validation: module %s, %i files done out of %i (%i DocBook files validated)'
                                 % (module_name, count, n_files, count_db))

                if not file.endswith(ext):
                    continue
                count_db += 1

                # Actual processing.
                file_name = os.path.join(root, file)
                self._call_validator(file_name)

        return count_db
