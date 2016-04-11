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
	type = nullptr; 
	identifier = nullptr; 
	initialiser = nullptr;
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

std::string Value::serialise() const {
	switch (type) {
	case NONE:
		std::cerr << "ASSERTION ERROR." << std::endl;
		return "none";
	case BOOLEAN: 
		return (content.b) ? "true" : "false";
	case INTEGER:
		return std::to_string(content.i);
	case DOUBLE:
		return std::to_string(content.d);
	case STRING:
		return *content.s;
	case OBJECT:
		return content.o->serialise();
	case CONSTANT:
		return *content.s;
	default:
		std::cerr << "ASSERTION ERROR." << std::endl;
		return "unknown";
	}
}

std::string Object::serialise() const {
	std::string retval = *identifier;
	retval += "(";
	auto end = parameters.end();
	for (auto iterator = parameters.begin(); iterator != end; ++iterator) {
		Value* value = *iterator;
		retval += value->serialise();
		if (std::next(iterator) != end) {
			retval += ", ";
		}
	}
	retval += ")";

	return retval;
}

std::string Parameter::pointersReferencesStr() const {
	return pointersReferences ? *pointersReferences : "";
}

std::string Parameter::serialise() const {
	if (isEllipsis) {
		return "...";
	}
	return *type + ' ' + pointersReferencesStr();
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

		retval += p->serialise() + ' ';
		if (p->identifier != nullptr) { // In rare occasions, there is no identifier! 
			retval += *p->identifier;
		}

		if (p->initialiser != nullptr) {
			Value* value = p->initialiser;
			retval += " = ";
			retval += p->initialiser->serialise();
		}

		if (std::next(iterator) != end) {
			retval += ", ";
		}
	}
	retval += ")";

	if (isConst) {
		retval += " const";
	}

	return retval;
}