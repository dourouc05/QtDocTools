#include <iostream>
#include <list>
#include <sstream>
#include <iterator>

#include "axe_1.5.4.164/include/axe.h"
//#include <axe/axe.h>

// For templates: "decorated name length exceeded, name was truncated"
#pragma warning(disable: 4503)

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
	Value* initialiser = nullptr;
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
	AST* retval = new AST;
	Object* currentOuterObject = nullptr;
	Object* currentInnerObject = nullptr;
	Value* currentValue = nullptr;
	std::string* currentIdentifier = nullptr;
	Parameter* currentParameter = new Parameter; 

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
	auto valueOuterObject = axe::e_ref([&currentValue, &currentOuterObject](I i1, I i2) {
		assert_nullptr(currentValue); currentValue = new Value;
		currentValue->content.o = currentOuterObject;
		currentValue->type = OBJECT;
	});
	auto valueInnerObject = axe::e_ref([&currentValue, &currentInnerObject](I i1, I i2) {
		assert_nullptr(currentValue); currentValue = new Value;
		currentValue->content.o = currentInnerObject;
		currentValue->type = OBJECT;
	});

	auto valueIdentifier = axe::e_ref([&currentIdentifier](I i1, I i2) {
		currentIdentifier = new std::string(i1, i2);
	});

	auto outerObject = axe::e_ref([&currentOuterObject](I i1, I i2) {
		currentOuterObject = new Object	;
	});
	auto outerObjectIdentifier = axe::e_ref([&currentOuterObject, &currentIdentifier](I i1, I i2) {
		currentOuterObject->identifier = currentIdentifier;
	});
	auto outerObjectNewValue = axe::e_ref([&currentOuterObject, &currentValue](I i1, I i2) {
		if (currentValue == nullptr) {
			// When the last parameter is read, a new value is added, but no text was found for it! 
			// Hence the null pointer and this case. 
			return;
		}
		if (currentValue->type == NONE) {
			std::cerr << "ASSERTION FAILED." << std::endl;
		}
		currentOuterObject->parameters.push_back(currentValue);
		currentValue = nullptr;
	});
	auto innerObject = axe::e_ref([&currentInnerObject](I i1, I i2) { // Duplicate of previous functions. @TODO: merge innards. 
		currentInnerObject = new Object;
	});
	auto innerObjectIdentifier = axe::e_ref([&currentInnerObject, &currentIdentifier](I i1, I i2) {
		currentInnerObject->identifier = currentIdentifier;
	});
	auto innerObjectNewValue = axe::e_ref([&currentInnerObject, &currentValue](I i1, I i2) {
		if (currentValue == nullptr) {
			// When the last parameter is read, a new value is added, but no text was found for it! 
			// Hence the null pointer and this case. 
			return;
		}
		if (currentValue->type == NONE) {
			std::cerr << "ASSERTION FAILED." << std::endl;
		}
		currentInnerObject->parameters.push_back(currentValue);
		currentValue = nullptr;
	});

	auto parameterConst = axe::e_ref([&currentParameter](I i1, I i2) {
		if (std::distance(i1, i2) > 2) {
			currentParameter->isConst = true;
		}
	});
	auto parameterType = axe::e_ref([&currentParameter, &currentIdentifier](I i1, I i2) {
		currentParameter->type = currentIdentifier;
	});
	auto parameterPointers = axe::e_ref([&currentParameter](I i1, I i2) {
		currentParameter->nPointers = std::distance(i1, i2);
	});
	auto parameterReferences = axe::e_ref([&currentParameter](I i1, I i2) {
		currentParameter->nReferences = std::distance(i1, i2);
	});
	auto parameterIdentifier = axe::e_ref([&currentParameter, &currentIdentifier](I i1, I i2) {
		currentParameter->identifier = currentIdentifier;
	});
	auto parameterInitialiser = axe::e_ref([&currentParameter, &currentValue](I i1, I i2) {
		currentParameter->initialiser = currentValue;
		currentValue = nullptr;
	});

	auto addParameter = axe::e_ref([&retval, &currentParameter](I i1, I i2) {
		retval->parameters.push_back(currentParameter);
		currentParameter = new Parameter;
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

	auto value_object_simple = (identifier >> outerObject >> outerObjectIdentifier)
		& *space 
		& paren_open 
		& *space 
		& ~((value_litteral >> outerObjectNewValue) % spaced_comma)
		& *space 
		& paren_close;
	auto value_simple = value_litteral | (value_object_simple >> valueOuterObject);

	auto value_object_compound = (identifier >> innerObject >> innerObjectIdentifier)
		& *space 
		& paren_open & *space & ~((value_simple >> innerObjectNewValue) % spaced_comma) & *space & paren_close;
	auto value = value_simple | (value_object_compound >> valueInnerObject);

	auto parameter = ~(kw_const >> parameterConst) 
		& *space 
		& (identifier >> parameterType) 
		& *space 
		& ~((+kw_pointer) >> parameterPointers | (+kw_reference) >> parameterReferences)
		& *space 
		& (identifier >> parameterIdentifier)
		& ~(*space & equal & *space & (value >> parameterInitialiser) & *space);
	auto parameters_list = (parameter >> addParameter) % spaced_comma;
	auto start = paren_open & *space & ~parameters_list & *space & paren_close;

	// Bootstrap it all. 
	auto result = start(begin, end);

	retval->matched = result.matched;
	return retval;
}

std::string test_serialise_object(const Object* const o);
std::string test_serialise_value(const Value* const v) {
	switch (v->type)
	{
	case NONE:
		std::cerr << "ASSERTION ERROR." << std::endl;
		return "none";
	case INTEGER:
		return std::to_string(v->content.i);
	case DOUBLE:
		return std::to_string(v->content.d);
	case STRING:
		return *v->content.s;
	case OBJECT:
		return test_serialise_object(v->content.o);
	default:
		std::cerr << "ASSERTION ERROR." << std::endl;
		return "unknown";
	}
}

std::string test_serialise_object(const Object* const o) {
	std::string retval = *o->identifier; 
	retval += "(";
	auto end = o->parameters.end();
	for (auto iterator = o->parameters.begin(); iterator != end; ++iterator) {
		Value* value = *iterator;
		retval += test_serialise_value(value);
		if (std::next(iterator) != end) {
			retval += ", ";
		}
	}
	retval += ")";

	return retval;
}

std::string test_serialise(const AST* const ast) {
	if (!ast->matched) {
		return "";
	}

	std::string retval = "(";
	auto end = ast->parameters.end();
	for (auto iterator = ast->parameters.begin(); iterator != end; ++iterator) {
		Parameter* p = *iterator;

		if (p->isConst) {
			retval += "const ";
		}

		retval += *p->type;

		retval += std::string(p->nPointers, '*'); 
		retval += std::string(p->nReferences, '&');
		retval += ' ';

		retval += *p->identifier;

		if (p->initialiser != nullptr) {
			Value* value = p->initialiser;
			retval += " = ";
			retval += test_serialise_value(p->initialiser);
		}

		if (std::next(iterator) != end) {
			retval += ", ";
		}
	}
	retval += ")";
	return retval;
}

bool test_match(const std::string & str, const std::string & testName) {
	// Start parsing. 
	AST* ast = cpp_prototype(str.begin(), str.end());
	if (!ast->matched) {
		std::cerr << testName << " failed (no match): '" << str << "'" << std::endl;
		return false;
	}

	// Test the AST: serialise it as C++ prototype; compare with input, removing all spaces.
	std::string original = str;
	std::string serialised = test_serialise(ast);
	original.erase(std::remove(original.begin(), original.end(), ' '), original.end());
	serialised.erase(std::remove(serialised.begin(), serialised.end(), ' '), serialised.end());
	if (original.compare(serialised) != 0) {
		std::cerr << testName << " failed (ASTs differ): '" << str << "'" << std::endl;
		std::cerr << "    Found '" << test_serialise(ast) << "' instead." << std::endl;
		return false;
	}

	// Done! 
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
