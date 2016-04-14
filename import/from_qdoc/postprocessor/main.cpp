#include <iostream>
#include <list>
#include <sstream>
#include <iterator>
#include <algorithm>

#include "AST.hpp"
#include "parser.hpp"
#include "pugixml\src\pugixml.hpp"

#define RUN_TEST_SUITE 1 // 0 to disable the test suite, other values to enable it. 

bool test_differ(const AST* const ast, const std::string & str) {
	std::string original = str;
	std::string serialised = ast->serialise();
	original.erase(std::remove(original.begin(), original.end(), ' '), original.end());
	serialised.erase(std::remove(serialised.begin(), serialised.end(), ' '), serialised.end());
	return original.compare(serialised) != 0;
}

bool test_match(const std::string & str, const std::string & testName) {
	// Start parsing. 
	const char * cstr = str.c_str();
	AST* ast = cpp_prototype(cstr, cstr + str.length());

	if (!ast->matched) {
		std::cerr << testName << " failed (no match): '" << str << "'" << std::endl;
		delete ast;
		return false;
	}

	// Test the AST: serialise it as C++ prototype; compare with input, removing all spaces.
	if (test_differ(ast, str)) {
		std::cerr << testName << " failed (ASTs differ): '" << str << "'" << std::endl;
		std::cerr << "    Found '" << ast->serialise() << "' instead." << std::endl;
		delete ast;
		return false;
	}

	// Done! 
	std::cerr << testName << " passed!" << std::endl;
	delete ast;
	return true;
}

void test() {
	int count = 0; 
	int total = 0;
	
	total++; count += test_match("()", "Dumb test");
	total++; count += test_match("(QRect rectangle)", "Simple test");
	total++; count += test_match("(const QRect & rectangle)", "Constant reference test");
	total++; count += test_match("(const QRect & rectangle, QSize * size)", "Two parameters and a pointer test");
	total++; count += test_match("(const QRect && rectangle, QSize ** size)", "Move semantics and double pointer test");
	total++; count += test_match("(QRect rectangle = 0)", "Simple initialiser test");
	total++; count += test_match("(QRect rectangle = \"rect\")", "String initialiser test");
	total++; count += test_match("(QRect rectangle = QRect())", "Simple object initialiser test");
	total++; count += test_match("(QRect rectangle = QRect(1))", "Object initialiser (one argument) test");
	total++; count += test_match("(QRect rectangle = QRect(1, \"left\"))", "Object initialiser (two arguments) test");
	total++; count += test_match("(QRect rectangle = QRect(QRect(1, \"left\")))", "Compound object test");
	total++; count += test_match("(const QRect & rectangle = QRect( QPoint( 0, 0 ), QSize( -1, -1 ) ))", "Horrible initialiser test");
	total++; count += test_match("( Qt::GestureType  gesture )", "Namespaced type");
	total++; count += test_match("( QList < QAction >  actions )", "Template type");
	total++; count += test_match("( QList < Qt::QAction >  actions )", "Template namespaced type");
	total++; count += test_match("( QList < QAction  *>  actions )", "Template type with pointer");
	total++; count += test_match("(Qt::WidgetAttribute  attribute) const", "Constant method");
	total++; count += test_match("( int  id ,  bool  enable  = true )", "Boolean default value");
	total++; count += test_match("( const  QKeySequence  &  key ,  Qt::ShortcutContext  context  = Qt::WindowShortcut )", "Complex default value");
	total++; count += test_match("( Qt::GestureType  gesture ,  Qt::GestureFlags  flags  = Qt::GestureFlags())", "Basic flags: namespaced objects");
	total++; count += test_match("( QPainter  *  painter , const  QPoint  &  targetOffset  = QPoint(), const QRegion  &  sourceRegion = QRegion(), RenderFlags  renderFlags = RenderFlags( DrawWindowBackground ) )", "Complex flags: objects with constant in constructor");
	total++; count += test_match("( QPainter  *  painter , const  QPoint  &  targetOffset  = QPoint(), const QRegion  &  sourceRegion = QRegion(), RenderFlags  renderFlags = RenderFlags( DrawWindowBackground | DrawChildren ) )", "Complex flags: objects with expressions in constructor");
	total++; count += test_match("( QMouseEvent  *)", "QSpashScreen::mousePressEvent: no name for argument");
	total++; count += test_match("(const  QString  &  message ,  int  alignment  = Qt::AlignLeft, const  QColor  &  color  = Qt::black)", "QSplashScreen::showMessage: identifier updates with constants");
	total++; count += test_match("(const  QMap < QString ,  QUrl > &  links , const  QString  &  keyword )", "QHelpIndexWidget::linksActivated: multiple parameters in template");
	total++; count += test_match("(unsigned offset, int  count)", "Simple primitive types");
	total++; count += test_match("( unsigned long  offset ,  unsigned long  count )", "QDomCharacterData::deleteData: complex primitive types");
	total++; count += test_match("(unsigned long long int offset, signed long long count, long double count)", "Primitive types horror test");
	total++; count += test_match("(const  QString  &  publicId , const  QString  &  systemId ,  QXmlInputSource  &  ret )", "QXmlDefaultHandler::resolveEntity: mix pointers and references");
	total++; count += test_match("( ...)", "Just an ellipsis");
	total++; count += test_match("( jclass  clazz , const  char  *  methodName , const  char  *  signature , ...)", "QAndroidJniObject::callStaticMethod: ellipsis"); 
	total++; count += test_match("(const  QMediaTimeInterval  &  interval)", "QMediaTimeRange::operator+=: regression");
	total++; count += test_match("( QByteArray  const &  name )", "QMediaObject::addPropertyWatch: const after type"); 
	total++; count += test_match("(const  QModelIndex  &  topLeft, const  QModelIndex  &  bottomRight, const  QVector < int > &  roles = QVector<int>())", "QTreeView::dataChanged: const after type"); 

	std::cerr << std::endl << std::endl << "Total: " << count << " passed out of " << total << "." << std::endl;
	if (count < total) std::cerr << "More work is needed for " << (total - count) << " item" << ((total - count) > 1 ? "s" : "") << ". " << std::endl;
	else std::cerr << "Good job." << std::endl;
}

