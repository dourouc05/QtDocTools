#include <iostream>
#include <sstream>
#include "axe_1.5.4.164/include/axe.h"
//#include <axe\axe.h>

#pragma warning(disable:4503)

class AST {
public: 
	bool matched; 
};

template<class I>
AST* cpp_prototype(I begin, I end) {
	// Lexer. 
	auto comma = axe::r_lit(',');
	auto equal = axe::r_lit('=');
	auto space = axe::r_any(" \t"); 
	auto paren_open = axe::r_lit('(');
	auto paren_close = axe::r_lit(')');
	auto quote = axe::r_lit('"');
	auto underscore = axe::r_lit('_');

	auto kw_const = axe::r_lit("const");
	auto kw_reference = axe::r_lit('&');
	auto kw_pointer = +axe::r_lit('*');

	// Grammar rules. 
	// Recursive rules don't work due to syntax sugar missing. Order: build simple values (litterals), grow them into
	// objects whose constructor only needs bare litterals, then once more to nest objects into objects. 
	auto identifier = (axe::r_alpha() | underscore) & *(axe::r_alnumstr() | underscore);
	auto value_number = axe::r_decimal() | axe::r_double();
	auto value_string = quote & *(axe::r_any() - quote) & quote;
	auto value_litteral = value_string | value_number;

	auto value_object_simple = identifier & *space & paren_open & *space & ~((value_litteral & *space) % (comma & *space)) & *space & paren_close;
	auto value_simple = value_litteral | value_object_simple; 

	auto value_object_compound = identifier & *space & ~(paren_open & *space & ~((value_simple & *space) % (comma & *space)) & *space & paren_close);
	auto value = value_simple | value_object_compound;
	// Note: identifier | value_object_simplest would not work (the parser gets into identifier, and is unable to get out to reach the next alike). 

	auto initialiser = equal & value;
	auto parameter = ~kw_const & *space & identifier & *space & ~(+kw_reference | +kw_pointer) & *space & identifier & ~(*space & equal & *space & value & *space);
	auto parameters_list = parameter % (*space & comma & *space);
	auto start = paren_open & *space & ~parameters_list & *space & paren_close;

	// Bootstrap it all. 
	auto result = start(begin, end);

	AST* retval = new AST();
	retval->matched = result.matched;
	return retval;
}

bool test_match(std::string str, std::string testName) {
	AST* ast = cpp_prototype(str.begin(), str.end());
	if (!ast->matched) {
		std::cerr << testName << " failed: '" << str << "'" << std::endl;
		return false;
	}
	std::cerr << testName << " passed!" << std::endl;
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
