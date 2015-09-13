#include "axe_1.5.4.164/include/axe.h"
//#include <axe/axe.h>

#include "AST.hpp"

// For templates: "decorated name length exceeded, name was truncated"
#pragma warning(disable: 4503)

template<class I>
AST* cpp_prototype(I begin, I end) {
	// Prepare the places to return the values being read. 
	AST* retval = new AST;
	Object* currentOuterObject = nullptr; // Two objects can be nested in prototypes. A more general solution would be to use a stack, 
	Object* currentInnerObject = nullptr; // but this is overkill, for only simply nested objects can be seen. 
	Value* currentValue = nullptr;
	std::string* currentIdentifier = nullptr;
	Parameter* currentParameter = nullptr;

	auto valueAllocator = axe::e_ref([&currentValue](I i1, I i2) {
		currentValue = new Value;
	});
	auto valueTrue = axe::e_ref([&currentValue](I i1, I i2) {
		currentValue->content.b = true;
		currentValue->type = BOOLEAN;
	});
	auto valueFalse = axe::e_ref([&currentValue](I i1, I i2) {
		currentValue->content.b = false;
		currentValue->type = BOOLEAN;
	});
	auto valueInt = axe::e_ref([&currentValue](I i1, I i2) {
		std::stringstream(std::string(i1, i2)) >> currentValue->content.i;
		currentValue->type = INTEGER;
	});
	auto valueDouble = axe::e_ref([&currentValue](I i1, I i2) {
		std::stringstream(std::string(i1, i2)) >> currentValue->content.d;
		currentValue->type = DOUBLE;
	});
	auto valueString = axe::e_ref([&currentValue](I i1, I i2) {
		currentValue->content.s = new std::string(std::string(i1, i2));
		currentValue->type = STRING;
	});
	auto valueOuterObject = axe::e_ref([&currentValue, &currentOuterObject](I i1, I i2) {
		currentValue->content.o = currentOuterObject;
		currentValue->type = OBJECT;
		currentOuterObject = nullptr;
	});
	auto valueInnerObject = axe::e_ref([&currentValue, &currentInnerObject](I i1, I i2) {
		currentValue->content.o = currentInnerObject;
		currentValue->type = OBJECT;
		currentInnerObject = nullptr;
	});
	auto valueConstant = axe::e_ref([&currentValue](I i1, I i2) {
		currentValue->content.s = new std::string(std::string(i1, i2));
		currentValue->type = CONSTANT;
	});

	auto valueIdentifier = axe::e_ref([&currentIdentifier](I i1, I i2) {
		if (currentIdentifier == nullptr) {
			currentIdentifier = new std::string(i1, i2);
		}
	}); 
	auto valueIdentifierAddCharacters = axe::e_ref([&currentIdentifier](I i1, I i2) {
		// Parses different components, but their structure is forgotten in the AST... 
		currentIdentifier->append(std::string(i1, i2));
	});
	
	auto outerObjectIdentifier = axe::e_ref([&currentOuterObject, &currentIdentifier](I i1, I i2) {
		currentOuterObject = new Object;
		currentOuterObject->identifier = currentIdentifier;
		currentIdentifier = nullptr;
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
	auto innerObjectIdentifier = axe::e_ref([&currentInnerObject, &currentIdentifier](I i1, I i2) {
		currentInnerObject = new Object;
		currentInnerObject->identifier = currentIdentifier;
		currentIdentifier = nullptr;
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

	auto parameterAllocator = axe::e_ref([&currentParameter](I i1, I i2) {
		if (currentParameter == nullptr) {
			currentParameter = new Parameter;
		}
	});
	auto parameterConst = axe::e_ref([&currentParameter](I i1, I i2) {
		if (std::distance(i1, i2) > 2) {
			currentParameter->isConst = true;
		}
	});
	auto parameterType = axe::e_ref([&currentParameter, &currentIdentifier](I i1, I i2) {
		currentParameter->type = currentIdentifier;
		currentIdentifier = nullptr;
	});
	auto parameterPointers = axe::e_ref([&currentParameter](I i1, I i2) {
		currentParameter->nPointers = std::distance(i1, i2);
	});
	auto parameterReferences = axe::e_ref([&currentParameter](I i1, I i2) {
		currentParameter->nReferences = std::distance(i1, i2);
	});
	auto parameterIdentifier = axe::e_ref([&currentParameter, &currentIdentifier](I i1, I i2) {
		currentParameter->identifier = currentIdentifier;
		currentIdentifier = nullptr;
	});
	auto parameterInitialiser = axe::e_ref([&currentParameter, &currentValue](I i1, I i2) {
		currentParameter->initialiser = currentValue;
		currentValue = nullptr;
	});

	auto addParameter = axe::e_ref([&retval, &currentParameter](I i1, I i2) {
		retval->parameters.push_back(currentParameter);
		currentParameter = nullptr;
	});
	auto isConst = axe::e_ref([&retval](I i1, I i2) {
		retval->isConst = true;
	});

	// Lexer. 
	auto space = axe::r_any(" \t");
	auto comma = axe::r_lit(',');
	auto spaced_comma = *space & comma & *space;
	auto equal = axe::r_lit('=');
	auto paren_open = axe::r_lit('(');
	auto paren_close = axe::r_lit(')');
	auto tpl_open = axe::r_lit('<');
	auto tpl_close = axe::r_lit('>');
	auto quote = axe::r_lit('"');
	auto underscore = axe::r_lit('_');

	auto kw_const = axe::r_lit("const");
	auto kw_reference = axe::r_lit('&');
	auto kw_pointer = +axe::r_lit('*');
	auto kw_namespace = axe::r_lit("::");
	auto kw_true = axe::r_lit("true");
	auto kw_false = axe::r_lit("false");

	// Grammar rules. 
	// Recursive rules don't work due to missing syntax sugar. Order: build simple values (litterals), grow them into
	// objects whose constructor only needs bare litterals, then once more to nest objects into objects. 
	auto raw_identifier = ((axe::r_alpha() | underscore) & *(axe::r_alnumstr() | underscore)) >> valueIdentifier;
	auto type_namespace = (kw_namespace >> valueIdentifierAddCharacters) & raw_identifier >> valueIdentifierAddCharacters;
	auto identifier = raw_identifier & *type_namespace;
	auto identifier_nowrite = ((axe::r_alpha() | underscore) & *(axe::r_alnumstr() | underscore)) & *(kw_namespace & ((axe::r_alpha() | underscore) & *(axe::r_alnumstr() | underscore)));
	auto type_template = (tpl_open >> valueIdentifierAddCharacters) 
		& *space 
		& (identifier_nowrite >> valueIdentifierAddCharacters)
		& *space
		& *(kw_pointer >> valueIdentifierAddCharacters)
		& *space
		& (tpl_close >> valueIdentifierAddCharacters);
	auto type = identifier
		& *space
		& ~type_template;
	auto value_boolean = (kw_true >> valueAllocator >> valueTrue) | (kw_false >> valueAllocator >> valueFalse);
	auto value_number = (axe::r_decimal() >> valueAllocator >> valueInt) | (axe::r_double() >> valueAllocator >> valueDouble);
	auto value_string = (quote & *(axe::r_any() - quote) & quote) >> valueAllocator >> valueString;
	auto value_litteral = value_string | value_number | value_boolean;

	auto value_object_simple = (identifier >> innerObjectIdentifier)
		& *space
		& paren_open
		& *space
		& ~((value_litteral >> innerObjectNewValue) % spaced_comma)
		& *space
		& paren_close;
	auto value_simple = value_litteral | (value_object_simple >> valueAllocator >> valueInnerObject);

	auto value_object_compound = (identifier >> outerObjectIdentifier)
		& *space
		& paren_open & *space & ~((value_simple >> outerObjectNewValue) % spaced_comma) & *space & paren_close;
	auto value_constant = identifier & *space & *type_namespace;
	auto value = value_litteral | (value_object_compound >> valueAllocator >> valueOuterObject) | (value_constant >> valueAllocator >> valueConstant);
	// value_object_compound and value_object_simple have the same prefix: if both are allowed inside value, 
	// then the parser will be confused about which one is actually at hand; it will then follow the first one, 
	// then backtrack if it is a dead end. In this case, it will have allocated objects, which will then be lost. 
	// As a consequence, this would be a waste of both time and memory. 

	auto parameter = ~(kw_const >> parameterAllocator >> parameterConst)
		& *space
		& (type >> parameterAllocator >> parameterType)
		& *space
		& ~((+kw_pointer) >> parameterPointers | (+kw_reference) >> parameterReferences)
		& *space
		& (identifier >> parameterIdentifier)
		& ~(*space & equal & *space & (value >> parameterInitialiser) & *space);
	auto parameters_list = (parameter >> addParameter) % spaced_comma;
	auto start = paren_open & *space & ~parameters_list & *space & paren_close & *space & ~(kw_const >> isConst);

	// Bootstrap it all. 
	auto result = start(begin, end);

	retval->matched = result.matched;
	return retval;
}