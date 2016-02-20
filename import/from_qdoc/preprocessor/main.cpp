#include <iostream>
#include <list>
#include <sstream>
#include <iterator>
#include <algorithm>

#include "AST.hpp"
#include "parser.hpp"
#include "pugixml\src\pugixml.hpp"

bool test_differ(const AST* const ast, const std::string & str) {
	std::string original = str;
	std::string serialised = ast->serialise();
	original.erase(std::remove(original.begin(), original.end(), ' '), original.end());
	serialised.erase(std::remove(serialised.begin(), serialised.end(), ' '), serialised.end());
	return original.compare(serialised) != 0;
}

bool test_match(const std::string & str, const std::string & testName) {
	// Start parsing. 
	AST* ast = cpp_prototype(str);
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

	std::cerr << std::endl << std::endl << "Total: " << count << " passed out of " << total << "." << std::endl;
	if (count < total) std::cerr << "More work is needed for " << (total - count) << " item" << ((total - count) > 1 ? "s" : "") << ". " << std::endl;
	else std::cerr << "Good job." << std::endl;
}

void ast_to_xml(pugi::xml_node methodsynopsis, AST* ast) {
	if (ast->parameters.size() == 0) {
		methodsynopsis.append_child("db:void");
	} else {
		auto end = ast->parameters.end();
		for (auto iterator = ast->parameters.begin(); iterator != end; ++iterator) {
			Parameter* p = *iterator;
			pugi::xml_node param = methodsynopsis.append_child("db:methodparam");

			if (p->isConst) {
				param.append_child("db:modifier").text().set("const");
			}

			std::string type = *p->type + ' ' + std::string(p->nPointers, '*') + std::string(p->nReferences, '&');
			param.append_child("db:type").text().set(type.c_str());

			param.append_child("db:parameter").text().set((*p->identifier).c_str());

			if (p->initialiser != nullptr) {
				param.append_child("db:initializer").text().set(p->initialiser->serialise().c_str());
			}
		}
	}

	if (ast->isConst) {
		methodsynopsis.append_child("db:modifier").text().set("const");
	}
}

int main(int argc, const char* argv[]) {
	pugi::xml_document doc;
	pugi::xml_parse_result result = doc.load_file("qwidget.db");
	if (!result) {
		std::cerr << "Error while loading XML file: " << std::endl; 
		std::cerr << "    " << result.description() << std::endl;
	}

	pugi::xpath_node_set to_analyse = doc.select_nodes("//db:exceptionname[@role='parameters']/text()");
	int total = 0;
	int errors = 0;
	for (pugi::xpath_node_set::const_iterator it = to_analyse.begin(); it != to_analyse.end(); ++it) {
		pugi::xpath_node node = *it;
		total += 1;

		// Parse the text. 
		std::string prototype = node.node().value();
		AST* ast = cpp_prototype(prototype);
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

	std::cerr << errors << " errors out of " << total << "." << std::endl;
	if (errors == 0) {
		doc.save_file("qwidget-new.db");
	}

	//test();
	//std::string str = "(const QRect & rectangle = QRect( QPoint( 0, 0 ), QSize( -1, -1 ) ))";
	//std::string str = "(QRect rectangle)";
	//cpp_prototype(str.begin(), str.end());
	std::cin.ignore();
	return 0;
}
