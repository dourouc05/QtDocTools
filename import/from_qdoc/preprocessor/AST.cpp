#include <iostream>
#include <list>
#include <sstream>
#include <iterator>

#include "AST.hpp"

AST::AST() {
}

AST::~AST() {
	for (Parameter*& p : parameters) {
		delete p;
	}
}

Parameter::Parameter() {
}

Parameter::~Parameter() {
	delete type;
	delete identifier;
	delete initialiser;
}

Object::Object() {
}

Object::~Object() {
	delete identifier;
	for (Value*& v : parameters) {
		delete v;
	}
}

Value::Value() {
}

Value::~Value() {
	if (type == STRING) {
		delete content.s;
	} else if (type == OBJECT) {
		delete content.o;
	}
}

std::string ast_serialise_object(const Object* const o);
std::string ast_serialise_value(const Value* const v) {
	switch (v->type) {
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
		return ast_serialise_object(v->content.o);
	default:
		std::cerr << "ASSERTION ERROR." << std::endl;
		return "unknown";
	}
}

std::string ast_serialise_object(const Object* const o) {
	std::string retval = *o->identifier;
	retval += "(";
	auto end = o->parameters.end();
	for (auto iterator = o->parameters.begin(); iterator != end; ++iterator) {
		Value* value = *iterator;
		retval += ast_serialise_value(value);
		if (std::next(iterator) != end) {
			retval += ", ";
		}
	}
	retval += ")";

	return retval;
}

std::string AST::serialise() const {
	if (!matched) {
		return "";
	}

	std::string retval = "(";
	auto end = parameters.end();
	for (auto iterator = parameters.begin(); iterator != end; ++iterator) {
		Parameter* p = *iterator;

		if (p->isConst) {
			retval += "const ";
		}

		retval += *p->type;
		retval += ' ';
		retval += std::string(p->nPointers, '*');
		retval += std::string(p->nReferences, '&');
		retval += ' ';

		retval += *p->identifier;

		if (p->initialiser != nullptr) {
			Value* value = p->initialiser;
			retval += " = ";
			retval += ast_serialise_value(p->initialiser);
		}

		if (std::next(iterator) != end) {
			retval += ", ";
		}
	}
	retval += ")";
	return retval;
}