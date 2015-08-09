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