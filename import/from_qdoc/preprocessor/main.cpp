#include <iostream>
#include <list>
#include <sstream>
#include <iterator>

#include "axe_1.5.4.164/include/axe.h"
//#include <axe/axe.h>

#pragma warning(disable:4503)

struct Parameter;
struct Object;
struct Value;

struct AST {
	bool matched; 
	std::list<Parameter*> parameters;
};

enum ValueType { NONE, INTEGER, DOUBLE, STRING, OBJECT };
typedef union {
	int i;
	double d;
	std::string* s;
	Object* o;
} ValueContent;
struct Value {
	ValueType type;
	ValueContent content;
};

struct Parameter{
	bool isConst = false;
	std::string* type;
	int nPointers = 0;
	int nReferences = 0;
	std::string* identifier;
	Value* initialiser;
};

struct Object {
	std::string* identifier;
	std::list<Value*> parameters;
};

template <typename T>
void assert_nullptr(T * ptr) {
	if (ptr != nullptr) {
		std::cerr << "ASSERTION FAILED: pointer is not null. Memory leak, logic error." << std::endl;
	}
}

template<class I>
AST* cpp_prototype(I begin, I end) {
	// Prepare the places to return the values being read. 
	Object* currentObject = new Object;
	Value* currentValue = nullptr;
	std::string* currentIdentifier = nullptr;

	auto valueInt = axe::e_ref([&currentValue](I i1, I i2) {
		assert_nullptr(currentValue); currentValue = new Value;
		std::stringstream(std::string(i1, i2)) >> currentValue->content.i;
		currentValue->type = INTEGER;
	});
	auto valueDouble = axe::e_ref([&currentValue](I i1, I i2) {
		assert_nullptr(currentValue); currentValue = new Value;
		std::stringstream(std::string(i1, i2)) >> currentValue->content.d;
		currentValue->type = DOUBLE;
	});
	auto valueString = axe::e_ref([&currentValue](I i1, I i2) {
		assert_nullptr(currentValue); currentValue = new Value;
		currentValue->content.s = new std::string(std::string(i1, i2));
		currentValue->type = STRING;
	});
	auto valueObjectSimple = axe::e_ref([&currentValue, &currentObject](I i1, I i2) {
		assert_nullptr(currentValue); currentValue = new Value;
		currentValue->content.o = currentObject;
		currentValue->type = OBJECT;
	});

	auto valueIdentifier = axe::e_ref([&currentIdentifier](I i1, I i2) {
		currentIdentifier = new std::string(i1, i2);
	});

	auto objectIdentifier = axe::e_ref([&currentObject, &currentIdentifier](I i1, I i2) {
		currentObject->identifier = currentIdentifier;
	});
	auto objectNewValue = axe::e_ref([&currentObject, &currentValue](I i1, I i2) {
		if (currentValue == nullptr) {
			// When the last parameter is read, a new value is added, but no text was found for it! 
			// Hence the null pointer and this case. 
			return;
		}
		if (currentValue->type == NONE) {
			std::cerr << "ASSERTION FAILED." << std::endl;
		}
		currentObject->parameters.push_back(currentValue);
		currentValue = nullptr;
	});


	// Lexer. 
	auto space = axe::r_any(" \t");
	auto comma = axe::r_lit(',');
	auto spaced_comma = *space & comma & *space;
	auto equal = axe::r_lit('=');
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
	auto identifier = ((axe::r_alpha() | underscore) & *(axe::r_alnumstr() | underscore)) >> valueIdentifier;
	auto value_number = axe::r_decimal() >> valueInt | axe::r_double() >> valueDouble;
	auto value_string = (quote & *(axe::r_any() - quote) & quote) >> valueString;
	auto value_litteral = value_string | value_number;

	auto value_object_simple = (identifier >> objectIdentifier) & *space & paren_open & *space & ~((value_litteral >> objectNewValue) % spaced_comma) & *space & paren_close;
	auto value_simple = value_litteral | value_object_simple; 

	auto value_object_compound = (identifier >> objectIdentifier) & *space & ~(paren_open & *space & ~((value_simple >> objectNewValue) % spaced_comma) & *space & paren_close);
	auto value = value_simple | value_object_compound;
	// Note: identifier | value_object_simplest would not work (the parser gets into identifier, and is unable to get out to reach the next alike). 

	auto parameter = ~kw_const & *space & identifier & *space & ~(+kw_reference | +kw_pointer) & *space & identifier & ~(*space & equal & *space & value & *space);
	auto parameters_list = parameter % spaced_comma;
	auto start = paren_open & *space & ~parameters_list & *space & paren_close;

	// Bootstrap it all. 
	auto result = start(begin, end);

	if (currentObject) {
		std::cout << currentObject->identifier << std::endl;
	}

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
	//total++; count += test_match("()", "Dumb test");
	//total++; count += test_match("(QRect rectangle)", "Simple test");
	//total++; count += test_match("(const QRect & rectangle)", "Constant reference test");
	//total++; count += test_match("(const QRect & rectangle, QSize * size)", "Two parameters and a pointer test");
	//total++; count += test_match("(const QRect && rectangle, QSize ** size)", "Move semantics and double pointer test");
	//total++; count += test_match("(QRect rectangle = 0)", "Simple initialiser test");
	//total++; count += test_match("(QRect rectangle = \"rect\")", "String initialiser test");
	//total++; count += test_match("(QRect rectangle = QRect())", "Simple object initialiser test");
	//total++; count += test_match("(QRect rectangle = QRect(1))", "Object initialiser (one argument) test");
	//total++; count += test_match("(QRect rectangle = QRect(1, \"left\"))", "Object initialiser (two arguments) test");
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