std::string trim(const std::string & str) {
	size_t first = str.find_first_not_of(' ');
	size_t last = str.find_last_not_of(' ');
	return str.substr(first, last - first + 1);
}

std::string replace(const std::string & str, const std::string & before, const std::string & after) {
	size_t index = 0;
	std::string modified = str;

	while (true) {
		index = str.find(before, index);
		if (index == std::string::npos) {
			break;
		}

		modified.replace(index, before.length(), after);
		index += after.length();
	}

	return modified;
}

void ast_to_xml(pugi::xml_node methodsynopsis, AST* ast) {
	if (ast->parameters.size() == 0) {
		methodsynopsis.append_child("db:void");
	} else {
		int paramsCounter = 1;
		for (auto iterator = ast->parameters.begin(); iterator != ast->parameters.end(); ++iterator) {
			Parameter* p = *iterator;
			pugi::xml_node param = methodsynopsis.append_child("db:methodparam");

			if (p->isEllipsis) {
				// TODO: modify DocBook to allow <varargs> here, instead of methodparam… (like funcparam).
				param.append_child("db:parameter").text().set("...");
			}
			else {
				if (p->constness == FrontConst) {
					param.append_child("db:modifier").text().set("const");
				}

				std::string sub_type = replace(replace(p->serialise(), "*", " *"), "&", " &");
				std::string type = trim(sub_type + ' ' + p->pointersReferencesStr());
				param.append_child("db:type").text().set(type.c_str());

				if (p->constness == RearConst) {
					param.append_child("db:modifier").text().set("const");
				}

				auto id = p->identifier;
				param.append_child("db:parameter").text().set((id != nullptr) ? id->c_str() : std::string("arg" + paramsCounter).c_str());

				if (p->initialiser != nullptr) {
					param.append_child("db:initializer").text().set(p->initialiser->serialise().c_str());
				}
			}

			++paramsCounter;
		}
	}

	if (ast->isConst) {
		methodsynopsis.append_child("db:modifier").text().set("const");
	}
}

int main(int argc, const char* argv[]) {
#if RUN_TEST_SUITE == 0
	// Parse arguments to the program: -s:input.xml -o:output.xml (like Saxon). 
	bool readFromFile = false;
	bool writeToFile = false;
	std::string inFile, outFile; 

	if (argc > 1) {
		for (int i = 1; i < argc; ++i) {
			if (std::string(argv[i]).compare(0, 3, "-s:") == 0) { // Input file (source). 
				readFromFile = true; 
				inFile = std::string(argv[i]).substr(3);
			}
			else if (std::string(argv[i]).compare(0, 3, "-o:") == 0) { // Output file. 
				writeToFile = true;
				outFile = std::string(argv[i]).substr(3);
			}
		}
	}

	// Parse the input document. 
	pugi::xml_document doc;
	pugi::xml_parse_result result;

	if (readFromFile) {
		result = doc.load_file(inFile.c_str());
	}
	else {
		result = doc.load(std::cin);
	}

	if (!result) {
		std::cerr << "Error while loading XML file: " << std::endl; 
		std::cerr << "    " << result.description() << std::endl;
		if (readFromFile) {
			std::cerr << "  Trying to read from a file: " << inFile << std::endl; 
		} else {
			std::cerr << "  Trying to read from std::in." << std::endl; 
		}
		return -1;
	}

	// Start the compiler on the given nodes. 
	pugi::xpath_node_set to_analyse = doc.select_nodes("//db:exceptionname[@role='parameters']/text()");
	int total = 0;
	int errors = 0;
	for (pugi::xpath_node_set::const_iterator it = to_analyse.begin(); it != to_analyse.end(); ++it) {
		pugi::xpath_node node = *it;
		total += 1;

		// Parse the text. 
		std::string prototype = node.node().value();
		const char * cstr = prototype.c_str();
		AST* ast = cpp_prototype(cstr, cstr + prototype.length());
		if (test_differ(ast, prototype)) {
			std::cerr << "Error when parsing a prototype, probably unsupported features:" << std::endl;
			std::cerr << "    " << prototype << std::endl;
			errors += 1;
			continue;
		}

		// Replace the node by a parsed version. 
		pugi::xml_node methodsynopsis = node.node().parent().parent(); // <db:methodsynopsis>
		methodsynopsis.remove_child(node.node().parent().previous_sibling()); // <db:void role="parameters"/>
		methodsynopsis.remove_child(node.node().parent()); // <db:exceptionname role="parameters">( QAction  *  action )</db:exceptionname>
		ast_to_xml(methodsynopsis, ast);
	}

	// Write the output if there were no errors. 
	if (errors == 0) {
		if (writeToFile) {
			doc.save_file(outFile.c_str());
		}
		else {
			doc.save(std::cout);
		}
	}
	else {
		std::cerr << errors << " errors out of " << total << " prototypes analysed." << std::endl;
	}

	// Run the test suite. 
#else
	test();
	std::cin.ignore();
#endif
	return 0;
}
