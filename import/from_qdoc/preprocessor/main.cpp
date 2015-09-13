#include <iostream>
#include <list>
#include <sstream>
#include <iterator>
#include <algorithm>

#include "AST.hpp"
#include "parser.hpp"

bool test_match(const std::string & str, const std::string & testName) {
	// Start parsing. 
	AST* ast = cpp_prototype(str.begin(), str.end());
	if (! ast->matched) {
		std::cerr << testName << " failed (no match): '" << str << "'" << std::endl;
		delete ast;
		return false;
	}

	// Test the AST: serialise it as C++ prototype; compare with input, removing all spaces.
	std::string original = str;
	std::string serialised = ast->serialise();
	original.erase(std::remove(original.begin(), original.end(), ' '), original.end());
	serialised.erase(std::remove(serialised.begin(), serialised.end(), ' '), serialised.end());
	if (original.compare(serialised) != 0) {
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

	std::cerr << std::endl << std::endl << "Total: " << count << " passed out of " << total << "." << std::endl;
	if (count < total) std::cerr << "More work is needed for " << (total - count) << " item" << ((total - count) > 1 ? "s" : "") << ". " << std::endl;
	else std::cerr << "Good job." << std::endl;
}

int main(int argc, const char* argv[]) {
	test();
	//std::string str = "(const QRect & rectangle = QRect( QPoint( 0, 0 ), QSize( -1, -1 ) ))";
	//std::string str = "(QRect rectangle)";
	//cpp_prototype(str.begin(), str.end());
	std::cin.ignore();
	return 0;
}
